package pe.uni.so.ui;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import pe.uni.so.comparador.Columna;
import pe.uni.so.comparador.Comparador;
import pe.uni.so.comparador.ExportadorCsv;
import pe.uni.so.comparador.OpcionesComparacion;
import pe.uni.so.comparador.Resultado;
import pe.uni.so.configuracion.Configuracion.Estrategia;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.excepciones.SimuladorException;
import pe.uni.so.sesion.PlanCarga;

/**
 * [P17][M8][G1] Ventana «Comparar configuraciones»: corre la misma carga con las políticas y las
 * estrategias marcadas, en un hilo aparte con barra de avance y «Cancelar», muestra una fila por
 * corrida con la mejor celda de cada columna resaltada dentro de su serie y exporta la tabla a CSV.
 * No toca la simulación de la máquina abierta: cada corrida usa un Kernel propio.
 */
final class ComparadorDialogo {

    private final Maquina maquina;
    private final PlanCarga planMaquina;
    private final Stage ventana = new Stage();
    private final ObservableList<Resultado> filas = FXCollections.observableArrayList();
    private final TableView<Resultado> tabla = new TableView<>(filas);
    private final Map<Politica, CheckBox> politicas = new EnumMap<>(Politica.class);
    private final Map<Estrategia, CheckBox> estrategias = new EnumMap<>(Estrategia.class);
    private final RadioButton cargaMaquina = new RadioButton();
    private final RadioButton cargaAleatoria = new RadioButton("Aleatoria:");
    private final Spinner<Integer> n = IngresoDialogo.spinner(1, PlanCarga.MAX_ALEATORIOS, 1);
    private final Spinner<Integer> semilla = IngresoDialogo.spinner(0, Integer.MAX_VALUE, 0);
    private final Button correr = new Button("▶ Correr");
    private final Button cancelar = new Button("Cancelar");
    private final Button exportar = new Button("Exportar CSV…");
    private final ProgressBar avance = new ProgressBar(0);
    private final Label estado = new Label();
    private final char decimal = DecimalFormatSymbols.getInstance().getDecimalSeparator();
    private VBox opciones;
    private Task<List<Resultado>> tarea;
    /** Opciones de la última corrida: el CSV lleva sus parámetros aunque luego se cambien las casillas. */
    private OpcionesComparacion corridas;

    private ComparadorDialogo(Maquina maquina, PlanCarga planMaquina) {
        this.maquina = maquina;
        this.planMaquina = planMaquina;
    }

    /** Abre la ventana del comparador para la máquina abierta y su carga. */
    static void abrir(Window duenio, Maquina maquina, PlanCarga plan) {
        new ComparadorDialogo(maquina, plan).armar(duenio).show();
    }

    private Stage armar(Window duenio) {
        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("comparador");
        raiz.setTop(opciones());
        raiz.setCenter(tabla());
        raiz.setBottom(barraInferior());
        BorderPane.setMargin(raiz.getCenter(), new Insets(0, 12, 0, 12));

        Rectangle2D util = Screen.getPrimary().getVisualBounds();      // la ventana cabe en la pantalla
        Scene escena = new Scene(raiz, Math.min(1240, util.getWidth() * 0.92), Math.min(680, util.getHeight() * 0.88));
        App.aplicarTema(escena);
        ventana.initOwner(duenio);
        ventana.setTitle("Comparar configuraciones — " + maquina.nombre());
        ventana.setMinWidth(720);
        ventana.setMinHeight(440);
        ventana.setScene(escena);
        ventana.setOnHidden(e -> { if (tarea != null) tarea.cancel(); });
        actualizarEstado();
        return ventana;
    }

    // ---------------------------------------------------------------- opciones

