package pe.uni.so.ui;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import pe.uni.so.procesos.CodigoError;
import pe.uni.so.procesos.CreadorProcesos;
import pe.uni.so.sesion.PlanCarga;
import pe.uni.so.sesion.SesionSimulacion;

/**
 * [P14][P18] Diálogos para ingresar procesos con la simulación en curso: «+ Proceso» (un proceso
 * que ingresa el usuario) y «Generar N» (procesos aleatorios que crea el SO). Solo piden números
 * dentro de sus rangos; la sesión valida el resto.
 */
final class IngresoDialogo {

    /** Datos de un proceso manual: llegada (tick), ráfaga, ejecutable en KiB y prioridad. */
    record Datos(int llegada, int burst, int kib, int prioridad) {}

    private IngresoDialogo() {}

    /** Pide un proceso manual que llega desde el instante {@code t} (por defecto, el tick siguiente). */
    static Optional<Datos> proceso(Window duenio, int t) {
        Dialog<Datos> d = base(duenio, "Ingresar proceso", "El proceso entra por «Ingresado por usuario» en su instante de llegada.");
        Spinner<Integer> llegada = spinner(t, SesionSimulacion.LIMITE_TICKS, t + 1);
        Spinner<Integer> burst = spinner(CreadorProcesos.BURST_MINIMO, CreadorProcesos.BURST_MAXIMO, 20);
        Spinner<Integer> kib = spinner(PlanCarga.KIB_MINIMO, PlanCarga.KIB_MAXIMO, 512);
        Spinner<Integer> prioridad = spinner(CreadorProcesos.PRIORIDAD_MINIMA, CreadorProcesos.PRIORIDAD_MAXIMA, CreadorProcesos.PRIORIDAD_MANUAL);
        GridPane g = grilla();
        fila(g, 0, "Llegada (tick)", llegada);
        fila(g, 1, "Ráfaga (instrucciones)", burst);
        fila(g, 2, "Ejecutable (KiB)", kib);
        fila(g, 3, "Prioridad (1 = más alta)", prioridad);
        d.getDialogPane().setContent(g);
        ButtonType ok = d.getDialogPane().getButtonTypes().get(0);
        d.setResultConverter(b -> b == ok ? new Datos(valor(llegada), valor(burst), valor(kib), valor(prioridad)) : null);
        d.setOnShown(e -> burst.requestFocus());
        return d.showAndWait();
    }

    /** Pide cuántos procesos aleatorios generar (1 a {@link SesionSimulacion#MAX_GENERAR}). */
    static Optional<Integer> generar(Window duenio) {
        Dialog<Integer> d = base(duenio, "Generar procesos", "El SO crea N procesos aleatorios (ecuación 8) que llegan desde el tick siguiente.");
        Spinner<Integer> n = spinner(1, SesionSimulacion.MAX_GENERAR, 10);
        GridPane g = grilla();
        fila(g, 0, "Cantidad (N)", n);
        d.getDialogPane().setContent(g);
        ButtonType ok = d.getDialogPane().getButtonTypes().get(0);
        d.setResultConverter(b -> b == ok ? valor(n) : null);
        d.setOnShown(e -> n.requestFocus());
        return d.showAndWait();
    }

