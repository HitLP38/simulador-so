package pe.uni.so.ui;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntConsumer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.sesion.RegistroES;
import pe.uni.so.sesion.TablaVectores;

/**
 * [E1][E2][E6][P16] Vista «Entrada/salida»: arriba, una fila por dispositivo (velocidad, IRQ,
 * proceso atendido y lo que le falta, cola de espera, servicios, uso y cola más larga); abajo,
 * el Gantt de los dispositivos (cada barra es un servicio, del color de su proceso). Solo lee la
 * foto y el {@link RegistroES}; un clic en una barra o en una fila elige el proceso.
 */
final class VistaES extends SplitPane {

    private static final double ALTO_CARRIL = 30, CABECERA = 22, MARGEN_IZQ = 86, MARGEN_DER = 12;

    /** Fila de la tabla: la ficha del dispositivo en la foto y su resumen hasta ese instante. */
    private record Fila(Instantanea.FichaDispositivo ficha, RegistroES.ResumenDispositivo resumen) {}

    private enum Ventana {
        TODA("Toda la corrida", 0), ULTIMOS_100("Últimos 100 ticks", 100), ULTIMOS_200("Últimos 200 ticks", 200);

        final String etiqueta;
        final int ticks;

        Ventana(String etiqueta, int ticks) { this.etiqueta = etiqueta; this.ticks = ticks; }

        @Override public String toString() { return etiqueta; }
    }

    private final IntConsumer alElegir;
    private final TableView<Fila> tabla = new TableView<>();
    private final ComboBox<Ventana> ventana = new ComboBox<>(FXCollections.observableArrayList(Ventana.values()));
    private final Canvas canvas = new Canvas();
    private final Pane lamina = new Pane(canvas);
    private final Label resumen = new Label();
    private RegistroES registro;
    private Instantanea foto;
    private int seleccionado = Instantanea.NINGUNO;
    private boolean actualizando;
    private int desde, hasta;
    private final ChangeListener<Boolean> oyenteTema = (o, a, v) -> dibujar();

    VistaES(IntConsumer alElegir) {
        this.alElegir = alElegir;
        setOrientation(Orientation.VERTICAL);
        getItems().setAll(armarTabla(), armarGantt());
        setDividerPositions(0.38);
        App.temaClaro().addListener(new javafx.beans.value.WeakChangeListener<>(oyenteTema));
    }

