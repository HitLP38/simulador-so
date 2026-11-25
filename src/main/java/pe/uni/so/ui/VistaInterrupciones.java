package pe.uni.so.ui;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
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
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import pe.uni.so.interrupciones.TipoInterrupcion;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.sesion.RegistroES;
import pe.uni.so.sesion.TablaVectores;

/**
 * [I1][I2][I3][I4][P2] Vista «Interrupciones»: a la izquierda la tabla de vectores (IVT) con lo
 * atendido hasta el instante mirado; a la derecha los registros del PIC 8259 (IRR, IMR, ISR)
 * dibujados bit a bit por línea IRQ y la bitácora de interrupciones, filtrable por tipo.
 */
final class VistaInterrupciones extends SplitPane {

    private static final int LINEAS = 16, MAX_BITACORA = 500;
    private static final double LADO = 26, SEP = 3, IZQ = 52, ARRIBA = 18;
    private static final String[] REGISTROS = {"IRR", "IMR", "ISR"};

    /** Fila de la IVT con lo atendido hasta la foto. */
    private record FilaIvt(TablaVectores.Fila v, int atendidas) {}

    private final TableView<FilaIvt> ivt = new TableView<>();
    private final Canvas canvas = new Canvas();
    private final Pane lamina = new Pane(canvas);
    private final Label leyendaPic = new Label();
    private final ComboBox<String> filtro = new ComboBox<>();
    private final TableView<RegistroES.EventoInterrupcion> bitacora = new TableView<>();
    private RegistroES registro;
    private Instantanea foto;
    private final ChangeListener<Boolean> oyenteTema = (o, a, v) -> dibujar();

    VistaInterrupciones() {
        setOrientation(Orientation.HORIZONTAL);
        getItems().setAll(armarIvt(), armarDerecha());
        setDividerPositions(0.56);
        App.temaClaro().addListener(new javafx.beans.value.WeakChangeListener<>(oyenteTema));
    }

    private VBox armarIvt() {
        Label titulo = new Label("Tabla de vectores de interrupción (IVT)");
        titulo.getStyleClass().add("titulo-panel");
        ivt.getColumns().add(columna("Vector", f -> f.v().vectorHex()));
        ivt.getColumns().add(columna("Interrupción", f -> nombre(f.v().tipo())));
        ivt.getColumns().add(columna("Clase", f -> switch (f.v().clase()) {
            case HARDWARE -> "hardware"; case SOFTWARE -> "software"; case EXCEPCION -> "excepción"; }));
        ivt.getColumns().add(columna("IRQ", f -> f.v().sincrona() ? "—" : Integer.toString(f.v().irq())));
        ivt.getColumns().add(columna("Prioridad", f -> f.v().sincrona() ? "inmediata" : Integer.toString(f.v().prioridad())));
        ivt.getColumns().add(columna("Atendidas", f -> f.v().generada() ? Integer.toString(f.atendidas()) : "—"));
        ivt.getColumns().add(columna("Rutina de servicio (ISR)", f -> f.v().rutina()));
        double[] anchos = {58, 150, 80, 40, 70, 75, 300};                 // la rutina se queda con el resto
        for (int i = 0; i < anchos.length; i++) {
            ivt.getColumns().get(i).setPrefWidth(anchos[i]);
            if (i < anchos.length - 1) ivt.getColumns().get(i).setMinWidth(anchos[i] * 0.8);
        }
        ivt.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        ivt.setFixedCellSize(26);
        ivt.setPrefHeight(26 * 12 + 6);
        ivt.setMinHeight(26 * 4);
        ivt.getItems().setAll(TablaVectores.filas().stream().map(v -> new FilaIvt(v, 0)).toList());
        Label detalle = new Label("Elige una fila para ver su rutina completa.");
        detalle.setWrapText(true);
        detalle.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        detalle.getStyleClass().add("etiqueta-campo");
        ivt.getSelectionModel().selectedItemProperty().addListener((o, a, f) -> {
            if (f != null) detalle.setText(f.v().vectorHex() + " · " + nombre(f.v().tipo()) + ": " + f.v().rutina());
        });
        Label nota = new Label("«—» en Atendidas: la simulación no genera esa interrupción. Prioridad del PIC: menor IRQ = más prioridad. Las excepciones y la int 0x80 son síncronas: "
                + "las provoca la instrucción y la CPU las atiende sin pasar por el PIC. En esta simulación los fines de E/S "
                + "pasan por el PIC, el TIMER se cuenta en cada fin de quantum (el kernel lo resuelve sin IRQ, por eso no se "
                + "ve en el IRR), la int 0x80 en cada pedido de E/S y las excepciones 0x00, 0x06 y 0x0D cuando el módulo de "
                + "errores aborta un proceso (P9).");
        nota.setWrapText(true);
        nota.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        nota.getStyleClass().add("subtitulo");
        VBox caja = new VBox(4, titulo, ivt, detalle, nota);
        caja.setPadding(new Insets(4, 8, 6, 8));
        return caja;
    }

