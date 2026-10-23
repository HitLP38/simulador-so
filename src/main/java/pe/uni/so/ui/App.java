package pe.uni.so.ui;

import java.util.List;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.PoliticaPlanificacion;
import pe.uni.so.planificacion.Prioridad;
import pe.uni.so.planificacion.RoundRobin;
import pe.uni.so.planificacion.SimuladorCpu;
import pe.uni.so.planificacion.Sjf;
import pe.uni.so.planificacion.Srtf;
import pe.uni.so.procesos.Pcb;

/** Interfaz mínima (solo lee resultados del motor). Se amplía en el paquete de visualización. */
public class App extends Application {

    @Override
    public void start(Stage ventana) {
        ComboBox<String> politicas = new ComboBox<>();
        politicas.getItems().addAll("FCFS", "SJF", "SRTF", "Prioridad (NE)", "Prioridad (E)", "RR q=2");
        politicas.getSelectionModel().selectFirst();
        Button ejecutar = new Button("Ejecutar lote de prueba");
        TextArea salida = new TextArea();
        salida.setEditable(false);
        salida.getStyleClass().add("salida");

        ejecutar.setOnAction(e -> salida.setText(simular(politicas.getValue())));

        HBox barra = new HBox(12, new Label("Política"), politicas, ejecutar);
        barra.getStyleClass().add("barra");
        BorderPane raiz = new BorderPane(salida, barra, null, null, null);
        raiz.setPadding(new Insets(16));

        Scene escena = new Scene(raiz, 900, 560);
        escena.getStylesheets().add(App.class.getResource("estilos.css").toExternalForm());
        ventana.setTitle("Simulador SO - SW407");
        ventana.setScene(escena);
        ventana.show();
    }

    private String simular(String nombre) {
        PoliticaPlanificacion politica = switch (nombre) {
            case "SJF" -> new Sjf();
            case "SRTF" -> new Srtf();
            case "Prioridad (NE)" -> new Prioridad(false);
            case "Prioridad (E)" -> new Prioridad(true);
            case "RR q=2" -> new RoundRobin(2);
            default -> new Fcfs();
        };
        // Lote del taller 2026-2, ejercicio 24 (llegada, ráfaga, prioridad)
        List<Pcb> lote = List.of(new Pcb(1, 0, 7, 3), new Pcb(2, 2, 4, 1), new Pcb(3, 3, 2, 2), new Pcb(4, 5, 1, 1));
        SimuladorCpu.Resultado r = new SimuladorCpu(politica, lote).ejecutar();
        StringBuilder sb = new StringBuilder(politica.nombre() + "\nGantt: " + r.gantt() + "\n\n");
        sb.append("Proceso  Llegada  CPU  Inicio  Fin  Retorno  Espera  Respuesta\n");
        for (Pcb p : r.procesos())
            sb.append(String.format("P%-7d %-8d %-4d %-7d %-4d %-8d %-7d %d%n",
                    p.pid, p.llegada, p.burst, p.inicio(), p.fin(), p.retorno(), p.espera(), p.respuesta()));
        sb.append(String.format("%nPromedios -> retorno %.2f  espera %.2f  respuesta %.2f  | transiciones %d%n",
                r.retornoMedio(), r.esperaMedia(), r.respuestaMedia(), r.transiciones()));
        return sb.toString();
    }

    public static void main(String[] args) { launch(args); }
}
