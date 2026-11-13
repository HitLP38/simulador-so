package pe.uni.so.ui;

import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import pe.uni.so.configuracion.GestorMaquinas;
import pe.uni.so.configuracion.RepositorioMaquinas;

/**
 * Interfaz del simulador. Al abrir la app aparece el gestor de máquinas (tipo VirtualBox), con
 * las máquinas guardadas en ~/.simulador-so/maquinas y la máquina por defecto preseleccionada.
 * La interfaz solo lee al motor; el motor no importa nada de este paquete.
 *
 * <p>El tema (oscuro o claro) es uno solo para todas las ventanas: {@link #temaClaro()}.
 */
public class App extends Application {

    private static final BooleanProperty TEMA_CLARO = new SimpleBooleanProperty(false);

    @Override
    public void start(Stage ventana) {
        new GestorMaquinasVista(new GestorMaquinas(RepositorioMaquinas.enCarpetaDelUsuario())).mostrar(ventana);
    }

    /** Hoja de estilos común a todas las ventanas y diálogos (tema oscuro). */
    static String estilos() { return App.class.getResource("estilos.css").toExternalForm(); }

    /** Hoja que se agrega encima de la común para el tema claro. */
    static String estilosClaros() { return App.class.getResource("estilos-claro.css").toExternalForm(); }

    /** Hojas de estilo del tema actual, para diálogos que se abren y se cierran. */
    static java.util.List<String> estilosActuales() {
        return TEMA_CLARO.get() ? java.util.List.of(estilos(), estilosClaros()) : java.util.List.of(estilos());
    }

    /** true = tema claro. Todas las ventanas escuchan esta propiedad. */
    static BooleanProperty temaClaro() { return TEMA_CLARO; }

    /** Aplica los estilos y el tema actual a una escena, y la mantiene al día si el tema cambia. */
    static void aplicarTema(Scene escena) {
        escena.getStylesheets().setAll(estilos());
        if (TEMA_CLARO.get()) escena.getStylesheets().add(estilosClaros());
        ChangeListener<Boolean> oyente = (o, a, claro) -> {
            escena.getStylesheets().remove(estilosClaros());
            if (claro) escena.getStylesheets().add(estilosClaros());
        };
        TEMA_CLARO.addListener(oyente);
        // al cerrar la ventana se quita el oyente: la propiedad global no retiene la escena cerrada
        Runnable alCerrar = () -> TEMA_CLARO.removeListener(oyente);
        if (escena.getWindow() != null) escena.getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> alCerrar.run());
        else escena.windowProperty().addListener((o, a, w) -> {
            if (w != null) w.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> alCerrar.run());
        });
    }

    /** Botón ☀/☾ que alterna el tema. */
    static Button botonTema() {
        Button b = new Button();
        b.getStyleClass().add("boton-icono");
        Runnable rotular = () -> {
            b.setText(TEMA_CLARO.get() ? "☾" : "☀");
            b.setTooltip(new Tooltip(TEMA_CLARO.get() ? "Cambiar a tema oscuro" : "Cambiar a tema claro"));
        };
        rotular.run();
        ChangeListener<Boolean> oyente = (o, a, v) -> rotular.run();
        b.getProperties().put("oyenteTema", oyente);                 // vive lo que viva el botón
        TEMA_CLARO.addListener(new WeakChangeListener<>(oyente));    // la propiedad global no retiene el botón
        b.setOnAction(e -> TEMA_CLARO.set(!TEMA_CLARO.get()));
        return b;
    }

    /**
     * Ancho y alto para abrir una ventana sin que pase de la pantalla (la barra de título siempre
     * queda a la vista): el pedido, recortado al área útil de la pantalla principal.
     */
    static double[] tamanoQueCabe(double ancho, double alto) {
        Rectangle2D util = Screen.getPrimary().getVisualBounds();
        return new double[] {Math.min(ancho, util.getWidth() - 40), Math.min(alto, util.getHeight() - 60)};
    }

    public static void main(String[] args) { launch(args); }
}