    private VBox armarTabla() {
        Label titulo = new Label("Dispositivos y colas de E/S");
        titulo.getStyleClass().add("titulo-panel");
        tabla.getColumns().add(columna("Dispositivo", f -> nombre(f.ficha().dispositivo())));
        tabla.getColumns().add(columna("IRQ · vector", f -> {
            TablaVectores.Fila v = TablaVectores.de(f.ficha().dispositivo().interrupcion);
            return "IRQ" + v.irq() + " · " + v.vectorHex();
        }));
        tabla.getColumns().add(columna("Velocidad", f -> String.valueOf(f.ficha().velocidad())));
        tabla.getColumns().add(columna("Atiende", f -> f.ficha().atendiendo() == Instantanea.NINGUNO ? "libre"
                : "P" + f.ficha().atendiendo() + (f.ficha().operacion() == null ? "" : " · " + f.ficha().operacion().name().toLowerCase())));
        tabla.getColumns().add(columna("Le falta", f -> f.ficha().restante() < 0 ? "—" : f.ficha().restante() + " t"));
        tabla.getColumns().add(columna("En espera", f -> {
            List<Integer> c = f.ficha().cola();
            int inicio = f.ficha().atendiendo() == Instantanea.NINGUNO ? 0 : 1;
            if (c.size() <= inicio) return "—";
            StringBuilder sb = new StringBuilder();
            for (int i = inicio; i < c.size(); i++) sb.append(i == inicio ? "" : ", ").append('P').append(c.get(i));
            return sb.toString();
        }));
        tabla.getColumns().add(columna("Servicios", f -> Integer.toString(f.resumen().servicios())));
        tabla.getColumns().add(columna("Uso", f -> String.format(Locale.ROOT, "%.1f %%", f.resumen().uso())));
        tabla.getColumns().add(columna("Cola máx.", f -> Integer.toString(f.resumen().colaMaxima())));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setFixedCellSize(26);
        tabla.setPrefHeight(26 * 6 + 6);                               // encabezado + 5 dispositivos
        tabla.setMinHeight(26 * 3);
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, f) -> {
            if (f != null && !actualizando && f.ficha().atendiendo() != Instantanea.NINGUNO) alElegir.accept(f.ficha().atendiendo());
        });
        VBox caja = new VBox(4, titulo, tabla);
        caja.setPadding(new Insets(4, 8, 4, 8));
        return caja;
    }

    private VBox armarGantt() {
        Label titulo = new Label("Gantt de dispositivos");
        titulo.getStyleClass().add("titulo-panel");
        titulo.setMinWidth(Region.USE_PREF_SIZE);
        ventana.setValue(Ventana.TODA);
        ventana.setMinWidth(Region.USE_PREF_SIZE);
        ventana.valueProperty().addListener((o, a, v) -> dibujar());
        resumen.getStyleClass().add("subtitulo");
        HBox barra = new HBox(8, titulo, ventana, resumen);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(4, 8, 4, 8));
        canvas.setManaged(false);                                   // el lienzo sigue al tamaño del panel, no al revés
        lamina.setMinSize(0, 0);
        lamina.widthProperty().addListener((o, a, v) -> dibujar());
        lamina.heightProperty().addListener((o, a, v) -> dibujar());
        lamina.setOnMouseClicked(e -> {
            int carril = (int) ((e.getY() - CABECERA) / ALTO_CARRIL);
            if (registro == null || e.getY() < CABECERA || carril < 0 || carril >= Dispositivo.values().length || e.getX() < MARGEN_IZQ) return;
            double escala = (lamina.getWidth() - MARGEN_IZQ - MARGEN_DER) / Math.max(1, hasta - desde);
            int t = desde + (int) ((e.getX() - MARGEN_IZQ) / escala);
            for (RegistroES.Servicio s : registro.servicios(Dispositivo.values()[carril], foto.t()))
                if (s.desde() <= t && t < s.hasta()) { alElegir.accept(s.pid()); return; }
        });
        VBox caja = new VBox(barra, lamina);
        VBox.setVgrow(lamina, Priority.ALWAYS);
        return caja;
    }

    private static <T> TableColumn<T, String> columna(String titulo, Function<T, String> f) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(f.apply(d.getValue())));
        c.setSortable(false);
        return c;
    }

    static String nombre(Dispositivo d) {
        return switch (d) {
            case TECLADO -> "Teclado"; case DISCO -> "Disco"; case IMPRESORA -> "Impresora"; case PANTALLA -> "Pantalla"; case RED -> "Red";
        };
    }

    /** Muestra la foto mirada (llamar solo cuando la vista está a la vista). */
    void mostrar(RegistroES registro, Instantanea foto) {
        this.registro = registro;
        this.foto = foto;
        actualizando = true;
        try {
            tabla.getItems().setAll(foto.dispositivos().stream()
                    .map(d -> new Fila(d, registro.resumen(d.dispositivo(), foto.t()))).toList());
        } finally { actualizando = false; }
        int servicios = tabla.getItems().stream().mapToInt(f -> f.resumen().servicios()).sum();
        resumen.setText(servicios + " servicios de E/S hasta t = " + foto.t());
        dibujar();
    }

    void seleccionar(int pid) {
        if (pid == seleccionado) return;
        seleccionado = pid;
        dibujar();
    }

    private void dibujar() {
        double ancho = lamina.getWidth(), alto = lamina.getHeight();
        if (ancho <= 0 || alto <= 0) return;
        canvas.setWidth(ancho);
        canvas.setHeight(alto);
        Paleta p = Paleta.de(App.temaClaro().get());
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(p.fondoArriba());
        g.fillRect(0, 0, ancho, alto);
        if (registro == null || foto == null) return;
        hasta = Math.max(1, foto.t());                                // la foto t cierra el tick [t − 1, t)
        desde = ventana.getValue().ticks <= 0 ? 0 : Math.max(0, hasta - ventana.getValue().ticks);
        double util = ancho - MARGEN_IZQ - MARGEN_DER;
        double escala = util / Math.max(1, hasta - desde);

        g.setFont(Font.font("System", FontWeight.NORMAL, 10));
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.CENTER);
        int paso = VistaProcesos.pasoEje(hasta - desde, util);
        double fondo = CABECERA + Dispositivo.values().length * ALTO_CARRIL;
        for (int t = (desde + paso - 1) / paso * paso; t <= hasta; t += paso) {
            double x = MARGEN_IZQ + (t - desde) * escala;
            g.setStroke(p.bordeCarril());
            g.setLineWidth(1);
            g.strokeLine(x, CABECERA - 4, x, fondo);
            g.setFill(p.tenue());
            g.fillText(Integer.toString(t), x, CABECERA / 2);
        }
        Dispositivo[] ds = Dispositivo.values();
        for (int i = 0; i < ds.length; i++) {
            double y = CABECERA + i * ALTO_CARRIL;
            g.setTextAlign(TextAlignment.LEFT);
            g.setFill(p.texto());
            g.setFont(Font.font("System", FontWeight.BOLD, 11));
            g.fillText(nombre(ds[i]), 8, y + ALTO_CARRIL / 2);
            g.setFont(Font.font("System", FontWeight.NORMAL, 9));
            g.setTextAlign(TextAlignment.CENTER);
            for (RegistroES.Servicio s : registro.servicios(ds[i], foto.t())) {
                int a = Math.max(s.desde(), desde), b = Math.min(s.hasta(), hasta);
                if (a >= b) continue;
                double x0 = MARGEN_IZQ + (a - desde) * escala, w = Math.max(1, (b - a) * escala);
                Color c = VistaMemoria.colorDe(s.pid());
                if (seleccionado != Instantanea.NINGUNO && s.pid() != seleccionado) c = c.deriveColor(0, 0.5, 1, 0.4);
                g.setFill(c);
                g.fillRect(x0, y + 4, w, ALTO_CARRIL - 8);
                if (s.pid() == seleccionado) {
                    g.setStroke(p.oro());
                    g.setLineWidth(1.5);
                    g.strokeRect(x0 + 0.75, y + 4.75, Math.max(0.5, w - 1.5), ALTO_CARRIL - 9.5);
                }
                if (w >= 22) {
                    g.setFill(Color.web("#0b1220"));
                    g.fillText("P" + s.pid(), x0 + w / 2, y + ALTO_CARRIL / 2);
                }
            }
        }
        double xt = MARGEN_IZQ + (hasta - desde) * escala;
        g.setStroke(p.oro());
        g.setLineWidth(2);
        g.strokeLine(xt, CABECERA - 6, xt, fondo);
    }
}
