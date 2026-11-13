package pe.uni.so.ui;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
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

    private static <T> Dialog<T> base(Window duenio, String titulo, String nota) {
        Dialog<T> d = new Dialog<>();
        d.initOwner(duenio);
        d.setTitle(titulo);
        d.setHeaderText(null);
        d.getDialogPane().getStylesheets().setAll(App.estilosActuales());
        d.getDialogPane().getStyleClass().add("formulario");
        ButtonType aceptar = new ButtonType("Ingresar", ButtonBar.ButtonData.OK_DONE);
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
        Spinner<Integer> s = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, Math.max(min, Math.min(max, inicial))));
        s.setEditable(true);
        s.getEditor().setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,9}") ? c : null));
        s.focusedProperty().addListener((o, a, f) -> { if (!f) valor(s); });
        s.setPrefWidth(140);
        return s;
    }

    /** Lleva al spinner lo escrito en su editor (acotado a su rango) y devuelve el valor. */
    static int valor(Spinner<Integer> s) {
        SpinnerValueFactory.IntegerSpinnerValueFactory f = (SpinnerValueFactory.IntegerSpinnerValueFactory) s.getValueFactory();
        String texto = s.getEditor().getText();
        if (texto != null && !texto.isBlank()) {
            long v = Long.parseLong(texto.strip());
            f.setValue((int) Math.max(f.getMin(), Math.min(f.getMax(), v)));
        }
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
