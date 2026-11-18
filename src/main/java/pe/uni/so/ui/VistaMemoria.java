package pe.uni.so.ui;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntConsumer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.sesion.MapaMarcos;
import pe.uni.so.sesion.TraductorDirecciones;

/**
 * [M1][M3][M4][M7][P16] Vista «Memoria»: a la izquierda la rejilla de marcos de la RAM coloreada
 * por proceso, con los indicadores de la foto (libres, huecos, fragmentación, desperdicio,
 * fallos de página); a la derecha, el proceso elegido: segmentos, tabla de páginas, cuota y
 * próxima víctima (bajo demanda) y el traductor de direcciones. Solo lee la foto mirada; un clic
 * en un marco elige su proceso.
 */
final class VistaMemoria extends SplitPane {

    private static final double LADO = 11, SEPARACION = 1, MARGEN = 8;
    private static final int MAX_CELDAS = 4096;

    private final IntConsumer alElegir;
    private final Canvas canvas = new Canvas();
    private final Pane lamina = new Pane(canvas);
    private final ScrollPane desplazable = new ScrollPane(lamina);
    private final Label indicadores = new Label();
    private final Label escala = new Label();
    private final Label bajoCursor = new Label(" ");

    private final Label tituloProceso = new Label("Elige un proceso");
    private final Label resumenProceso = new Label();
    private final TableView<GestorMemoria.LimitesSegmento> tablaSegmentos = new TableView<>();
    private final TableView<GestorMemoria.TramoPaginas> tablaPaginas = new TableView<>();
    private final TextField direccion = new TextField();
    private final Label resultado = new Label();

    private Instantanea foto;
    private MapaMarcos mapa;
    private int seleccionado = Instantanea.NINGUNO;
    private final ChangeListener<Boolean> oyenteTema = (o, a, v) -> dibujar();

    VistaMemoria(IntConsumer alElegir) {
        this.alElegir = alElegir;
        setOrientation(Orientation.HORIZONTAL);
        getItems().setAll(armarRejilla(), armarProceso());
        setDividerPositions(0.55);
        App.temaClaro().addListener(new javafx.beans.value.WeakChangeListener<>(oyenteTema));
    }

    // ---------------------------------------------------------------- armado

