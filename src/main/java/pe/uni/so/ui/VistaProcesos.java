package pe.uni.so.ui;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
import pe.uni.so.motor.ColorEstado;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.motor.Momento;
import pe.uni.so.procesos.Estado;
import pe.uni.so.sesion.DiagramaGantt;
import pe.uni.so.sesion.DiagramaGantt.Filtro;
import pe.uni.so.sesion.MapaEstados;
import pe.uni.so.sesion.RegistroProcesos;
import pe.uni.so.sesion.RegistroProcesos.FilaMetricas;

/**
 * [P1][P6][P16][P17] Vista «Procesos y Gantt»: arriba el diagrama de Gantt de la corrida hasta el
 * instante mirado (una fila por proceso, colores de los 5 estados); abajo, en pestañas, la
 * bitácora de sucesos con su momento a–e y las métricas por proceso con sus promedios.
 * Solo lee el {@link RegistroProcesos} y la foto; un clic en una fila elige el proceso.
 */
final class VistaProcesos extends SplitPane {

    private static final double ALTO_FILA = 18, CABECERA = 22, MARGEN_IZQ = 64, MARGEN_DER = 12;
    private static final int MAX_BITACORA = 500;

    /** Ventanas de tiempo del Gantt (ticks hacia atrás; 0 = toda la corrida). */
    private enum Ventana {
        TODA("Toda la corrida", 0), ULTIMOS_50("Últimos 50 ticks", 50), ULTIMOS_100("Últimos 100 ticks", 100),
        ULTIMOS_200("Últimos 200 ticks", 200);

        final String etiqueta;
        final int ticks;

        Ventana(String etiqueta, int ticks) { this.etiqueta = etiqueta; this.ticks = ticks; }

        @Override public String toString() { return etiqueta; }
    }

    private final IntConsumer alElegir;
    private final ComboBox<Ventana> ventana = new ComboBox<>(FXCollections.observableArrayList(Ventana.values()));
    private final ComboBox<Filtro> filtro = new ComboBox<>(FXCollections.observableArrayList(Filtro.values()));
    private final Canvas canvas = new Canvas();
    private final Pane lamina = new Pane(canvas);
    private final ScrollPane desplazable = new ScrollPane(lamina);
    private final Label resumenGantt = new Label();

    private final ComboBox<String> filtroMomento = new ComboBox<>();
    private final CheckBox soloElegido = new CheckBox("Solo el proceso elegido");
    private final TableView<Instantanea.Suceso> tablaBitacora = new TableView<>();
    private final Label conteo = new Label();
    private final TableView<FilaMetricas> tablaMetricas = new TableView<>();
    private final Label promedios = new Label();

    private RegistroProcesos registro;
    private Instantanea foto;
    private DiagramaGantt gantt;
    private int seleccionado = Instantanea.NINGUNO;
    private final ChangeListener<Boolean> oyenteTema = (o, a, v) -> dibujar();

    VistaProcesos(IntConsumer alElegir) {
        this.alElegir = alElegir;
        setOrientation(Orientation.VERTICAL);
        getItems().setAll(armarGantt(), armarPestanas());
        setDividerPositions(0.58);
        getStyleClass().add("vista-procesos");
        App.temaClaro().addListener(new javafx.beans.value.WeakChangeListener<>(oyenteTema));
    }

    // ---------------------------------------------------------------- armado

    private VBox armarGantt() {
        ventana.setValue(Ventana.TODA);
        filtro.setValue(Filtro.TODOS);
        ventana.valueProperty().addListener((o, a, v) -> refrescar());
        filtro.valueProperty().addListener((o, a, v) -> refrescar());
        Label titulo = new Label("Diagrama de Gantt");
        titulo.getStyleClass().add("titulo-panel");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        resumenGantt.getStyleClass().add("subtitulo");
        for (Region r : List.of(titulo, ventana, filtro)) r.setMinWidth(Region.USE_PREF_SIZE);   // la leyenda de colores ya está en el pie
        HBox barra = new HBox(8, titulo, ventana, filtro, resumenGantt, hueco);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(4, 8, 4, 8));

        desplazable.setFitToWidth(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.getStyleClass().add("detalle-scroll");
        desplazable.viewportBoundsProperty().addListener((o, a, v) -> dibujar());
        lamina.setOnMouseClicked(e -> {
            if (gantt == null) return;
            int fila = (int) ((e.getY() - CABECERA) / ALTO_FILA);
            if (e.getY() >= CABECERA && fila >= 0 && fila < gantt.filas().size()) alElegir.accept(gantt.filas().get(fila).pid());
        });
        VBox caja = new VBox(barra, desplazable);
        VBox.setVgrow(desplazable, Priority.ALWAYS);
        caja.getStyleClass().add("panel-gantt");
        return caja;
    }

