package pe.uni.so.ui;

import java.util.List;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.FormularioMaquina;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.VelocidadDispositivo;
import pe.uni.so.memoria.Reemplazo;

/**
 * [G5] Diálogo Nueva / Configurar máquina. Cada control solo ofrece opciones válidas (listas y
 * rangos de {@link Configuracion}); el quantum se habilita solo con Round Robin y el reemplazo
 * solo con paginación bajo demanda. «Aceptar» queda deshabilitado mientras haya errores, y los
 * avisos (máquina modesta, capacidad con el lote oficial) se actualizan al cambiar cualquier campo.
 */
final class FormularioMaquinaDialogo extends Dialog<Maquina> {

    private static final double ANCHO_ETIQUETA = 210, ANCHO_CONTROL = 260;

    private final FormularioMaquina modelo;
    private final VBox errores = new VBox(4);
    private final VBox avisos = new VBox(4);
    private final Label marcos = new Label();
    private final Spinner<Integer> quantum;
    private final ComboBox<Reemplazo> reemplazo;
    private final Node aceptar;

    FormularioMaquinaDialogo(Window duenio, String titulo, FormularioMaquina modelo) {
        this.modelo = modelo;
        initOwner(duenio);
        setTitle(titulo);
        setHeaderText(null);
        setResizable(true);
        getDialogPane().getStylesheets().add(App.estilos());
        getDialogPane().getStyleClass().add("formulario");

        ButtonType aceptarTipo = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(aceptarTipo, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        aceptar = getDialogPane().lookupButton(aceptarTipo);
        aceptar.getStyleClass().add("boton-primario");

        // ---- General
        TextField nombre = new TextField(modelo.nombre());
        nombre.setPrefColumnCount(26);
        nombre.textProperty().addListener((o, a, v) -> cambiar(() -> modelo.nombre(v)));
        TextField semilla = new TextField(modelo.semilla());
        semilla.setTextFormatter(new TextFormatter<String>(c ->
                c.getControlNewText().matches("\\d{0," + FormularioMaquina.DIGITOS_SEMILLA + "}") ? c : null));
        semilla.textProperty().addListener((o, a, v) -> cambiar(() -> modelo.semilla(v)));
        GridPane general = grilla();
        fila(general, 0, "Nombre", nombre);
        fila(general, 1, "Semilla", semilla);

        // ---- Memoria
        ComboBox<Configuracion.Ram> ram = combo(Configuracion.Ram.values(), modelo.ram(), modelo::ram);
        ComboBox<Configuracion.Pagina> pagina = combo(Configuracion.Pagina.values(), modelo.pagina(), modelo::pagina);
        ComboBox<Configuracion.Carga> carga = combo(Configuracion.Carga.values(), modelo.carga(), modelo::carga);
        reemplazo = combo(Reemplazo.values(), modelo.reemplazo(), modelo::reemplazo);
        ComboBox<Configuracion.Estrategia> estrategia = combo(Configuracion.Estrategia.values(), modelo.estrategia(), modelo::estrategia);
        marcos.getStyleClass().add("dato-calculado");
        GridPane memoria = grilla();
        fila(memoria, 0, "RAM", ram);
        fila(memoria, 1, "Página / marco", pagina);
        fila(memoria, 2, "Marcos", marcos);
        fila(memoria, 3, "Carga", carga);
        fila(memoria, 4, "Reemplazo", reemplazo);
        fila(memoria, 5, "Asignación de huecos", estrategia);

        // ---- CPU y planificación
        ComboBox<Configuracion.Cpu> cpu = combo(Configuracion.Cpu.values(), modelo.cpu(), modelo::cpu);
        ComboBox<Configuracion.Politica> politica = combo(Configuracion.Politica.values(), modelo.politica(), modelo::politica);
        quantum = new Spinner<>(Configuracion.QUANTUM_MINIMO, Configuracion.QUANTUM_MAXIMO, modelo.quantum());
        quantum.valueProperty().addListener((o, a, v) -> cambiar(() -> modelo.quantum(v)));
        ComboBox<Double> costo = new ComboBox<>(FXCollections.observableArrayList(FormularioMaquina.costos()));
        costo.setConverter(new StringConverter<>() {
            @Override public String toString(Double d) { return d == null ? "" : FormularioMaquina.etiquetaCosto(d); }
            @Override public Double fromString(String s) { return null; }
        });
        costo.setValue(modelo.costoDespacho());
        costo.valueProperty().addListener((o, a, v) -> { if (v != null) cambiar(() -> modelo.costoDespacho(v)); });
        Spinner<Integer> grado = new Spinner<>(Configuracion.GRADO_MINIMO, Configuracion.GRADO_MAXIMO, modelo.grado());
        grado.valueProperty().addListener((o, a, v) -> cambiar(() -> modelo.grado(v)));
        GridPane procesador = grilla();
        fila(procesador, 0, "Velocidad", cpu);
        fila(procesador, 1, "Política", politica);
        fila(procesador, 2, "Quantum (ticks)", quantum);
        fila(procesador, 3, "Cambio de contexto", costo);
        fila(procesador, 4, "Grado de multiprogramación", grado);

        // ---- Dispositivos
        GridPane dispositivos = grilla();
        int i = 0;
        for (Dispositivo d : Dispositivo.values()) {
            ComboBox<VelocidadDispositivo> v = combo(VelocidadDispositivo.values(), modelo.dispositivo(d), x -> modelo.dispositivo(d, x));
            fila(dispositivos, i++, etiqueta(d), v);
        }

        errores.getStyleClass().add("caja-errores");
        avisos.getStyleClass().add("caja-avisos");
        VBox contenido = new VBox(10,
                seccion("General", general), seccion("Memoria", memoria), seccion("CPU y planificación", procesador),
                seccion("Dispositivos", dispositivos), avisos);
        contenido.setPadding(new Insets(4));
        ScrollPane desplazable = new ScrollPane(contenido);
        desplazable.setFitToWidth(true);
        desplazable.setPrefViewportHeight(560);
        desplazable.getStyleClass().add("formulario-scroll");
        getDialogPane().setContent(new VBox(8, desplazable, errores));   // los errores siempre a la vista
        getDialogPane().setPrefWidth(560);

        refrescar();
        setResultConverter(b -> b == aceptarTipo ? modelo.construir() : null);
        setOnShown(e -> nombre.requestFocus());
    }

    /** Aplica el cambio al modelo y vuelve a calcular habilitados, errores y avisos. */
    private void cambiar(Runnable cambio) {
        cambio.run();
        refrescar();
    }

    private void refrescar() {
        quantum.setDisable(!modelo.quantumHabilitado());
        reemplazo.setDisable(!modelo.reemplazoHabilitado());
        marcos.setText(String.valueOf(modelo.marcos()));
        List<String> e = modelo.errores();
        errores.getChildren().setAll(e.stream().map(t -> mensaje("✕ " + t, "error")).toList());
        errores.setVisible(!e.isEmpty());
        errores.setManaged(!e.isEmpty());
        List<String> a = modelo.avisos();
        avisos.getChildren().setAll(a.stream().map(t -> mensaje("⚠ " + t, "aviso")).toList());
        avisos.setVisible(!a.isEmpty());
        avisos.setManaged(!a.isEmpty());
        aceptar.setDisable(!e.isEmpty());
    }

    /** Lista cerrada de opciones; al elegir una, la pasa al modelo y refresca el formulario. */
    private <T> ComboBox<T> combo(T[] opciones, T actual, Consumer<T> alCambiar) {
        ComboBox<T> c = new ComboBox<>(FXCollections.observableArrayList(opciones));
        c.setValue(actual);
        c.setMaxWidth(Double.MAX_VALUE);
        c.valueProperty().addListener((o, a, v) -> { if (v != null) cambiar(() -> alCambiar.accept(v)); });
        return c;
    }

    private static GridPane grilla() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(8);
        g.setPadding(new Insets(8));
        ColumnConstraints etiquetas = new ColumnConstraints(ANCHO_ETIQUETA);   // las secciones quedan alineadas
        ColumnConstraints controles = new ColumnConstraints(ANCHO_CONTROL);
        g.getColumnConstraints().addAll(etiquetas, controles);
        return g;
    }

    private static void fila(GridPane g, int fila, String texto, Node control) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        if (control instanceof javafx.scene.control.Control c) c.setMaxWidth(Double.MAX_VALUE);
        g.addRow(fila, l, control);
    }

    private static TitledPane seccion(String titulo, Node contenido) {
        TitledPane t = new TitledPane(titulo, contenido);
        t.setCollapsible(true);
        t.getStyleClass().add("seccion");
        return t;
    }

    private static Label mensaje(String texto, String clase) {
        Label l = new Label(texto);
        l.setWrapText(true);
        l.getStyleClass().add(clase);
        return l;
    }

    static String etiqueta(Dispositivo d) {
        String n = d.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