    private VBox armarDerecha() {
        Label titulo = new Label("PIC 8259 — registros por línea IRQ");
        titulo.getStyleClass().add("titulo-panel");
        leyendaPic.getStyleClass().add("subtitulo");
        leyendaPic.setWrapText(true);
        leyendaPic.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        canvas.setManaged(false);
        lamina.setMinHeight(ARRIBA + REGISTROS.length * (LADO + SEP) + 8);
        lamina.setPrefHeight(lamina.getMinHeight());
        lamina.widthProperty().addListener((o, a, v) -> dibujar());

        Label tituloBit = new Label("Bitácora de interrupciones");
        tituloBit.getStyleClass().add("titulo-panel");
        filtro.getItems().add("Todas");
        for (TablaVectores.Fila v : TablaVectores.filas()) if (v.generada()) filtro.getItems().add(nombre(v.tipo()));
        filtro.setValue("Todas");
        filtro.valueProperty().addListener((o, a, v) -> refrescarBitacora());
        HBox barra = new HBox(8, tituloBit, filtro);
        barra.setAlignment(Pos.CENTER_LEFT);
        TableColumn<RegistroES.EventoInterrupcion, String> t = columnaE("t", e -> Integer.toString(e.t()));
        t.setMinWidth(50);
        t.setMaxWidth(70);
        bitacora.getColumns().add(t);
        bitacora.getColumns().add(columnaE("Interrupción", e -> nombre(e.tipo()) + " (" + TablaVectores.de(e.tipo()).vectorHex() + ")"));
        bitacora.getColumns().add(columnaE("Qué pasó", e -> e.accion().etiqueta + (e.veces() > 1 ? " ×" + e.veces() : "")));
        bitacora.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        bitacora.setPlaceholder(new Label("Sin interrupciones hasta este instante"));

        VBox caja = new VBox(4, titulo, lamina, leyendaPic, barra, bitacora);
        VBox.setVgrow(bitacora, Priority.ALWAYS);
        caja.setPadding(new Insets(4, 8, 6, 8));
        return caja;
    }

