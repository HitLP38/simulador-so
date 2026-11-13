package pe.uni.so.ui;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pe.uni.so.configuracion.FichaMaquina;
import pe.uni.so.configuracion.FormularioMaquina;
import pe.uni.so.configuracion.GestorMaquinas;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.excepciones.SimuladorException;
import pe.uni.so.sesion.PlanCarga;

/**
 * [G5] Gestor de máquinas, la primera ventana de la app (al estilo de VirtualBox): a la izquierda
 * la lista de máquinas con la máquina por defecto preseleccionada; arriba Nueva, Configurar,
 * Clonar, Eliminar e Iniciar; al centro la ficha de la máquina elegida con sus avisos; abajo,
 * solo si los hay, los archivos dañados con la opción de eliminarlos. La lógica vive en
 * {@link GestorMaquinas}; esta clase solo arma controles y muestra diálogos.
 */
final class GestorMaquinasVista {

    private final GestorMaquinas gestor;
    private final Map<String, Stage> ventanas = new HashMap<>();   // nombre → ventana abierta
    private final ListView<Maquina> lista = new ListView<>();
    private final VBox detalle = new VBox(12);
    private final ListView<Map.Entry<Path, String>> danados = new ListView<>();
    private final TitledPane panelDanados = new TitledPane();
    private final Button nueva = boton("＋ Nueva", "Crear una máquina con los valores del curso");
    private final Button configurar = boton("⚙ Configurar", "Cambiar las características de la máquina elegida");
    private final Button clonar = boton("⧉ Clonar", "Copiar la máquina elegida con otro nombre");
    private final Button eliminar = boton("✕ Eliminar", "Borrar la máquina elegida");
    private final Button iniciar = boton("▶ Iniciar", "Abrir la máquina elegida (también con doble clic o Enter)");
    private final Label estado = new Label();
    private Stage ventana;

    GestorMaquinasVista(GestorMaquinas gestor) { this.gestor = gestor; }

