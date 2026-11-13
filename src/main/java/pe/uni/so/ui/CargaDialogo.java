package pe.uni.so.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.excepciones.SimuladorException;
import pe.uni.so.procesos.CreadorProcesos;
import pe.uni.so.procesos.Pcb;
import pe.uni.so.sesion.PlanCarga;

/**
 * [P14][P17][P18] Ventana de carga, antes de iniciar la máquina: pestañas Lote oficial, Aleatoria
 * (N y semilla) y Manual (tabla que el usuario llena fila por fila). Al cambiar algo se valida la
 * carga contra la máquina («Iniciar» queda deshabilitado si no cabe) y se muestra cuántos
 * procesos pueden estar dentro a la vez.
 */
final class CargaDialogo extends Dialog<PlanCarga> {

    private final Maquina maquina;
    private final TabPane pestanas = new TabPane();
    private final Spinner<Integer> cantidad = IngresoDialogo.spinner(1, PlanCarga.MAX_ALEATORIOS, 20);
    private final TextField semilla = new TextField();
    private final ObservableList<PlanCarga.Fila> filas = FXCollections.observableArrayList();
    private final Label resumen = new Label();
    private final Label error = new Label();
    private final Node iniciar;
    private PlanCarga plan;

    CargaDialogo(Window duenio, Maquina maquina) {
        this.maquina = maquina;
        initOwner(duenio);
        setTitle("Carga de «" + maquina.nombre() + "»");
        setHeaderText(null);
        setResizable(true);
        getDialogPane().getStylesheets().setAll(App.estilosActuales());
        getDialogPane().getStyleClass().add("formulario");
        ButtonType iniciarTipo = new ButtonType("Iniciar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(iniciarTipo, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        iniciar = getDialogPane().lookupButton(iniciarTipo);
        iniciar.getStyleClass().add("boton-primario");

        semilla.setText(Long.toString(maquina.configuracion().semilla()));
        semilla.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,18}") ? c : null));
        semilla.setPrefColumnCount(12);

        pestanas.getTabs().addAll(
                pestana("Lote oficial", loteOficial()),
                pestana("Aleatoria", aleatoria()),
                pestana("Manual", manual()));
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pestanas.getSelectionModel().selectedIndexProperty().addListener((o, a, v) -> validar());
        cantidad.valueProperty().addListener((o, a, v) -> validar());
        cantidad.getEditor().textProperty().addListener((o, a, v) -> validar());
        semilla.textProperty().addListener((o, a, v) -> validar());
        filas.addListener((javafx.collections.ListChangeListener<PlanCarga.Fila>) c -> validar());

        resumen.setWrapText(true);
        resumen.getStyleClass().add("nota");
        error.setWrapText(true);
        error.getStyleClass().add("error");
        VBox contenido = new VBox(10, pestanas, resumen, error);
        VBox.setVgrow(pestanas, Priority.ALWAYS);
        getDialogPane().setContent(contenido);
        getDialogPane().setPrefSize(660, 520);
        validar();
        setResultConverter(b -> b == iniciarTipo ? plan : null);
    }

    private static Tab pestana(String titulo, Node contenido) {
        VBox caja = new VBox(10, contenido);
        caja.setPadding(new Insets(12));
        return new Tab(titulo, caja);
    }

    private Node loteOficial() {
        Label l = new Label("Los 20 procesos de la tabla del informe (P17): llegadas, ráfagas, tamaños y prioridades fijos; "
                + "su plan de E/S sale de la semilla " + CreadorProcesos.SEMILLA_LOTE_OFICIAL + ", así que el lote es idéntico en cada corrida.");
        l.setWrapText(true);
        return l;
    }

    private Node aleatoria() {
        GridPane g = IngresoDialogo.grilla();
        g.setPadding(Insets.EMPTY);
        IngresoDialogo.fila(g, 0, "Procesos (N)", cantidad);
        IngresoDialogo.fila(g, 1, "Semilla", semilla);
        Label l = new Label("Llegadas de Poisson (media " + (int) CreadorProcesos.LLEGADA_MEDIA + " ticks); ráfaga "
                + CreadorProcesos.BURST_MINIMO + "–" + CreadorProcesos.BURST_MAXIMO_ALEATORIO + "; ejecutable "
                + PlanCarga.KIB_MINIMO + "–" + PlanCarga.KIB_MAXIMO + " KiB; prioridad 1–5 (ecuación 8). La misma semilla da la misma carga.");
        l.setWrapText(true);
        l.getStyleClass().add("nota");
        return new VBox(10, g, l);
    }

