package pe.uni.so.ui;

import javafx.application.Application;
import javafx.stage.Stage;
import pe.uni.so.configuracion.GestorMaquinas;
import pe.uni.so.configuracion.RepositorioMaquinas;

/**
 * Interfaz del simulador. Al abrir la app aparece el gestor de máquinas (tipo VirtualBox), con
 * las máquinas guardadas en ~/.simulador-so/maquinas y la máquina por defecto preseleccionada.
 * La interfaz solo lee al motor; el motor no importa nada de este paquete.
 */
public class App extends Application {

    @Override
    public void start(Stage ventana) {
        new GestorMaquinasVista(new GestorMaquinas(RepositorioMaquinas.enCarpetaDelUsuario())).mostrar(ventana);
    }

    /** Hoja de estilos común a todas las ventanas y diálogos. */
    static String estilos() { return App.class.getResource("estilos.css").toExternalForm(); }

    public static void main(String[] args) { launch(args); }
}