    private TabPane armarPestanas() {
        filtroMomento.getItems().add("Todos los momentos");
        for (Momento m : Momento.values()) filtroMomento.getItems().add(m.name().toLowerCase() + " · " + m.descripcion);
        filtroMomento.setValue("Todos los momentos");
        filtroMomento.setPrefWidth(320);
        filtroMomento.valueProperty().addListener((o, a, v) -> refrescarBitacora());
        soloElegido.selectedProperty().addListener((o, a, v) -> refrescarBitacora());

        tablaBitacora.getColumns().add(columna("t", s -> Integer.toString(s.t()), 60));
        tablaBitacora.getColumns().add(columna("Momento", s -> s.momento() == null ? "—" : s.momento().name().toLowerCase(), 80));
        tablaBitacora.getColumns().add(columna("Proceso", s -> "P" + s.pid(), 80));
        TableColumn<Instantanea.Suceso, String> detalle = columna("Detalle", Instantanea.Suceso::detalle, 520);
        tablaBitacora.getColumns().add(detalle);
        tablaBitacora.setPlaceholder(new Label("Sin sucesos hasta este instante"));
        tablaBitacora.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> { if (s != null) alElegir.accept(s.pid()); });
        conteo.getStyleClass().add("subtitulo");
        HBox filtros = new HBox(10, filtroMomento, soloElegido, conteo);
        filtros.setAlignment(Pos.CENTER_LEFT);
        filtros.setPadding(new Insets(6, 8, 6, 8));
        VBox bitacora = new VBox(filtros, tablaBitacora);
        VBox.setVgrow(tablaBitacora, Priority.ALWAYS);

        tablaMetricas.getColumns().add(columnaM("PID", x -> "P" + x.pid() + (x.usuario() ? " · usuario" : "")));
        tablaMetricas.getColumns().add(columnaM("Llegada", x -> Integer.toString(x.llegada())));
        tablaMetricas.getColumns().add(columnaM("Ráfaga", x -> Integer.toString(x.burst())));
        tablaMetricas.getColumns().add(columnaM("Prioridad", x -> Integer.toString(x.prioridad())));
        tablaMetricas.getColumns().add(columnaM("Estado", x -> MapaEstados.titulo(x.estado())));
        tablaMetricas.getColumns().add(columnaM("Fin", x -> conocido(x.fin())));
        tablaMetricas.getColumns().add(columnaM("Retorno", x -> conocido(x.retorno())));
        tablaMetricas.getColumns().add(columnaM("Respuesta", x -> conocido(x.respuesta())));
        tablaMetricas.getColumns().add(columnaM("Espera", x -> Integer.toString(x.espera())));
        tablaMetricas.getColumns().add(columnaM("CPU", x -> Integer.toString(x.cpu())));
        tablaMetricas.getColumns().add(columnaM("Bloqueado", x -> Integer.toString(x.bloqueado())));
        tablaMetricas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tablaMetricas.getSelectionModel().selectedItemProperty().addListener((o, a, x) -> { if (x != null) alElegir.accept(x.pid()); });
        promedios.getStyleClass().add("subtitulo");
        promedios.setPadding(new Insets(6, 8, 6, 8));
        promedios.setWrapText(true);
        VBox metricas = new VBox(promedios, tablaMetricas);
        VBox.setVgrow(tablaMetricas, Priority.ALWAYS);