    void mostrar(Stage ventana) {
        this.ventana = ventana;
        iniciar.getStyleClass().add("boton-primario");
        eliminar.getStyleClass().add("boton-peligro");
        nueva.setOnAction(e -> nueva());
        configurar.setOnAction(e -> configurar());
        clonar.setOnAction(e -> clonar());
        eliminar.setOnAction(e -> eliminar());
        iniciar.setOnAction(e -> iniciar());

        Label titulo = new Label("Gestor de máquinas");
        titulo.getStyleClass().add("titulo");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox barra = new HBox(8, titulo, hueco, nueva, configurar, clonar, eliminar, iniciar, App.botonTema());
        barra.getStyleClass().add("barra");

        lista.getStyleClass().add("lista-maquinas");
        lista.setPrefWidth(320);
        lista.setCellFactory(v -> new CeldaMaquina());
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, m) -> mostrarDetalle(m));
        lista.setOnMouseClicked(e -> { if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) iniciar(); });
        lista.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) iniciar();
            else if (e.getCode() == KeyCode.DELETE && !eliminar.isDisabled()) eliminar();
        });

        ScrollPane centro = new ScrollPane(detalle);
        centro.setFitToWidth(true);
        centro.getStyleClass().add("detalle-scroll");
        detalle.setPadding(new Insets(4, 4, 4, 16));

        Button borrarDanado = boton("Eliminar archivo", "Borrar el archivo dañado elegido de la carpeta de máquinas");
        borrarDanado.getStyleClass().add("boton-peligro");
        borrarDanado.disableProperty().bind(danados.getSelectionModel().selectedItemProperty().isNull());
        borrarDanado.setOnAction(e -> eliminarDanado());
        danados.setPrefHeight(64);
        danados.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(Map.Entry<Path, String> item, boolean vacio) {
                super.updateItem(item, vacio);
                if (vacio || item == null) { setText(null); return; }
                String archivo = item.getKey().getFileName().toString();
                String motivo = item.getValue().startsWith(archivo + ": ") ? item.getValue().substring(archivo.length() + 2) : item.getValue();
                setText(archivo + " — " + motivo);
            }
        });
        VBox cajaDanados = new VBox(8, new Label("Estos archivos no se pudieron leer y no aparecen en la lista."), danados, borrarDanado);
        panelDanados.setContent(cajaDanados);
        panelDanados.getStyleClass().add("panel-danados");

        estado.getStyleClass().add("estado");
        estado.setText("Carpeta: " + gestor.repositorio().carpeta());
        VBox abajo = new VBox(8, panelDanados, estado);
        abajo.setPadding(new Insets(12, 0, 0, 0));

        BorderPane raiz = new BorderPane(centro, barra, null, abajo, lista);
        raiz.setPadding(new Insets(16));

        double[] tam = App.tamanoQueCabe(1100, 680);
        Scene escena = new Scene(raiz, tam[0], tam[1]);
        App.aplicarTema(escena);
        ventana.setTitle("Gestor de máquinas — Simulador SO (SW407)");
        ventana.setScene(escena);
        ventana.setOnCloseRequest(e -> {
            if (!ventanas.isEmpty() && !confirmar("Cerrar el simulador",
                    "Hay " + ventanas.size() + " máquina(s) abiertas; se cerrarán también.", "Cerrar todo")) e.consume();
            else Platform.exit();
        });
        recargar(null);
        ventana.show();
        lista.requestFocus();
    }

    // ---------------------------------------------------------------- acciones

    private void nueva() {
        new FormularioMaquinaDialogo(ventana, "Nueva máquina", FormularioMaquina.nueva(gestor))
                .showAndWait().ifPresent(m -> intentar(() -> {
                    gestor.crear(m);
                    recargar(m.nombre());
                    avisar("Máquina «" + m.nombre() + "» creada");
                }));
    }

    private void configurar() {
        Maquina actual = elegida();
        if (actual == null) return;
        new FormularioMaquinaDialogo(ventana, "Configurar «" + actual.nombre() + "»", FormularioMaquina.configurar(gestor, actual))
                .showAndWait().ifPresent(m -> intentar(() -> {
                    gestor.actualizar(actual.nombre(), m);
                    recargar(m.nombre());
                    avisar("Máquina «" + m.nombre() + "» guardada");
                }));
    }

    private void clonar() {
        Maquina origen = elegida();
        if (origen == null) return;
        TextInputDialog d = new TextInputDialog(gestor.nombreParaCopia(origen.nombre()));
        d.initOwner(ventana);
        d.setTitle("Clonar «" + origen.nombre() + "»");
        d.setHeaderText("Nombre de la copia (misma configuración)");
        d.getDialogPane().getStylesheets().setAll(App.estilosActuales());
        Label error = new Label();
        error.getStyleClass().add("error");
        Node ok = d.getDialogPane().lookupButton(ButtonType.OK);
        d.getEditor().textProperty().addListener((o, a, v) -> {
            Optional<String> e = gestor.errorNombre(v, null);
            error.setText(e.orElse(""));
            ok.setDisable(e.isPresent());
        });
        d.getDialogPane().setContent(new VBox(8, d.getEditor(), error));
        d.showAndWait().ifPresent(nombre -> intentar(() -> {
            Maquina copia = gestor.clonar(origen.nombre(), nombre);
            recargar(copia.nombre());
            avisar("Máquina «" + copia.nombre() + "» creada a partir de «" + origen.nombre() + "»");
        }));
    }

    private void eliminar() {
        Maquina m = elegida();
        if (m == null) return;
        if (m.esPorDefecto()) {
            if (confirmar("Restaurar la máquina por defecto",
                    "La máquina por defecto no se elimina: vuelve a los valores de fábrica del curso.", "Restaurar"))
                intentar(() -> {
                    gestor.restaurarPorDefecto();
                    recargar(Maquina.NOMBRE_POR_DEFECTO);
                    avisar("Máquina por defecto restaurada");
                });
            return;
        }
        if (confirmar("Eliminar «" + m.nombre() + "»", "Se borrará su archivo. Esta acción no se puede deshacer.", "Eliminar"))
            intentar(() -> {
                gestor.eliminar(m.nombre());
                recargar(null);
                avisar("Máquina «" + m.nombre() + "» eliminada");
            });
    }

    private void iniciar() {
        Maquina m = elegida();
        if (m == null) return;
        if (!gestor.abrir(m.nombre())) {                          // ya abierta: traerla al frente
            Stage abierta = ventanas.get(m.nombre());
            if (abierta != null) abierta.toFront();
            return;
        }
        try {
            intentar(() -> {
                Optional<PlanCarga> plan = new CargaDialogo(ventana, m).showAndWait();   // [P14][P17][P18] ventana de carga
                if (plan.isEmpty()) return;                                             // cancelada: la máquina queda cerrada
                Stage s = VentanaMaquina.crear(m, plan.get());
                ventanas.put(m.nombre(), s);
                s.setOnHidden(e -> {
                    ventanas.remove(m.nombre());
                    gestor.cerrar(m.nombre());
                    actualizarBotones();
                    lista.refresh();
                });
                s.show();
                actualizarBotones();
                lista.refresh();
            });
        } finally {
            if (!ventanas.containsKey(m.nombre())) gestor.cerrar(m.nombre());   // falló al abrir
        }
    }

    private void eliminarDanado() {
        Map.Entry<Path, String> e = danados.getSelectionModel().getSelectedItem();
        if (e == null) return;
        if (confirmar("Eliminar archivo dañado", e.getKey().getFileName() + "\n" + e.getValue(), "Eliminar"))
            intentar(() -> {
                gestor.eliminarArchivoDanado(e.getKey());
                Maquina m = elegida();
                recargar(m == null ? null : m.nombre());
                avisar("Archivo " + e.getKey().getFileName() + " eliminado");
            });
    }

    // ---------------------------------------------------------------- refresco

    /** Vuelve a leer la carpeta y selecciona esa máquina (o la por defecto). */
    private void recargar(String seleccionar) {
        lista.getItems().setAll(gestor.maquinas());
        lista.getSelectionModel().select(gestor.indiceASeleccionar(seleccionar));
        lista.scrollTo(lista.getSelectionModel().getSelectedIndex());
        Map<Path, String> d = gestor.archivosDanados();
        danados.getItems().setAll(new ArrayList<>(d.entrySet()));
        panelDanados.setText("⚠ Archivos dañados (" + d.size() + ")");
        panelDanados.setVisible(!d.isEmpty());
        panelDanados.setManaged(!d.isEmpty());
        actualizarBotones();
    }

    private void mostrarDetalle(Maquina m) {
        actualizarBotones();
        if (m == null) {
            detalle.getChildren().setAll(new Label("Elige una máquina de la lista."));
            return;
        }
        Label nombre = new Label(m.nombre());
        nombre.getStyleClass().add("titulo-detalle");
        Label resumen = new Label(FichaMaquina.resumen(m.configuracion()));
        resumen.getStyleClass().add("subtitulo");
        List<Node> hijos = new ArrayList<>(List.of(new VBox(2, nombre, resumen)));
        if (m.esPorDefecto()) {
            Label nota = new Label("Máquina por defecto: se puede configurar y restaurar, pero no eliminar ni renombrar.");
            nota.getStyleClass().add("nota");
            nota.setWrapText(true);
            hijos.add(nota);
        }
        hijos.add(PanelFicha.crear(m.configuracion()));
        List<String> avisos = new FormularioMaquina(m, null).avisos();
        if (!avisos.isEmpty()) {
            VBox caja = new VBox(4);
            caja.getChildren().setAll(PanelFicha.mensajes(avisos, "⚠ ", "aviso"));
            caja.getStyleClass().add("caja-avisos");
            hijos.add(caja);
        }
        detalle.getChildren().setAll(hijos);
    }

    private void actualizarBotones() {
        Maquina m = elegida();
        boolean ninguna = m == null;
        boolean abierta = !ninguna && gestor.estaAbierta(m.nombre());
        configurar.setDisable(ninguna || abierta);
        clonar.setDisable(ninguna);
        eliminar.setDisable(ninguna || abierta);
        iniciar.setDisable(ninguna);
        eliminar.setText(!ninguna && m.esPorDefecto() ? "↺ Restaurar" : "✕ Eliminar");
        eliminar.setTooltip(new Tooltip(!ninguna && m.esPorDefecto()
                ? "Volver la máquina por defecto a los valores de fábrica" : "Borrar la máquina elegida"));
        iniciar.setText(abierta ? "↗ Ir a la ventana" : "▶ Iniciar");
        if (abierta) estado.setText("«" + m.nombre() + "» está abierta: ciérrala para configurarla o eliminarla");
    }

    // ---------------------------------------------------------------- auxiliares

    private Maquina elegida() { return lista.getSelectionModel().getSelectedItem(); }

    private void intentar(Runnable accion) {
        try {
            accion.run();
        } catch (SimuladorException | UncheckedIOException ex) {  // regla de negocio o fallo de disco; los errores de programación se propagan
            Alert a = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
            Label texto = new Label(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            texto.setWrapText(true);
            texto.setMaxWidth(480);
            a.getDialogPane().setContent(texto);
            a.initOwner(ventana);
            a.setHeaderText("No se pudo completar la acción");
            a.getDialogPane().getStylesheets().setAll(App.estilosActuales());
            a.showAndWait();
            Maquina m = elegida();
            recargar(m == null ? null : m.nombre());
        }
    }

    private boolean confirmar(String titulo, String texto, String accion) {
        ButtonType si = new ButtonType(accion, ButtonBar.ButtonData.OK_DONE);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, texto, si, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        a.initOwner(ventana);
        a.setTitle(titulo);
        a.setHeaderText(titulo);
        a.getDialogPane().getStylesheets().setAll(App.estilosActuales());
        return a.showAndWait().filter(b -> b == si).isPresent();
    }

    private void avisar(String texto) { estado.setText(texto); }

    private static Button boton(String texto, String ayuda) {
        Button b = new Button(texto);
        b.setTooltip(new Tooltip(ayuda));
        return b;
    }

    /** Celda de la lista: nombre, insignias (por defecto / abierta) y resumen de una línea. */
    private final class CeldaMaquina extends ListCell<Maquina> {
        CeldaMaquina() { setPrefWidth(0); }                       // sin barra horizontal: el resumen se recorta con «…»

        @Override protected void updateItem(Maquina m, boolean vacio) {
            super.updateItem(m, vacio);
            if (vacio || m == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label nombre = new Label(m.nombre());
            nombre.getStyleClass().add("nombre-maquina");
            HBox linea = new HBox(6, nombre);
            linea.setAlignment(Pos.CENTER_LEFT);
            if (m.esPorDefecto()) linea.getChildren().add(insignia("por defecto", "insignia-defecto"));
            if (gestor.estaAbierta(m.nombre())) linea.getChildren().add(insignia("● abierta", "insignia-abierta"));
            Label resumen = new Label(FichaMaquina.resumen(m.configuracion()));
            resumen.getStyleClass().add("resumen-maquina");
            setText(null);
            setGraphic(new VBox(2, linea, resumen));
        }

        private Label insignia(String texto, String clase) {
            Label l = new Label(texto);
            l.getStyleClass().addAll("insignia", clase);
            return l;
        }
    }
}