    /**
     * [P9] «Provocar error»: elige el código (E01–E03) con el que fallará el proceso {@code pid}, que
     * tiene la CPU, al ejecutar su próxima instrucción.
     */
    static Optional<CodigoError> provocarError(Window duenio, int pid) {
        Dialog<CodigoError> d = base(duenio, "Provocar error", "P" + pid + " tiene la CPU: su próxima instrucción provocará la excepción "
                + "elegida y el módulo de errores lo abortará con ese código (queda en su PCB y en las estadísticas).", "Provocar");
        ToggleGroup grupo = new ToggleGroup();
        VBox opciones = new VBox(8);
        for (CodigoError c : CodigoError.DE_EXCEPCION) {
            RadioButton r = new RadioButton(c.etiqueta());
            r.setUserData(c);
            r.setToggleGroup(grupo);
            opciones.getChildren().add(r);
        }
        grupo.selectToggle(grupo.getToggles().get(0));
        opciones.setPadding(new Insets(12));
        d.getDialogPane().setContent(opciones);
        d.getDialogPane().setPrefWidth(460);                    // la nota se ajusta en vez de ensanchar el diálogo
        d.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);   // alto según la nota ya ajustada: los botones no tapan E03
        ButtonType ok = d.getDialogPane().getButtonTypes().get(0);
        d.setResultConverter(b -> b == ok && grupo.getSelectedToggle() != null ? (CodigoError) grupo.getSelectedToggle().getUserData() : null);
        return d.showAndWait();
    }

    private static <T> Dialog<T> base(Window duenio, String titulo, String nota) {
        return base(duenio, titulo, nota, "Ingresar");
    }

    private static <T> Dialog<T> base(Window duenio, String titulo, String nota, String textoAceptar) {
        Dialog<T> d = new Dialog<>();
        d.initOwner(duenio);
        d.setTitle(titulo);
        d.setHeaderText(null);
        d.getDialogPane().getStylesheets().setAll(App.estilosActuales());
        d.getDialogPane().getStyleClass().add("formulario");
        ButtonType aceptar = new ButtonType(textoAceptar, ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(aceptar, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        d.getDialogPane().lookupButton(aceptar).getStyleClass().add("boton-primario");
        Label l = new Label(nota);
        l.setWrapText(true);
        l.getStyleClass().add("nota");
        d.getDialogPane().setHeader(new VBox(l));
        ((VBox) d.getDialogPane().getHeader()).setPadding(new Insets(12, 12, 0, 12));
        return d;
    }

    /** Spinner editable que solo acepta dígitos. */
    static Spinner<Integer> spinner(int min, int max, int inicial) {
        SpinnerValueFactory.IntegerSpinnerValueFactory f =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, Math.max(min, Math.min(max, inicial)));
        f.setConverter(new StringConverter<>() {                // texto vacío o inválido: conserva el último valor válido
            @Override public String toString(Integer v) { return v == null ? "" : v.toString(); }
            @Override public Integer fromString(String texto) {
                Integer actual = f.getValue() == null ? f.getMin() : f.getValue();
                if (texto == null || texto.isBlank()) return actual;
                try {
                    long v = Long.parseLong(texto.strip());
                    return (int) Math.max(f.getMin(), Math.min(f.getMax(), v));
                } catch (NumberFormatException e) {
                    return actual;
                }
            }
        });
        Spinner<Integer> s = new Spinner<>(f);
        s.setEditable(true);
        String digitos = "\\d{0," + String.valueOf(max).length() + "}";      // tantas cifras como el máximo (semilla: 10)
        s.getEditor().setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches(digitos) ? c : null));
        s.focusedProperty().addListener((o, a, enfocado) -> { if (!enfocado) valor(s); });
        s.setPrefWidth(140);
        return s;
    }

    /** Lleva al spinner lo escrito en su editor (acotado a su rango) y devuelve el valor. */
    static int valor(Spinner<Integer> s) {
        SpinnerValueFactory.IntegerSpinnerValueFactory f = (SpinnerValueFactory.IntegerSpinnerValueFactory) s.getValueFactory();
        f.setValue(f.getConverter().fromString(s.getEditor().getText()));
        if (f.getValue() == null) f.setValue(f.getMin());
        s.getEditor().setText(String.valueOf(f.getValue()));
        return f.getValue();
    }

    static GridPane grilla() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(8);
        g.setPadding(new Insets(12));
        return g;
    }

    static void fila(GridPane g, int fila, String texto, javafx.scene.Node control) {
        Label l = new Label(texto);
        l.getStyleClass().add("etiqueta-campo");
        l.setLabelFor(control);
        g.addRow(fila, l, control);
    }
}