    private static <T> TableColumn<T, String> columna(String titulo, Function<T, String> f) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(f.apply(d.getValue())));
        c.setSortable(false);
        return c;
    }

    private static TableColumn<RegistroES.EventoInterrupcion, String> columnaE(String t, Function<RegistroES.EventoInterrupcion, String> f) {
        return columna(t, f);
    }

    static String nombre(TipoInterrupcion t) {
        return switch (t) {
            case DIVISION_CERO -> "División entre cero";
            case INSTRUCCION_INVALIDA -> "Instrucción inválida";
            case ACCESO_ILEGAL -> "Acceso ilegal (GPF)";
            case FALLO_PAGINA -> "Fallo de página";
            case TIMER -> "Timer";
            case TECLADO -> "Teclado";
            case DISCO -> "Disco";
            case IMPRESORA -> "Impresora";
            case PANTALLA -> "Pantalla";
            case RED -> "Red";
            case LLAMADA_SISTEMA -> "Llamada al sistema";
        };
    }

    /** Muestra la foto mirada (llamar solo cuando la vista está a la vista). */
    void mostrar(RegistroES registro, Instantanea foto) {
        this.registro = registro;
        this.foto = foto;
        ivt.getItems().setAll(TablaVectores.filas().stream().map(v -> new FilaIvt(v, registro.atendidas(v.tipo(), foto.t()))).toList());
        dibujar();
        refrescarBitacora();
    }

    private void refrescarBitacora() {
        if (registro == null || foto == null) return;
        int i = filtro.getSelectionModel().getSelectedIndex();
        Set<TipoInterrupcion> tipos = Set.of();
        if (i > 0) {
            List<TablaVectores.Fila> generadas = TablaVectores.filas().stream().filter(TablaVectores.Fila::generada).toList();
            tipos = EnumSet.of(generadas.get(i - 1).tipo());
        }
        bitacora.getItems().setAll(registro.bitacora(foto.t(), tipos, MAX_BITACORA));
    }

    private void dibujar() {
        double ancho = lamina.getWidth();
        double alto = lamina.getMinHeight();
        if (ancho <= 0) return;
        canvas.setWidth(ancho);
        canvas.setHeight(alto);
        Paleta p = Paleta.de(App.temaClaro().get());
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(p.fondoArriba());
        g.fillRect(0, 0, ancho, alto);
        Instantanea.Pic pic = foto == null ? null : foto.pic();
        double lado = Math.min(LADO, (ancho - IZQ - 4) / LINEAS - SEP);
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.CENTER);
        g.setFont(Font.font("System", FontWeight.NORMAL, 9));
        for (int irq = 0; irq < LINEAS; irq++) {
            g.setFill(p.tenue());
            g.fillText(Integer.toString(irq), IZQ + irq * (lado + SEP) + lado / 2, ARRIBA / 2);
        }
        Color[] encendido = {Color.web("#F28C28"), Color.web("#8A8F98"), Color.web("#E8457C")};
        StringBuilder resumen = new StringBuilder();
        for (int r = 0; r < REGISTROS.length; r++) {
            double y = ARRIBA + r * (lado + SEP);
            g.setTextAlign(TextAlignment.LEFT);
            g.setFill(p.texto());
            g.setFont(Font.font("System", FontWeight.BOLD, 11));
            g.fillText(REGISTROS[r], 6, y + lado / 2);
            List<TipoInterrupcion> lista = pic == null ? List.of() : switch (r) {
                case 0 -> pic.pendientes(); case 1 -> pic.enmascaradas(); default -> pic.enServicio(); };
            resumen.append(REGISTROS[r]).append(": ").append(lista.isEmpty() ? "—" : String.join(", ", lista.stream().map(VistaInterrupciones::nombre).toList()));
            if (r < REGISTROS.length - 1) resumen.append("   ");
            for (int irq = 0; irq < LINEAS; irq++) {
                double x = IZQ + irq * (lado + SEP);
                TipoInterrupcion linea = deIrq(irq);
                boolean bit = linea != null && lista.contains(linea);
                g.setFill(bit ? encendido[r] : linea == null ? p.fondoArriba().deriveColor(0, 1, 0.9, 1) : p.bordeCarril());
                g.fillRoundRect(x, y, lado, lado, 4, 4);
                g.setTextAlign(TextAlignment.CENTER);
                g.setFont(Font.font("System", FontWeight.BOLD, 10));
                g.setFill(bit ? Color.web("#0b1220") : p.tenue());
                g.fillText(linea == null ? "·" : bit ? "1" : "0", x + lado / 2, y + lado / 2);
            }
        }
        leyendaPic.setText(resumen + "   (líneas en uso: 0 timer, 1 teclado, 7 impresora, 10 pantalla, 11 red, 14 disco)");
    }

    private static TipoInterrupcion deIrq(int irq) {
        for (TablaVectores.Fila v : TablaVectores.filas()) if (v.irq() == irq) return v.tipo();
        return null;
    }
}