    private VBox armarRejilla() {
        Label titulo = new Label("Marcos de la RAM");
        titulo.getStyleClass().add("titulo-panel");
        titulo.setMinWidth(Region.USE_PREF_SIZE);
        for (Label l : List.of(escala, indicadores, bajoCursor)) {
            l.getStyleClass().add("subtitulo");
            l.setWrapText(true);
            l.setMinHeight(Region.USE_PREF_SIZE);                       // el texto baja de línea en vez de cortarse
        }
        VBox cabecera = new VBox(2, titulo, escala, indicadores);
        cabecera.setPadding(new Insets(4, 8, 4, 8));

        desplazable.setFitToWidth(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.getStyleClass().add("detalle-scroll");
        desplazable.viewportBoundsProperty().addListener((o, a, v) -> dibujar());
        lamina.setOnMouseClicked(e -> {
            int c = celdaEn(e.getX(), e.getY());
            if (c >= 0 && mapa.dueno(c) != MapaMarcos.LIBRE) alElegir.accept(mapa.dueno(c));
        });
        lamina.setOnMouseMoved(e -> {
            int c = celdaEn(e.getX(), e.getY());
            if (c < 0) { bajoCursor.setText(" "); return; }
            int a = mapa.primerMarco(c), b = Math.min(mapa.marcos(), a + mapa.porCelda()) - 1;
            String marcos = a == b ? "Marco " + a : "Marcos " + a + "–" + b;
            bajoCursor.setText(marcos + " · " + (mapa.dueno(c) == MapaMarcos.LIBRE ? "libre" : "P" + mapa.dueno(c))
                    + (mapa.mixta(c) ? " (celda mixta)" : ""));
        });
        HBox pie = new HBox(bajoCursor);
        pie.setPadding(new Insets(2, 8, 4, 8));
        VBox caja = new VBox(cabecera, desplazable, pie);
        VBox.setVgrow(desplazable, Priority.ALWAYS);
        return caja;
    }

    private VBox armarProceso() {
        tituloProceso.getStyleClass().add("titulo-panel");
        resumenProceso.getStyleClass().add("subtitulo");
        resumenProceso.setWrapText(true);
        resumenProceso.setMinHeight(Region.USE_PREF_SIZE);

        tablaSegmentos.getColumns().add(columna("Segmento", s -> nombre(s.segmento())));
        tablaSegmentos.getColumns().add(columna("Base", s -> hex(s.base())));
        tablaSegmentos.getColumns().add(columna("Límite", s -> hex(s.limite())));
        tablaSegmentos.getColumns().add(columna("Páginas", s -> Long.toString((s.limite() - s.base()) / bloque())));
        tablaSegmentos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tablaSegmentos.setFixedCellSize(24);
        tablaSegmentos.setPrefHeight(24 * 5 + 4);
        tablaSegmentos.setMinHeight(Region.USE_PREF_SIZE);
        tablaSegmentos.setPlaceholder(new Label("—"));

        tablaPaginas.getColumns().add(columna("Páginas", t -> t.paginas() == 1 ? Long.toString(t.paginaInicial())
                : t.paginaInicial() + "–" + t.paginaFinal()));
        tablaPaginas.getColumns().add(columna("Marcos", t -> !t.valida() ? "—" : t.paginas() == 1 ? Integer.toString(t.marcoInicial())
                : t.marcoInicial() + "–" + (t.marcoInicial() + t.paginas() - 1)));
        tablaPaginas.getColumns().add(columna("v/i", t -> t.valida() ? "v" : "i"));
        tablaPaginas.getColumns().add(columna("R", t -> t.referenciada() ? "1" : "0"));
        tablaPaginas.getColumns().add(columna("M", t -> t.modificada() ? "1" : "0"));
        tablaPaginas.getColumns().add(columna("Segmento", this::segmentoDe));
        tablaPaginas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tablaPaginas.setPlaceholder(new Label("El proceso no está en memoria en este instante"));

        Label etiquetaTabla = new Label("Tabla de páginas (tramos de páginas consecutivas)");
        etiquetaTabla.getStyleClass().add("etiqueta-campo");
        etiquetaTabla.setWrapText(true);
        Label etiquetaTraductor = new Label("Traductor de direcciones (decimal o 0x…)");
        etiquetaTraductor.setWrapText(true);
        etiquetaTraductor.getStyleClass().add("etiqueta-campo");
        direccion.setPromptText("p. ej. 0x10A0");
        direccion.setOnAction(e -> traducir());
        Button traducir = new Button("Traducir");
        traducir.setOnAction(e -> traducir());
        HBox.setHgrow(direccion, Priority.ALWAYS);
        HBox filaTraductor = new HBox(6, direccion, traducir);
        resultado.setWrapText(true);
        resultado.setMinHeight(Region.USE_PREF_SIZE);
        resultado.getStyleClass().add("subtitulo");

        VBox caja = new VBox(6, tituloProceso, resumenProceso, tablaSegmentos, etiquetaTabla, tablaPaginas,
                etiquetaTraductor, filaTraductor, resultado);
        VBox.setVgrow(tablaPaginas, Priority.ALWAYS);
        caja.setPadding(new Insets(6, 8, 8, 8));
        caja.setMinWidth(300);
        return caja;
    }

    private static <T> TableColumn<T, String> columna(String titulo, Function<T, String> f) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(f.apply(d.getValue())));
        c.setSortable(false);
        return c;
    }

    private static String nombre(EspacioProceso.Segmento s) {
        return switch (s) { case TEXTO -> "Texto"; case DATOS -> "Datos"; case HEAP -> "Heap"; case PILA -> "Pila"; };
    }

    private static String hex(long v) { return "0x" + Long.toHexString(v).toUpperCase(Locale.ROOT); }

    private long bloque() { return foto == null || foto.memoria() == null ? 1 : foto.memoria().bloque(); }

    private String segmentoDe(GestorMemoria.TramoPaginas t) {
        Instantanea.MemoriaProceso mp = procesoElegido();
        if (mp == null) return "";
        long dir = t.paginaInicial() * bloque();
        for (GestorMemoria.LimitesSegmento s : mp.segmentos()) if (dir >= s.base() && dir < s.limite()) return nombre(s.segmento());
        return "";
    }

    // ---------------------------------------------------------------- datos

    /** Muestra la foto mirada (llamar solo cuando la vista está a la vista). */
    void mostrar(Instantanea foto) {
        this.foto = foto;
        Instantanea.Memoria m = foto.memoria();
        if (m == null) {
            mapa = null;
            indicadores.setText("Esta máquina no simula memoria.");
            dibujar();
            mostrarProceso();
            return;
        }
        mapa = MapaMarcos.de(m, MAX_CELDAS);
        escala.setText(m.marcos() + " marcos de " + tamano(m.bloque())
                + (mapa.porCelda() > 1 ? " · cada celda = " + mapa.porCelda() + " marcos" : ""));
        GestorMemoria.Muestra x = m.muestra();
        indicadores.setText(String.format(Locale.ROOT,
                "Libres %d · ocupados %d · huecos %d · mayor hueco %d · fragmentación externa %d marcos · desperdicio interno %s · fallos de página %d · %s%s",
                x.libres(), x.ocupados(), x.huecos(), x.mayorHueco(), x.fragmentacionExterna(), tamano(x.desperdicioInterno()),
                m.fallosPagina(), m.estrategia(), m.bajoDemanda() ? " · bajo demanda (" + m.reemplazo() + ")" : " · carga total"));
        dibujar();
        mostrarProceso();
    }

    void seleccionar(int pid) {
        if (pid == seleccionado) return;
        seleccionado = pid;
        dibujar();
        mostrarProceso();
    }

    private Instantanea.MemoriaProceso procesoElegido() {
        return foto == null || foto.memoria() == null ? null : foto.memoria().procesos().get(seleccionado);
    }

    private void mostrarProceso() {
        Instantanea.MemoriaProceso mp = procesoElegido();
        if (seleccionado == Instantanea.NINGUNO) {
            tituloProceso.setText("Elige un proceso");
            resumenProceso.setText("Haz clic en un marco de la rejilla, en el explorador o en la arena.");
        } else tituloProceso.setText("P" + seleccionado);
        if (mp == null) {
            if (seleccionado != Instantanea.NINGUNO) resumenProceso.setText("P" + seleccionado + " no está en memoria en este instante.");
            tablaSegmentos.getItems().clear();
            tablaPaginas.getItems().clear();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(mp.paginasValidas()).append(" páginas en memoria de ").append(mp.paginasRegistradas()).append(" registradas");
            if (mp.cuota() > 0) {
                sb.append(" · cuota ").append(mp.cuota()).append(" marcos · marcos locales: ");
                List<Long> locales = mp.marcosLocales();
                for (int i = 0; i < locales.size(); i++) sb.append(i == 0 ? "" : ", ").append(locales.get(i) == null ? "libre" : "pág " + locales.get(i));
                sb.append(" · próxima víctima: ").append(mp.proximaVictima() < 0 ? "ninguna (hay marcos libres)" : "pág " + mp.proximaVictima());
            }
            resumenProceso.setText(sb.toString());
            tablaSegmentos.getItems().setAll(mp.segmentos());
            tablaPaginas.getItems().setAll(mp.tabla());
        }
        resultado.setText("");
    }

    private void traducir() {
        if (foto == null || foto.memoria() == null) { resultado.setText("Esta máquina no simula memoria."); return; }
        if (seleccionado == Instantanea.NINGUNO) { resultado.setText("Primero elige un proceso."); return; }
        long d;
        try {
            String t = direccion.getText().strip().replace("_", "");
            d = t.startsWith("0x") || t.startsWith("0X") ? Long.parseUnsignedLong(t.substring(2), 16) : Long.parseLong(t);
            if (d < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            resultado.setText("Escribe una dirección entera ≥ 0, en decimal o en hexadecimal (0x…).");
            return;
        }
        TraductorDirecciones.Resultado r = TraductorDirecciones.traducir(foto.memoria(), seleccionado, d);
        StringBuilder sb = new StringBuilder(r.tipo().etiqueta).append(": ");
        if (r.segmento() != null) sb.append("segmento ").append(nombre(r.segmento())).append(" · ");
        if (r.pagina() >= 0) sb.append("página ").append(r.pagina()).append(" · desplazamiento ").append(r.desplazamiento()).append(" · ");
        if (r.tipo() == TraductorDirecciones.Tipo.TRADUCIDA) sb.append("física ").append(hex(r.fisica())).append(" (").append(r.fisica()).append(") · ");
        sb.append(r.detalle());
        resultado.setText(sb.toString());
        if (r.marco() >= 0 && mapa != null) dibujar(mapa.celdaDe(r.marco()));
    }

    private static String tamano(long bytes) {
        if (bytes >= 1L << 30 && bytes % (1L << 30) == 0) return (bytes >> 30) + " GiB";
        if (bytes >= 1L << 20) return String.format(Locale.ROOT, "%.1f MiB", bytes / (double) (1L << 20));
        if (bytes >= 1L << 10) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        return bytes + " B";
    }

    // ---------------------------------------------------------------- dibujo

    private int columnas() { return MapaMarcos.columnas(desplazable.getViewportBounds().getWidth() - 2 * MARGEN, LADO + SEPARACION); }

    private int celdaEn(double x, double y) {
        if (mapa == null) return -1;
        int col = (int) ((x - MARGEN) / (LADO + SEPARACION)), fila = (int) ((y - MARGEN) / (LADO + SEPARACION));
        int columnas = columnas();
        if (x < MARGEN || y < MARGEN || col >= columnas) return -1;
        int c = fila * columnas + col;
        return c < mapa.celdas() ? c : -1;
    }

    private void dibujar() { dibujar(-1); }

    /** Dibuja la rejilla; {@code resaltada} es la celda de la última traducción (−1: ninguna). */
    private void dibujar(int resaltada) {
        Paleta p = Paleta.de(App.temaClaro().get());
        double ancho = Math.max(120, desplazable.getViewportBounds().getWidth());
        int columnas = columnas();
        int filas = mapa == null ? 0 : (mapa.celdas() + columnas - 1) / columnas;
        double alto = Math.max(desplazable.getViewportBounds().getHeight(), 2 * MARGEN + filas * (LADO + SEPARACION));
        canvas.setWidth(ancho);
        canvas.setHeight(alto);
        lamina.setPrefSize(ancho, alto);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(p.fondoArriba());
        g.fillRect(0, 0, ancho, alto);
        if (mapa == null) return;
        Color libre = p.bordeCarril();
        for (int c = 0; c < mapa.celdas(); c++) {
            double x = MARGEN + (c % columnas) * (LADO + SEPARACION), y = MARGEN + (c / columnas) * (LADO + SEPARACION);
            int d = mapa.dueno(c);
            boolean elegido = d != MapaMarcos.LIBRE && d == seleccionado;
            Color color = d == MapaMarcos.LIBRE ? libre : colorDe(d);
            if (seleccionado != Instantanea.NINGUNO && !elegido && d != MapaMarcos.LIBRE) color = color.deriveColor(0, 0.5, 1, 0.45);
            g.setFill(color);
            g.fillRect(x, y, LADO, LADO);
            if (elegido || c == resaltada) {
                g.setStroke(p.oro());
                g.setLineWidth(c == resaltada ? 2.5 : 1.5);
                g.strokeRect(x + 0.75, y + 0.75, LADO - 1.5, LADO - 1.5);
            }
        }
    }

    /** Color estable por proceso: matiz repartido con el ángulo áureo. */
    static Color colorDe(int pid) { return Color.hsb((pid * 137.508) % 360, 0.55, 0.82); }
}