    private VBox opciones() {
        ToggleGroup grupo = new ToggleGroup();
        cargaMaquina.setToggleGroup(grupo);
        cargaAleatoria.setToggleGroup(grupo);
        cargaMaquina.setText("De la máquina: " + planMaquina.descripcion());
        cargaMaquina.setSelected(true);
        n.getValueFactory().setValue(OpcionesComparacion.nInicial(planMaquina));
        semilla.getValueFactory().setValue((int) Math.min(Integer.MAX_VALUE, OpcionesComparacion.semillaInicial(planMaquina)));
        n.setPrefWidth(100);
        semilla.setPrefWidth(135);                                     // caben las 10 cifras de Integer.MAX_VALUE
        n.disableProperty().bind(cargaMaquina.selectedProperty());
        semilla.disableProperty().bind(cargaMaquina.selectedProperty());
        Label nl = new Label("N"), sl = new Label("semilla");
        nl.getStyleClass().add("etiqueta-campo");
        sl.getStyleClass().add("etiqueta-campo");
        Tooltip ayuda = new Tooltip("Hasta " + PlanCarga.MAX_ALEATORIOS + " procesos; con 1000, cada configuración tarda unos segundos");
        n.setTooltip(ayuda);
        cargaAleatoria.setTooltip(ayuda);
        FlowPane carga = new FlowPane(10, 6, titulo("Carga"), cargaMaquina, cargaAleatoria, nl, n, sl, semilla);
        carga.setAlignment(Pos.CENTER_LEFT);
        for (javafx.scene.Node x : carga.getChildren()) if (x instanceof Region r) r.setMinWidth(Region.USE_PREF_SIZE);

        FlowPane pol = casillas("Políticas", Politica.values(), politicas);
        FlowPane est = casillas("Estrategias de memoria", Estrategia.values(), estrategias);

        opciones = new VBox(8, carga, pol, est);
        opciones.setPadding(new Insets(12));
        return opciones;
    }

    private <E extends Enum<E>> FlowPane casillas(String texto, E[] valores, Map<E, CheckBox> destino) {
        FlowPane f = new FlowPane(12, 6);
        f.setAlignment(Pos.CENTER_LEFT);
        Label t = titulo(texto);
        t.setMinWidth(Region.USE_PREF_SIZE);
        f.getChildren().add(t);
        for (E v : valores) {
            CheckBox c = new CheckBox(etiqueta(v));
            c.setMinWidth(Region.USE_PREF_SIZE);
            c.setSelected(true);
            c.selectedProperty().addListener((o, a, b) -> actualizarEstado());
            destino.put(v, c);
            f.getChildren().add(c);
        }
        return f;
    }

    private static String etiqueta(Enum<?> v) {
        if (v instanceof Politica p) return p.etiqueta;
        if (v instanceof Estrategia e) return e.etiqueta;
        return v.toString();
    }

    private static Label titulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    // ---------------------------------------------------------------- tabla

    private TableView<Resultado> tabla() {
        tabla.getStyleClass().addAll("tabla-pcb", "tabla-comparador");
        tabla.setPlaceholder(new Label("Marca las configuraciones y pulsa «Correr». Cada corrida usa la misma carga."));
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        tabla.getColumns().add(texto("Serie", r -> r.serie().etiqueta, 100));
        tabla.getColumns().add(texto("Configuración", Resultado::configuracion, 190));
        for (Columna c : Columna.values()) tabla.getColumns().add(numero(c));
        tabla.getColumns().add(texto("Nota", Resultado::nota, 260));

        tabla.setRowFactory(t -> new TableRow<>() {
            @Override protected void updateItem(Resultado r, boolean vacio) {
                super.updateItem(r, vacio);
                getStyleClass().remove("fila-incompleta");
                if (!vacio && r != null && !r.completa()) getStyleClass().add("fila-incompleta");
            }
        });
        return tabla;
    }

    private static TableColumn<Resultado, String> texto(String titulo, java.util.function.Function<Resultado, String> valor, double ancho) {
        TableColumn<Resultado, String> col = new TableColumn<>(titulo);
        col.setCellValueFactory(d -> new ReadOnlyStringWrapper(valor.apply(d.getValue())));
        col.setPrefWidth(ancho);
        col.setSortable(false);
        return col;
    }

