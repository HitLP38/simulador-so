package pe.uni.so.ui;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pe.uni.so.configuracion.FichaMaquina;
import pe.uni.so.configuracion.Maquina;

/**
 * [G5] Ventana de una máquina iniciada. Por ahora es mínima: muestra la ficha y permite correr el
 * lote oficial completo para comprobar la máquina. El espacio de trabajo completo (explorador,
 * propiedades, lienzo con zoom, línea de tiempo) llega en el PR #21.
 */
final class VentanaMaquina {

    private VentanaMaquina() {}

    static Stage crear(Maquina maquina) {
        Label titulo = new Label(maquina.nombre());
        titulo.getStyleClass().add("titulo");
        Label resumen = new Label(FichaMaquina.resumen(maquina.configuracion()));
        resumen.getStyleClass().add("subtitulo");

        TextArea salida = new TextArea("El espacio de trabajo completo llega en la siguiente versión.\n"
                + "Mientras tanto, «Correr lote oficial» simula los 20 procesos del informe en esta máquina.");
        salida.setEditable(false);
        salida.setWrapText(true);
        salida.getStyleClass().add("salida");

        Button correr = new Button("▶ Correr lote oficial");
        correr.getStyleClass().add("boton-primario");
        correr.setOnAction(e -> {
            correr.setDisable(true);
            salida.setText("Simulando…");
            Task<FichaMaquina.Prueba> tarea = new Task<>() {
                @Override protected FichaMaquina.Prueba call() { return FichaMaquina.probarLoteOficial(maquina.configuracion()); }
            };
            tarea.setOnSucceeded(x -> {
                FichaMaquina.Prueba p = tarea.getValue();
                salida.setText(p.mensaje() + (p.ok() ? "\n\nGantt (pid por tick):\n" + p.gantt() : ""));
                correr.setDisable(false);
            });
            tarea.setOnFailed(x -> {
                salida.setText("Error inesperado: " + tarea.getException());
                correr.setDisable(false);
            });
            Thread hilo = new Thread(tarea, "lote-oficial");
            hilo.setDaemon(true);
            hilo.start();
        });

        VBox cabecera = new VBox(2, titulo, resumen);
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox barra = new HBox(12, cabecera, hueco, correr);
        barra.getStyleClass().add("barra");

        Node ficha = PanelFicha.crear(maquina.configuracion());
        BorderPane.setMargin(ficha, new Insets(12, 0, 0, 0));
        BorderPane raiz = new BorderPane(salida, barra, null, ficha, null);
        raiz.setPadding(new Insets(16));

        Scene escena = new Scene(raiz, 960, 640);
        escena.getStylesheets().add(App.estilos());
        Stage ventana = new Stage();
        ventana.setTitle(maquina.nombre() + " — Simulador SO");
        ventana.setScene(escena);
        return ventana;
    }
}
