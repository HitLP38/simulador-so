package pe.uni.so.ui;

import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.procesos.Estado;
import pe.uni.so.sesion.Propiedades;

/**
 * [G5] Panel inferior «Eventos y E/S» del espacio de trabajo. Reúne lo que en el diagrama del
 * curso son la cola de eventos E/S, el módulo de administrar señales y los eventos varios (TIMER,
 * aborts), para que la arena solo muestre el flujo principal. Se oculta desde su propia barra y
 * vuelve desde el menú Ver, como el explorador y el panel derecho.
 */
final class PanelEventos extends VBox {

    private final HBox colaES = new HBox(8);                     // una columna por dispositivo
    private final VBox senales = new VBox(3), eventos = new VBox(3);

    PanelEventos(BooleanProperty visible) {
        super(4);
        getStyleClass().addAll("panel-lateral", "panel-eventos");
        Label titulo = new Label("Eventos y E/S");
        titulo.getStyleClass().add("titulo-panel");
        Button ocultar = new Button("⌄");
        ocultar.getStyleClass().addAll("boton-icono", "boton-ocultar");
        ocultar.setTooltip(new Tooltip("Ocultar eventos y E/S (se vuelve a mostrar en Ver)"));
        ocultar.setOnAction(e -> visible.set(false));
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox barra = new HBox(6, titulo, hueco, ocultar);
        barra.setAlignment(Pos.CENTER_LEFT);

        VBox tarjetaES = tarjeta("COLA DE EVENTOS E/S", "bloqueados por dispositivo", colaES, 1);
        HBox.setHgrow(tarjetaES, Priority.NEVER);                    // ancho justo para los 5 dispositivos
        tarjetaES.setPrefWidth(Region.USE_COMPUTED_SIZE);
        tarjetaES.setMinWidth(Region.USE_PREF_SIZE);
        HBox tarjetas = new HBox(10, tarjetaES,
                tarjeta("MÓD. SEÑALES", "manejador de interrupciones · PIC 8259", senales, 1),
                tarjeta("EVENTOS VARIOS", "TIMER (IRQ0), aborts, otros · despachador", eventos, 1.2));
        tarjetas.setFillHeight(true);
        ScrollPane desplazable = new ScrollPane(tarjetas);
        desplazable.setFitToWidth(true);
        desplazable.setFitToHeight(true);
        desplazable.getStyleClass().add("detalle-scroll");
        VBox.setVgrow(desplazable, Priority.ALWAYS);
        getChildren().addAll(barra, desplazable);
        setMinHeight(90);
    }

    private static VBox tarjeta(String titulo, String tecnico, javafx.scene.Node contenido, double peso) {
        Label t = new Label(titulo);
        t.getStyleClass().add("titulo-tarjeta");
        Label sub = new Label(tecnico);
        sub.getStyleClass().add("subtitulo");
        sub.setStyle("-fx-font-size: 11px;");
        VBox v = new VBox(4, new VBox(0, t, sub), contenido);
        v.getStyleClass().add("tarjeta");
        v.setPadding(new Insets(8, 12, 8, 12));
        HBox.setHgrow(v, Priority.ALWAYS);
        v.setMinWidth(200);
        v.setPrefWidth(300 * peso);
        return v;
    }

    /** Actualiza las tres tarjetas con la foto mirada. */
    void mostrar(Instantanea f) {
        colaES.getChildren().clear();
        java.util.Set<Integer> enDispositivo = new java.util.HashSet<>();
        for (Instantanea.FichaDispositivo d : f.dispositivos()) {
            FlowPane chips = new FlowPane(3, 3);
            if (!d.libre()) {
                chips.getChildren().add(chip(f, d.atendiendo(), true));
                enDispositivo.add(d.atendiendo());
            }
            for (int pid : d.cola())
                if (pid != d.atendiendo()) {
                    chips.getChildren().add(chip(f, pid, false));
                    enDispositivo.add(pid);
                }
            colaES.getChildren().add(columna(nombre(d.dispositivo().name()), d.libre() ? "libre" : "faltan " + d.restante() + " t",
                    !d.libre(), chips));
        }
        List<Integer> otros = f.en(Estado.BLOQUEADO).stream().map(Instantanea.Proceso::pid).filter(p -> !enDispositivo.contains(p)).toList();
        if (!otros.isEmpty()) {
            FlowPane chips = new FlowPane(3, 3);
            otros.forEach(p -> chips.getChildren().add(chip(f, p, false)));
            colaES.getChildren().add(columna("Otros", "", false, chips));
        }
        List<Propiedades.Fila> kernel = Propiedades.deKernel(f);
        pintar(senales, kernel, "Señales (PIC 8259)");
        pintar(eventos, kernel, "Eventos varios", "Despachador");
    }

    /** Columna de un dispositivo: nombre, estado y sus procesos (el atendido primero). */
    private static VBox columna(String nombre, String estado, boolean ocupado, FlowPane chips) {
        Label n = new Label(nombre);
        n.getStyleClass().add("etiqueta-campo");
        Label e = new Label(estado);
        e.getStyleClass().add(ocupado ? "dispositivo-ocupado" : "etiqueta-campo");
        e.setStyle("-fx-font-size: 11px;");
        chips.setPrefWrapLength(70);
        VBox v = new VBox(2, n, e, chips);
        v.setMinWidth(Region.USE_PREF_SIZE);
        v.setPrefWidth(78);
        return v;
    }

    /** Ficha pequeña de un proceso con el color de su estado; la atendida, resaltada. */
    private static Label chip(Instantanea f, int pid, boolean atendido) {
        Label l = new Label("P" + pid);
        String color = f.proceso(pid).map(Instantanea.Proceso::color).orElse("#F28C28");
        l.setStyle("-fx-background-color: " + color + (atendido ? "" : "55") + "; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-background-radius: 6; -fx-padding: 1 6; -fx-font-size: 11px;"
                + (atendido ? " -fx-border-color: #fde68a; -fx-border-radius: 6; -fx-border-width: 1.5;" : ""));
        l.setTooltip(new Tooltip(atendido ? "En servicio" : "En cola"));
        return l;
    }

    private static void pintar(VBox destino, List<Propiedades.Fila> filas, String... grupos) {
        destino.getChildren().clear();
        GridPane g = new GridPane();                                 // pares clave–valor en dos columnas: cabe sin desplazar
        g.setHgap(10);
        g.setVgap(2);
        int i = 0;
        for (Propiedades.Fila f : filas) {
            if (!List.of(grupos).contains(f.grupo())) continue;
            Label k = new Label(f.clave());
            k.getStyleClass().add("etiqueta-campo");
            k.setStyle("-fx-font-size: 12px;");
            k.setMinWidth(Region.USE_PREF_SIZE);
            Label v = new Label(f.valor());
            v.getStyleClass().add("valor-campo");
            v.setStyle("-fx-font-size: 12px;");
            v.setMinWidth(30);
            v.setTooltip(new Tooltip(f.clave() + ": " + f.valor()));          // el texto completo, si no cabe
            g.add(k, (i % 2) * 2, i / 2);
            g.add(v, (i % 2) * 2 + 1, i / 2);
            i++;
        }
        destino.getChildren().add(g);
    }

    private static String nombre(String enumName) {
        String n = enumName.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