        TabPane p = new TabPane(new Tab("Bitácora (momentos a–e)", bitacora), new Tab("Métricas", metricas));
        p.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        p.getStyleClass().add("pestanas-panel");
        return p;
    }

    private static String conocido(int v) { return v < 0 ? "—" : Integer.toString(v); }

    private static TableColumn<Instantanea.Suceso, String> columna(String t, Function<Instantanea.Suceso, String> f, double ancho) {
        TableColumn<Instantanea.Suceso, String> c = new TableColumn<>(t);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(f.apply(d.getValue())));
        c.setPrefWidth(ancho);
        c.setSortable(false);
        return c;
    }

    private static TableColumn<FilaMetricas, String> columnaM(String t, Function<FilaMetricas, String> f) {
        TableColumn<FilaMetricas, String> c = new TableColumn<>(t);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(f.apply(d.getValue())));
        c.setSortable(false);
        return c;
    }

    // ---------------------------------------------------------------- datos

    /** Muestra la foto mirada (llamar solo cuando la vista está a la vista). */
    void mostrar(RegistroProcesos registro, Instantanea foto) {
        this.registro = registro;
        this.foto = foto;
        refrescar();
    }

    void seleccionar(int pid) {
        if (pid == seleccionado) return;
        seleccionado = pid;
        dibujar();
        if (soloElegido.isSelected()) refrescarBitacora();
    }

    private void refrescar() {
        if (registro == null || foto == null) return;
        gantt = DiagramaGantt.de(registro, foto, ventana.getValue().ticks, filtro.getValue());
        resumenGantt.setText(gantt.filas().size() + " procesos" + (gantt.ocultos() > 0 ? " (" + gantt.ocultos() + " ocultos)" : "")
                + " · t " + gantt.desde() + "–" + foto.t());
        dibujar();
        refrescarBitacora();
        RegistroProcesos.Metricas m = registro.metricas(foto);
        tablaMetricas.getItems().setAll(m.filas());
        promedios.setText(m.terminados() == 0
                ? "Aún no termina ningún proceso · uso de CPU " + String.format("%.1f %%", m.usoCpu())
                : String.format("Terminados: %d de %d · retorno medio %.2f · espera media %.2f · respuesta media %.2f · uso de CPU %.1f %%",
                        m.terminados(), m.filas().size(), m.retornoMedio(), m.esperaMedia(), m.respuestaMedia(), m.usoCpu()));
    }

    private void refrescarBitacora() {
        if (registro == null || foto == null) return;
        int indice = filtroMomento.getSelectionModel().getSelectedIndex();
        Set<Momento> momentos = indice <= 0 ? Set.of() : EnumSet.of(Momento.values()[indice - 1]);
        int pid = soloElegido.isSelected() ? seleccionado : Instantanea.NINGUNO;
        if (soloElegido.isSelected() && pid == Instantanea.NINGUNO) {
            tablaBitacora.getItems().clear();
            conteo.setText("Elige un proceso");
            return;
        }
        List<Instantanea.Suceso> lista = registro.bitacora(foto.t(), pid, momentos, MAX_BITACORA);
        tablaBitacora.getItems().setAll(lista);
        Map<Momento, Integer> c = registro.conteoMomentos(foto.t());
        StringBuilder sb = new StringBuilder();
        for (Momento m : Momento.values()) sb.append(m.name().toLowerCase()).append(' ').append(c.get(m)).append("   ");
        conteo.setText(sb.toString().strip() + (lista.size() == MAX_BITACORA ? "   (últimos " + MAX_BITACORA + ")" : ""));
    }

    // ---------------------------------------------------------------- dibujo

    private void dibujar() {
        if (gantt == null) return;
        Paleta p = Paleta.de(App.temaClaro().get());
        double ancho = Math.max(200, desplazable.getViewportBounds().getWidth());
        double alto = Math.max(desplazable.getViewportBounds().getHeight(), CABECERA + gantt.filas().size() * ALTO_FILA + 6);
        canvas.setWidth(ancho);
        canvas.setHeight(alto);
        lamina.setPrefSize(ancho, alto);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(p.fondoArriba());
        g.fillRect(0, 0, ancho, alto);
        double util = ancho - MARGEN_IZQ - MARGEN_DER;
        double escala = util / Math.max(1, gantt.ancho());                 // píxeles por tick

        g.setFont(Font.font("System", FontWeight.NORMAL, 10));             // eje de tiempo
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.CENTER);
        int paso = pasoEje(gantt.ancho(), util);
        for (int t = (gantt.desde() + paso - 1) / paso * paso; t <= gantt.hasta(); t += paso) {
            double x = MARGEN_IZQ + (t - gantt.desde()) * escala;
            g.setStroke(p.bordeCarril());
            g.setLineWidth(1);
            g.strokeLine(x, CABECERA - 4, x, alto);
            g.setFill(p.tenue());
            g.fillText(Integer.toString(t), x, CABECERA / 2);
        }

        for (int i = 0; i < gantt.filas().size(); i++) {
            DiagramaGantt.Fila f = gantt.filas().get(i);
            double y = CABECERA + i * ALTO_FILA;
            if (f.pid() == seleccionado) {
                g.setFill(p.fondoOro());
                g.fillRect(0, y, ancho, ALTO_FILA);
            }
            g.setTextAlign(TextAlignment.LEFT);
            g.setFill(f.pid() == seleccionado ? p.oro() : p.texto());
            g.setFont(Font.font("System", FontWeight.BOLD, 11));
            g.fillText("P" + f.pid() + (f.usuario() ? " ·u" : ""), 8, y + ALTO_FILA / 2);
            for (DiagramaGantt.Barra b : f.barras()) {
                double x0 = MARGEN_IZQ + (b.desde() - gantt.desde()) * escala;
                double w = Math.max(1, (b.hasta() - b.desde()) * escala);
                g.setFill(Color.web(ColorEstado.de(b.estado())));
                g.fillRect(x0, y + 3, w, ALTO_FILA - 6);
            }
        }
        double xt = MARGEN_IZQ + (foto.t() + 1 - gantt.desde()) * escala;   // instante mirado
        g.setStroke(p.oro());
        g.setLineWidth(2);
        g.strokeLine(xt, CABECERA - 6, xt, alto);
    }

    /** Separación entre marcas del eje: la menor de 1, 2, 5, 10, 20, 50… que deja al menos 40 px. */
    static int pasoEje(int ticks, double pixeles) {
        int[] base = {1, 2, 5};
        for (int potencia = 1; ; potencia *= 10)
            for (int b : base) if (b * potencia * pixeles / Math.max(1, ticks) >= 40) return b * potencia;
    }
}