    private TableColumn<Resultado, Resultado> numero(Columna c) {
        TableColumn<Resultado, Resultado> col = new TableColumn<>();
        Label cabecera = new Label(c.etiqueta);                          // cabecera con ayuda: qué valor es mejor
        cabecera.setTooltip(new Tooltip(c.etiqueta + (!c.comparable() ? " (no se compara)"
                : c.menorEsMejor ? " — menor es mejor" : " — mayor es mejor")
                + (!c.comparable() || c.serie == null ? "" : "\nSe resalta en la serie de " + c.serie.etiqueta.toLowerCase())));
        col.setGraphic(cabecera);
        col.setCellValueFactory(d -> new javafx.beans.property.ReadOnlyObjectWrapper<>(d.getValue()));
        col.setPrefWidth(Math.max(80, c.etiqueta.length() * 8.2 + 16));
        col.setSortable(false);
        col.setCellFactory(t -> new TableCell<>() {
            @Override protected void updateItem(Resultado r, boolean vacio) {
                super.updateItem(r, vacio);
                getStyleClass().remove("celda-mejor");
                setAlignment(Pos.CENTER_RIGHT);
                if (vacio || r == null) { setText(null); return; }
                setText(OpcionesComparacion.texto(c, r, decimal));
                if (c.esMejor(r, filas)) getStyleClass().add("celda-mejor");
            }
        });
        return col;
    }

    // ---------------------------------------------------------------- barra inferior

    private HBox barraInferior() {
        correr.getStyleClass().add("boton-primario");
        correr.setDefaultButton(true);
        correr.setOnAction(e -> correr());
        cancelar.setDisable(true);
        cancelar.setOnAction(e -> { if (tarea != null) tarea.cancel(); });
        exportar.setDisable(true);
        exportar.setOnAction(e -> exportar());
        Button cerrar = new Button("Cerrar");
        cerrar.setCancelButton(true);
        cerrar.setOnAction(e -> ventana.hide());
        avance.setPrefWidth(180);
        avance.setVisible(false);
        estado.getStyleClass().add("nota");
        estado.setMinWidth(0);
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox.setHgrow(estado, Priority.SOMETIMES);
        HBox b = new HBox(10, correr, cancelar, avance, estado, hueco, exportar, cerrar);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setPadding(new Insets(10, 12, 12, 12));
        return b;
    }

    /** Habilita «Correr» según las casillas y, si la tabla está vacía y no hay un mensaje final, muestra el resumen. */
    private void actualizarEstado() {
        if (tarea != null && tarea.isRunning()) return;
        habilitarCorrer();
        if (filas.isEmpty()) estado.setText(correr.isDisable() ? "Marca al menos una política o una estrategia"
                : OpcionesComparacion.resumen(marcadas(politicas), marcadas(estrategias)) + " con la misma carga");
    }

    private void habilitarCorrer() {
        correr.setDisable(marcadas(politicas).isEmpty() && marcadas(estrategias).isEmpty());
    }

    private static <E extends Enum<E>> List<E> marcadas(Map<E, CheckBox> casillas) {
        List<E> lista = new ArrayList<>();
        casillas.forEach((v, c) -> { if (c.isSelected()) lista.add(v); });
        return lista;
    }

    // ---------------------------------------------------------------- correr