    private Node manual() {
        Spinner<Integer> llegada = IngresoDialogo.spinner(0, 100_000, 0);
        Spinner<Integer> burst = IngresoDialogo.spinner(CreadorProcesos.BURST_MINIMO, CreadorProcesos.BURST_MAXIMO, 20);
        Spinner<Integer> kib = IngresoDialogo.spinner(PlanCarga.KIB_MINIMO, PlanCarga.KIB_MAXIMO, 512);
        Spinner<Integer> prioridad = IngresoDialogo.spinner(CreadorProcesos.PRIORIDAD_MINIMA, CreadorProcesos.PRIORIDAD_MAXIMA,
                CreadorProcesos.PRIORIDAD_MANUAL);
        for (Spinner<Integer> s : List.of(llegada, burst, kib, prioridad)) s.setPrefWidth(92);

        TableView<PlanCarga.Fila> tabla = new TableView<>(filas);
        tabla.getColumns().add(columna("PID", f -> filas.indexOf(f) + 1));
        tabla.getColumns().add(columna("Llegada", PlanCarga.Fila::llegada));
        tabla.getColumns().add(columna("Ráfaga", PlanCarga.Fila::burst));
        tabla.getColumns().add(columna("Ejecutable (KiB)", PlanCarga.Fila::kib));
        tabla.getColumns().add(columna("Prioridad", PlanCarga.Fila::prioridad));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPlaceholder(new Label("Agrega procesos con la fila de arriba"));
        tabla.setPrefHeight(220);

        Button agregar = new Button("+ Agregar");
        agregar.getStyleClass().add("boton-primario");
        agregar.setOnAction(e -> {
            if (filas.size() >= PlanCarga.MAX_FILAS) return;
            filas.add(new PlanCarga.Fila(IngresoDialogo.valor(llegada), IngresoDialogo.valor(burst),
                    IngresoDialogo.valor(kib), IngresoDialogo.valor(prioridad)));
            llegada.getValueFactory().setValue(IngresoDialogo.valor(llegada) + 1);   // el siguiente llega un tick después
        });
        Button quitar = new Button("Quitar");
        quitar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        quitar.setOnAction(e -> filas.remove(tabla.getSelectionModel().getSelectedItem()));
        agregar.setMinWidth(Region.USE_PREF_SIZE);                  // el texto nunca se corta
        quitar.setMinWidth(Region.USE_PREF_SIZE);

        HBox entrada = new HBox(8, campo("Llegada", llegada), campo("Ráfaga", burst), campo("KiB", kib),
                campo("Prioridad", prioridad), agregar, quitar);
        entrada.setAlignment(Pos.BOTTOM_LEFT);
        VBox caja = new VBox(10, entrada, tabla);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        return caja;
    }

    private static VBox campo(String texto, Spinner<Integer> s) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        l.setLabelFor(s);
        return new VBox(4, l, s);
    }

    private static TableColumn<PlanCarga.Fila, Integer> columna(String titulo, Function<PlanCarga.Fila, Integer> valor) {
        TableColumn<PlanCarga.Fila, Integer> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(valor.apply(d.getValue())));
        c.setSortable(false);
        return c;
    }

    /** Arma el plan de la pestaña elegida y lo prueba contra la máquina. */
    private void validar() {
        plan = null;
        String mensaje = null;
        try {
            PlanCarga p = switch (pestanas.getSelectionModel().getSelectedIndex()) {
                case 1 -> PlanCarga.aleatoria(cantidadEscrita(), semillaEscrita());
                case 2 -> PlanCarga.manual(new ArrayList<>(filas), semillaEscrita());
                default -> PlanCarga.loteOficial();
            };
            List<Pcb> procesos = p.procesos(maquina.configuracion().pagina().bytes);
            List<String> problemas = maquina.configuracion().problemas(procesos);
            if (problemas.isEmpty()) {
                plan = p;
                resumen.setText("Carga: " + p.descripcion() + ". " + maquina.configuracion().capacidad(procesos).resumen() + ".");
            } else mensaje = String.join("; ", problemas);
        } catch (SimuladorException | NumberFormatException ex) {
            mensaje = ex instanceof NumberFormatException ? "Escribe números enteros" : ex.getMessage();
        }
        if (plan == null) resumen.setText("");
        error.setText(mensaje == null ? "" : "✕ " + mensaje);
        error.setVisible(mensaje != null);
        error.setManaged(mensaje != null);
        iniciar.setDisable(plan == null);
    }

    private int cantidadEscrita() {
        String t = cantidad.getEditor().getText();
        return t == null || t.isBlank() ? 0 : Integer.parseInt(t.strip());
    }

    private long semillaEscrita() {
        String t = semilla.getText();
        if (t == null || t.isBlank()) throw new NumberFormatException();
        return Long.parseLong(t.strip());
    }
}