    private void correr() {
        OpcionesComparacion o;
        List<Comparador.Escenario> escenarios;
        try {
            PlanCarga carga = OpcionesComparacion.carga(planMaquina, cargaMaquina.isSelected(), IngresoDialogo.valor(n), IngresoDialogo.valor(semilla));
            o = new OpcionesComparacion(maquina.nombre(), maquina.configuracion(), carga, marcadas(politicas), marcadas(estrategias));
            escenarios = o.escenarios();
        } catch (SimuladorException ex) {
            error("No se pudo preparar la comparación", ex.getMessage());
            return;
        }
        corridas = o;
        filas.clear();
        tabla.scrollToColumnIndex(0);                                   // cabecera y cuerpo alineados desde la primera columna
        int total = escenarios.size();
        PlanCarga carga = o.carga();
        tarea = new Task<>() {
            @Override protected List<Resultado> call() {
                int[] hechas = {0};
                updateProgress(0, total);
                updateMessage("Corriendo 1 de " + total + "…");
                return Comparador.correr(escenarios, carga, r -> {
                    if (isCancelled()) return;                          // la corrida que terminó después de «Cancelar» no entra
                    hechas[0]++;
                    Task<List<Resultado>> esta = this;
                    Platform.runLater(() -> {
                        if (esta != tarea || esta.isCancelled()) return;   // fila de una comparación anterior o cancelada
                        filas.add(r);
                        tabla.refresh();                                    // la mejor celda puede cambiar con cada fila
                    });
                    updateProgress(hechas[0], total);
                    updateMessage(hechas[0] < total ? "Corriendo " + (hechas[0] + 1) + " de " + total + "…" : "Terminando…");
                }, this::isCancelled);
            }
        };
        estado.textProperty().bind(tarea.messageProperty());
        avance.progressProperty().bind(tarea.progressProperty());
        enMarcha(true);
        tarea.setOnSucceeded(e -> terminar(() -> filas.size() + " de " + total + " corridas · carga: " + carga.descripcion()));
        tarea.setOnCancelled(e -> terminar(() -> "Cancelado: " + filas.size() + " de " + total + " corridas hechas"));
        tarea.setOnFailed(e -> {
            Throwable ex = tarea.getException();
            terminar(() -> "La comparación se detuvo");
            error("La comparación se detuvo", ex == null ? "Error desconocido" : ex.getMessage());
        });
        Thread hilo = new Thread(tarea, "comparador");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void enMarcha(boolean si) {
        opciones.setDisable(si);
        correr.setDisable(si);
        cancelar.setDisable(!si);
        exportar.setDisable(true);
        avance.setVisible(si);
    }

    private void terminar(java.util.function.Supplier<String> mensaje) {
        Platform.runLater(() -> {                                       // después de las filas que aún estén en cola
            estado.textProperty().unbind();
            avance.progressProperty().unbind();
            enMarcha(false);
            estado.setText(mensaje.get());
            exportar.setDisable(filas.isEmpty());
            habilitarCorrer();                                          // sin pisar el mensaje final («Cancelado: 0 de …»)
            tabla.refresh();
        });
    }

    // ---------------------------------------------------------------- CSV

    private void exportar() {
        if (filas.isEmpty() || corridas == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Exportar comparación a CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV separado por «;»", "*.csv"));
        fc.setInitialFileName(OpcionesComparacion.nombreArchivo(maquina.nombre(), LocalDate.now()));
        File archivo = fc.showSaveDialog(ventana);
        if (archivo == null) return;
        try {
            new ExportadorCsv(decimal == ';' ? ',' : decimal).escribir(archivo.toPath(), corridas.parametros(), List.copyOf(filas));
            estado.setText("Exportado: " + archivo.getName());
        } catch (IOException ex) {
            error("No se pudo guardar el CSV", ex.getMessage());
        } catch (SimuladorException ex) {
            error("No se pudo exportar", ex.getMessage());
        }
    }

    private void error(String cabecera, String detalle) {
        Alert a = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
        Label texto = new Label(detalle);
        texto.setWrapText(true);
        texto.setMaxWidth(480);
        a.getDialogPane().setContent(texto);
        a.setHeaderText(cabecera);
        a.initOwner(ventana);
        a.getDialogPane().getStylesheets().setAll(ventana.getScene().getStylesheets());
        a.show();
    }
}
