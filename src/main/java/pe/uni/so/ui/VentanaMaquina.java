package pe.uni.so.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.AnimationTimer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import pe.uni.so.configuracion.FichaMaquina;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.excepciones.SimuladorException;
import pe.uni.so.motor.ColorEstado;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.procesos.Estado;
import pe.uni.so.sesion.Camara;
import pe.uni.so.sesion.MapaEstados;
import pe.uni.so.sesion.Propiedades;
import pe.uni.so.sesion.Reproductor;
import pe.uni.so.sesion.SesionSimulacion;
import pe.uni.so.sesion.Vista;

/**
 * [G5] Espacio de trabajo de una máquina, al estilo de Logisim. Una sola línea de cabecera
 * (menús Simulación y Ver, vista, controles, velocidad, zoom, ⓘ y tema); a la izquierda el
 * explorador; al centro la arena del kernel; a la derecha las pestañas Propiedades y PCB; debajo
 * de la arena el panel «Eventos y E/S» (cola de eventos E/S, señales, eventos varios); al pie la
 * línea de tiempo, la leyenda y la barra de estado. Cada panel se oculta desde su propia barra y
 * vuelve desde el menú Ver; la arena ocupa el espacio libre. Toda la lógica vive en
 * {@code pe.uni.so.sesion}; esta clase solo arma controles.
 *
 * <p>Atajos: Espacio ▶/⏸ · → +1 tick · ← −1 tick · Inicio reiniciar · Fin hasta el final · F ajustar.
 */
final class VentanaMaquina {

    private final Maquina maquina;
    private final SesionSimulacion sesion;
    private final Reproductor reproductor = new Reproductor();
    private final LienzoMapa lienzo;
    private final Stage ventana = new Stage();

    private final TreeView<Nodo> explorador = new TreeView<>();
    private final Map<Integer, TreeItem<Nodo>> itemsProceso = new LinkedHashMap<>();
    private final VBox propiedades = new VBox(10);
    private final BooleanProperty verEventos = new SimpleBooleanProperty(true);
    private final PanelEventos panelEventos = new PanelEventos(verEventos);
    private final SplitPane vertical = new SplitPane();
    private final TableView<Instantanea.Proceso> tablaPcb = new TableView<>();
    private boolean actualizandoTabla;
    private final ComboBox<Vista> selectorVista = new ComboBox<>(FXCollections.observableArrayList(Vista.values()));
    private final Button reproducir = boton("▶", "Reproducir / pausar (Espacio)");
    private final Button pasoAtras = boton("◀", "Retroceder 1 tick (←)");
    private final Button pasoAdelante = boton("▶|", "Avanzar 1 tick (→)");
    private final Button reiniciar = boton("⏮", "Volver a t = 0 (Inicio)");
    private final Button alFinal = boton("⏭", "Simular hasta que terminen todos (Fin)");
    private final Slider linea = new Slider(0, 1, 0);
    private final Label instante = new Label();
    private final Label estado = new Label();
    private final Label zoom = new Label();
    private final SplitPane centro = new SplitPane();
    private final Node panelExplorador, panelPropiedades;
    private final BooleanProperty verExplorador = new SimpleBooleanProperty(true), verPropiedades = new SimpleBooleanProperty(true);
    private int seleccionado = Instantanea.NINGUNO;
    private boolean moviendoLinea;
    private long ultimoCuadro;

    /** Nodo del explorador: una vista, un proceso o un título. */
    private record Nodo(String texto, Vista vista, int pid) {
        @Override public String toString() { return texto; }
    }

    private final AnimationTimer animacion = new AnimationTimer() {
        @Override public void handle(long ahora) {
            long dt = ultimoCuadro == 0 ? 0 : ahora - ultimoCuadro;
            ultimoCuadro = ahora;
            int n = reproductor.ticks(dt);
            if (n > 0) accion(() -> sesion.avanzar(n));
            if (!sesion.puedeAvanzar()) pausar();
        }
    };

    private VentanaMaquina(Maquina maquina) {
        this.maquina = maquina;
        this.sesion = new SesionSimulacion(maquina);             // valida que el lote quepa en la máquina
        this.lienzo = new LienzoMapa(maquina.configuracion().politica(), this::elegirProceso, this::actualizarZoom);
        panelExplorador = armarExplorador();
        panelPropiedades = armarPropiedades();
    }

    /** Arma la ventana de la máquina con el lote oficial cargado en t = 0. */
    static Stage crear(Maquina maquina) { return new VentanaMaquina(maquina).armar(); }

    private Stage armar() {
        BorderPane raiz = new BorderPane();
        raiz.setTop(barraHerramientas());                               // una sola línea: menús y todos los controles
        verExplorador.addListener((o, a, v) -> mostrarPanel(panelExplorador, true, v));
        verPropiedades.addListener((o, a, v) -> mostrarPanel(panelPropiedades, false, v));
        centro.getItems().setAll(panelExplorador, lienzo, panelPropiedades);
        centro.setDividerPositions(0.18, 0.78);
        SplitPane.setResizableWithParent(panelExplorador, false);
        SplitPane.setResizableWithParent(panelPropiedades, false);
        vertical.setOrientation(Orientation.VERTICAL);                  // arena arriba, «Eventos y E/S» abajo
        vertical.getItems().setAll(centro, panelEventos);
        vertical.setDividerPositions(0.71);
        SplitPane.setResizableWithParent(panelEventos, false);
        verEventos.addListener((o, a, v) -> {
            if (!v) vertical.getItems().remove(panelEventos);
            else if (!vertical.getItems().contains(panelEventos)) {
                vertical.getItems().add(panelEventos);
                vertical.setDividerPositions(0.71);
            }
        });
        raiz.setCenter(vertical);
        raiz.setBottom(piePagina());
        raiz.getStyleClass().add("espacio-trabajo");

        sesion.alCambiar(this::mostrar);
        mostrar(sesion.vista());

        double[] tam = App.tamanoQueCabe(1280, 800);
        Scene escena = new Scene(raiz, tam[0], tam[1]);
        App.aplicarTema(escena);
        escena.addEventFilter(KeyEvent.KEY_PRESSED, this::atajo);
        ventana.setTitle(maquina.nombre() + " — Simulador SO");
        ventana.setMinWidth(900);
        ventana.setMinHeight(560);
        ventana.setScene(escena);
        ventana.setMaximized(true);                                     // la barra de título y sus botones siempre a la vista
        ventana.setOnHidden(e -> {
            animacion.stop();
            lienzo.detener();
        });
        return ventana;
    }

    // ---------------------------------------------------------------- armado

    /** Menús desplegables Simulación y Ver (las opciones que no caben en la barra). */
    private MenuBar menus() {
        MenuItem reproducirMenu = item("Reproducir / pausar", "Espacio", this::alternarReproduccion);
        MenuItem avanzarMenu = item("Avanzar 1 tick", "→", () -> accion(() -> sesion.avanzar(1)));
        MenuItem retrocederMenu = item("Retroceder 1 tick", "←", () -> accion(() -> sesion.retroceder(1)));
        MenuItem reiniciarMenu = item("Reiniciar (t = 0)", "Inicio", () -> { pausar(); accion(sesion::reiniciar); });
        MenuItem finalMenu = item("Hasta el final", "Fin", () -> { pausar(); accion(sesion::hastaElFinal); });
        Menu simulacion = new Menu("Simulación", null, reproducirMenu, avanzarMenu, retrocederMenu, new SeparatorMenuItem(),
                reiniciarMenu, finalMenu);

        CheckMenuItem explorador = new CheckMenuItem("Explorador");
        explorador.selectedProperty().bindBidirectional(verExplorador);
        CheckMenuItem props = new CheckMenuItem("Propiedades");
        props.selectedProperty().bindBidirectional(verPropiedades);
        CheckMenuItem eventosMenu = new CheckMenuItem("Eventos y E/S");
        eventosMenu.selectedProperty().bindBidirectional(verEventos);
        CheckMenuItem claro = new CheckMenuItem("Tema claro");
        claro.selectedProperty().bindBidirectional(App.temaClaro());
        Menu ver = new Menu("Ver", null, explorador, props, eventosMenu, new SeparatorMenuItem(),
                item("Ajustar a la ventana", "F", lienzo::ajustar), item("Acercar", "", () -> lienzo.zoom(Camara.PASO_ZOOM)),
                item("Alejar", "", () -> lienzo.zoom(1 / Camara.PASO_ZOOM)), new SeparatorMenuItem(), claro);
        MenuBar menus = new MenuBar(simulacion, ver);
        menus.getStyleClass().add("menu-superior");

        return menus;
    }

    private static MenuItem item(String texto, String atajo, Runnable accion) {
        MenuItem m = new MenuItem(atajo.isEmpty() ? texto : texto + "    (" + atajo + ")");
        m.setOnAction(e -> accion.run());
        return m;
    }

    /** Oculta o muestra un panel lateral; el lienzo se queda con el espacio libre. */
    private void mostrarPanel(Node panel, boolean izquierda, boolean visible) {
        if (!visible) centro.getItems().remove(panel);
        else if (!centro.getItems().contains(panel)) {
            if (izquierda) centro.getItems().add(0, panel);
            else centro.getItems().add(panel);
        }
        boolean exp = centro.getItems().contains(panelExplorador), props = centro.getItems().contains(panelPropiedades);
        if (exp && props) centro.setDividerPositions(0.18, 0.78);       // los divisores se recalculan según lo visible
        else if (exp) centro.setDividerPositions(0.18);
        else if (props) centro.setDividerPositions(0.78);
    }

    /** Barra de título de un panel lateral con su botón para ocultarlo. */
    private static Node barraPanel(String titulo, BooleanProperty visible, boolean izquierda) {
        Label l = new Label(titulo);
        l.getStyleClass().add("titulo-panel");
        Button ocultar = new Button(izquierda ? "‹" : "›");
        ocultar.getStyleClass().addAll("boton-icono", "boton-ocultar");
        ocultar.setTooltip(new Tooltip("Ocultar " + titulo.toLowerCase(java.util.Locale.ROOT) + " (se vuelve a mostrar en Ver)"));
        ocultar.setOnAction(e -> visible.set(false));
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox b = izquierda ? new HBox(6, l, hueco, ocultar) : new HBox(6, ocultar, hueco, l);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    private Node barraHerramientas() {
        selectorVista.setValue(Vista.MAPA_ESTADOS);
        selectorVista.valueProperty().addListener((o, a, v) -> { if (v != null) elegirVista(v); });
        ComboBox<Integer> velocidad = new ComboBox<>(FXCollections.observableArrayList(Reproductor.VELOCIDADES));
        velocidad.setConverter(new StringConverter<>() {
            @Override public String toString(Integer v) { return v == null ? "" : Reproductor.etiqueta(v); }
            @Override public Integer fromString(String s) { return null; }
        });
        velocidad.setValue(reproductor.velocidad());
        velocidad.setTooltip(new Tooltip("Velocidad de reproducción"));
        velocidad.valueProperty().addListener((o, a, v) -> {
            if (v == null) return;
            reproductor.velocidad(v);
            if (reproductor.reproduciendo()) lienzo.velocidadReproduccion(v);   // las fichas caminan al ritmo de la reproducción
        });

        reproducir.getStyleClass().add("boton-primario");
        reproducir.setOnAction(e -> alternarReproduccion());
        pasoAtras.setOnAction(e -> accion(() -> sesion.retroceder(1)));
        pasoAdelante.setOnAction(e -> accion(() -> sesion.avanzar(1)));
        reiniciar.setOnAction(e -> { pausar(); accion(sesion::reiniciar); });
        alFinal.setOnAction(e -> { pausar(); accion(sesion::hastaElFinal); });

        Button menos = boton("−", "Alejar");
        menos.setOnAction(e -> lienzo.zoom(1 / Camara.PASO_ZOOM));
        Button mas = boton("+", "Acercar");
        mas.setOnAction(e -> lienzo.zoom(Camara.PASO_ZOOM));
        Button ajustar = boton("⤢ Ajustar", "Encajar el dibujo en la ventana (F o doble clic)");
        ajustar.setOnAction(e -> lienzo.ajustar());

        Label etiquetaVista = new Label("Vista");
        etiquetaVista.setLabelFor(selectorVista);
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        Label info = new Label("ⓘ");                                    // nombre y características de la máquina, sin ocupar espacio
        info.getStyleClass().add("info-maquina");
        info.setTooltip(new Tooltip(maquina.nombre() + "\n" + FichaMaquina.resumen(maquina.configuracion())
                + "\nCarga: lote oficial de 20 procesos"));
        HBox barra = new HBox(6, menus(), separador(), etiquetaVista, selectorVista, separador(), reiniciar, pasoAtras, reproducir,
                pasoAdelante, alFinal, velocidad, hueco, menos, zoom, mas, ajustar, separador(), info, App.botonTema());
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.getStyleClass().add("barra-herramientas");
        return barra;
    }

    private Node armarExplorador() {
        TreeItem<Nodo> raiz = new TreeItem<>(new Nodo(maquina.nombre(), null, Instantanea.NINGUNO));
        TreeItem<Nodo> vistas = new TreeItem<>(new Nodo("Vistas", null, Instantanea.NINGUNO));
        for (Vista v : Vista.values()) vistas.getChildren().add(new TreeItem<>(new Nodo(v.etiqueta, v, Instantanea.NINGUNO)));
        TreeItem<Nodo> procesos = new TreeItem<>(new Nodo("Procesos", null, Instantanea.NINGUNO));
        for (Instantanea.Proceso p : sesion.vista().procesos()) {
            TreeItem<Nodo> item = new TreeItem<>(new Nodo("P" + p.pid(), null, p.pid()), new Circle(5));
            itemsProceso.put(p.pid(), item);
            procesos.getChildren().add(item);
        }
        raiz.getChildren().addAll(List.of(vistas, procesos));
        raiz.setExpanded(true);
        vistas.setExpanded(true);
        procesos.setExpanded(true);
        explorador.setRoot(raiz);
        explorador.getStyleClass().add("explorador");
        explorador.getSelectionModel().selectedItemProperty().addListener((o, a, item) -> {
            if (item == null) return;
            Nodo n = item.getValue();
            if (n.vista() != null) selectorVista.setValue(n.vista());
            else if (n.pid() != Instantanea.NINGUNO) elegirProceso(n.pid());
            else if (item == raiz) elegirProceso(Instantanea.NINGUNO);
        });
        VBox caja = new VBox(6, barraPanel("Explorador", verExplorador, true), explorador);
        VBox.setVgrow(explorador, Priority.ALWAYS);
        caja.getStyleClass().add("panel-lateral");
        caja.setMinWidth(160);
        return caja;
    }

    /**
     * Panel derecho con pestañas: Propiedades (de la máquina o del proceso elegido), Procesos (la
     * cola de procesos/PCB completa) y Kernel (señales, eventos varios, dispositivos y despachador,
     * que en la arena son módulos compactos).
     */
    private Node armarPropiedades() {
        Tab tabPropiedades = new Tab("Propiedades", desplazable(propiedades));
        Tab tabProcesos = new Tab("PCB", armarTablaPcb());
        tabProcesos.setTooltip(new Tooltip("Cola de procesos: un PCB por fila"));
        TabPane pestanas = new TabPane(tabPropiedades, tabProcesos);
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pestanas.getStyleClass().add("pestanas-panel");
        VBox caja = new VBox(4, barraPanel("Panel", verPropiedades, false), pestanas);
        VBox.setVgrow(pestanas, Priority.ALWAYS);
        caja.getStyleClass().add("panel-lateral");
        caja.setMinWidth(240);
        return caja;
    }

    private static ScrollPane desplazable(VBox contenido) {
        ScrollPane d = new ScrollPane(contenido);
        d.setFitToWidth(true);
        d.getStyleClass().add("detalle-scroll");
        contenido.setPadding(new Insets(6, 4, 8, 0));
        return d;
    }

    /** Tabla de la cola de procesos (un PCB por fila); elegir una fila elige el proceso. */
    private TableView<Instantanea.Proceso> armarTablaPcb() {
        tablaPcb.getColumns().setAll(List.of(
                columna("PID", p -> "P" + p.pid(), 48),
                columnaEstado(),
                columna("Lleg.", p -> Integer.toString(p.llegada()), 44),
                columna("Ráf.", p -> Integer.toString(p.burst()), 40),
                columna("Rest.", p -> Integer.toString(p.restante()), 42),
                columna("Prior.", p -> Integer.toString(p.prioridad()), 44),
                columna("PC", Instantanea.Proceso::pcHex, 92),
                columna("Marcos", p -> Integer.toString(p.marcos()), 52),
                columna("Espera", p -> Integer.toString(p.espera()), 50),
                columna("Marcas", p -> p.insignias().stream().sorted().map(Propiedades::insignia)
                        .collect(java.util.stream.Collectors.joining(", ")), 140)));
        tablaPcb.setPlaceholder(new Label("Sin procesos"));
        tablaPcb.getStyleClass().add("tabla-pcb");
        tablaPcb.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> {
            if (!actualizandoTabla && p != null && p.pid() != seleccionado) elegirProceso(p.pid());
        });
        return tablaPcb;
    }

    private static TableColumn<Instantanea.Proceso, String> columna(String titulo,
            java.util.function.Function<Instantanea.Proceso, String> valor, double ancho) {
        TableColumn<Instantanea.Proceso, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new ReadOnlyStringWrapper(valor.apply(d.getValue())));
        c.setPrefWidth(ancho);
        return c;
    }

    /** Columna del estado con un punto del color del estado. */
    private static TableColumn<Instantanea.Proceso, String> columnaEstado() {
        TableColumn<Instantanea.Proceso, String> c = columna("Estado", p -> MapaEstados.titulo(p.estado()), 96);
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean vacio) {
                super.updateItem(v, vacio);
                Instantanea.Proceso p = vacio || getTableRow() == null ? null : getTableRow().getItem();
                setText(vacio ? null : v);
                setGraphic(p == null ? null : new Circle(5, Color.web(p.color())));
            }
        });
        return c;
    }

    private Node piePagina() {
        linea.setBlockIncrement(1);
        linea.setMajorTickUnit(1);
        linea.setSnapToTicks(true);
        HBox.setHgrow(linea, Priority.ALWAYS);
        linea.valueProperty().addListener((o, a, v) -> {
            if (moviendoLinea) return;
            int t = (int) Math.round(v.doubleValue());
            if (t != sesion.vista().t()) {
                pausar();
                accion(() -> sesion.irA(t));
            }
        });
        instante.getStyleClass().add("instante");
        instante.setMinWidth(170);
        HBox tiempo = new HBox(12, new Label("Línea de tiempo"), linea, instante);
        tiempo.setAlignment(Pos.CENTER_LEFT);
        tiempo.getStyleClass().add("linea-tiempo");

        HBox leyenda = new HBox(14);
        for (Estado e : Estado.values()) {
            Rectangle muestra = new Rectangle(12, 12, Color.web(ColorEstado.de(e)));
            muestra.setArcWidth(4);
            muestra.setArcHeight(4);
            Label l = new Label(MapaEstados.titulo(e), muestra);
            l.getStyleClass().add("leyenda-item");
            leyenda.getChildren().add(l);
        }
        Label prioridad = new Label("prioridad (1 = más alta)", insigniaMuestra());
        prioridad.getStyleClass().add("leyenda-item");
        Circle punto = new Circle(4, Color.web("#fde047"));
        Label marca = new Label("marca (expropiado, fin de quantum, …)", punto);
        marca.getStyleClass().add("leyenda-item");
        Rectangle borde = new Rectangle(12, 12, Color.TRANSPARENT);
        borde.setStroke(Color.web("#94a3b8"));                         // se ve en los dos temas
        Label cambio = new Label("cambió en este tick", borde);
        cambio.getStyleClass().add("leyenda-item");
        Label dorada = new Label("lo elegiría el planificador", muestraDorada());
        dorada.getStyleClass().add("leyenda-item");
        leyenda.getChildren().addAll(new Separator(Orientation.VERTICAL), prioridad, dorada, marca, cambio);
        leyenda.setAlignment(Pos.CENTER_LEFT);

        estado.getStyleClass().add("estado");
        HBox barraEstado = new HBox(estado);
        barraEstado.getStyleClass().add("barra-estado");
        VBox pie = new VBox(8, tiempo, leyenda, barraEstado);
        pie.getStyleClass().add("pie");
        return pie;
    }

    // ---------------------------------------------------------------- reacciones

    /** Muestra una foto: lienzo, explorador, propiedades, línea de tiempo y barra de estado. */
    private void mostrar(Instantanea foto) {
        lienzo.mostrar(foto);
        for (Instantanea.Proceso p : foto.procesos()) {
            TreeItem<Nodo> item = itemsProceso.get(p.pid());
            if (item != null && item.getGraphic() instanceof Circle c) c.setFill(Color.web(p.color()));
        }
        mostrarPropiedades(foto);
        actualizandoTabla = true;                                      // la tabla y la pestaña Kernel siguen la foto
        tablaPcb.getItems().setAll(foto.procesos());
        foto.proceso(seleccionado).ifPresent(p -> tablaPcb.getItems().stream().filter(x -> x.pid() == p.pid()).findFirst()
                .ifPresent(x -> tablaPcb.getSelectionModel().select(x)));
        actualizandoTabla = false;
        panelEventos.mostrar(foto);
        moviendoLinea = true;
        linea.setMax(Math.max(1, sesion.tActual()));
        linea.setValue(foto.t());
        moviendoLinea = false;
        instante.setText("t = " + foto.t() + " / " + sesion.tActual());
        estado.setText(Propiedades.barraEstado(foto, sesion.enVivo(), sesion.tActual(), sesion.terminada(), sesion.atascada()));
        boolean puede = sesion.puedeAvanzar();
        pasoAdelante.setDisable(!puede);
        alFinal.setDisable(!puede);
        pasoAtras.setDisable(foto.t() == 0);
        if (!puede && !reproductor.reproduciendo()) reproducir.setDisable(true);
        else reproducir.setDisable(false);
    }

    private void mostrarPropiedades(Instantanea foto) {
        List<Propiedades.Fila> filas;
        String titulo;
        var proceso = seleccionado == Instantanea.NINGUNO ? java.util.Optional.<Instantanea.Proceso>empty() : foto.proceso(seleccionado);
        if (proceso.isPresent()) {
            filas = Propiedades.deProceso(proceso.get());
            titulo = "Proceso P" + seleccionado;
        } else {
            filas = Propiedades.deMaquina(foto);
            titulo = "Máquina en t = " + foto.t();
        }
        pintar(propiedades, titulo, filas);
        if (proceso.isPresent()) {
            Button volver = boton("← Ver la máquina", "Quitar la selección del proceso");
            volver.setOnAction(e -> elegirProceso(Instantanea.NINGUNO));
            propiedades.getChildren().add(1, volver);
        }
    }

    /** Pinta filas agrupadas por sección en un VBox (pestañas Propiedades y Kernel). */
    private static void pintar(VBox destino, String titulo, List<Propiedades.Fila> filas) {
        Label encabezado = new Label(titulo);
        encabezado.getStyleClass().add("titulo-tarjeta");
        destino.getChildren().setAll(encabezado);
        String grupo = null;
        GridPane grilla = null;
        for (Propiedades.Fila f : filas) {
            if (!f.grupo().equals(grupo)) {
                grupo = f.grupo();
                Label g = new Label(grupo);
                g.getStyleClass().add("grupo-propiedad");
                grilla = new GridPane();
                grilla.setHgap(10);
                grilla.setVgap(3);
                destino.getChildren().addAll(g, grilla);
            }
            Label clave = new Label(f.clave());
            clave.getStyleClass().add("etiqueta-campo");
            clave.setMinWidth(Region.USE_PREF_SIZE);                       // la clave nunca se recorta; el valor se ajusta
            Label valor = new Label(f.valor());
            valor.getStyleClass().add("valor-campo");
            valor.setWrapText(true);
            grilla.addRow(grilla.getRowCount(), clave, valor);
        }
    }

    private void elegirProceso(int pid) {
        seleccionado = pid;
        lienzo.seleccionar(pid);
        mostrarPropiedades(sesion.vista());
        if (!actualizandoTabla) {
            actualizandoTabla = true;
            tablaPcb.getItems().stream().filter(x -> x.pid() == pid).findFirst()
                    .ifPresentOrElse(x -> tablaPcb.getSelectionModel().select(x), () -> tablaPcb.getSelectionModel().clearSelection());
            actualizandoTabla = false;
        }
        TreeItem<Nodo> item = itemsProceso.get(pid);
        if (item != null && explorador.getSelectionModel().getSelectedItem() != item) explorador.getSelectionModel().select(item);
    }

    private void elegirVista(Vista v) {
        lienzo.vista(v);
        if (v == Vista.MAPA_ESTADOS) lienzo.ajustar();
    }

    private void alternarReproduccion() {
        if (reproductor.reproduciendo()) pausar();
        else if (sesion.puedeAvanzar()) {
            reproductor.reproducir();
            lienzo.velocidadReproduccion(reproductor.velocidad());
            ultimoCuadro = 0;
            animacion.start();
            reproducir.setText("⏸");
        }
    }

    private void pausar() {
        reproductor.pausar();
        lienzo.velocidadReproduccion(1);                                   // paso a paso: se ve todo el recorrido
        animacion.stop();
        reproducir.setText("▶");
        reproducir.setDisable(!sesion.puedeAvanzar());
    }

    private void actualizarZoom() { zoom.setText(lienzo.camara().porcentaje() + " %"); }

    private void atajo(KeyEvent e) {
        if (e.getTarget() instanceof ComboBoxBase<?>) return;              // las listas usan sus propias flechas
        switch (e.getCode()) {
            case SPACE -> alternarReproduccion();
            case RIGHT -> { if (!pasoAdelante.isDisabled()) accion(() -> sesion.avanzar(1)); }
            case LEFT -> { if (!pasoAtras.isDisabled()) accion(() -> sesion.retroceder(1)); }
            case HOME -> { pausar(); accion(sesion::reiniciar); }
            case END -> { if (!alFinal.isDisabled()) { pausar(); accion(sesion::hastaElFinal); } }
            case F -> lienzo.ajustar();
            default -> { return; }
        }
        e.consume();
    }

    /** Ejecuta una acción de la sesión; un error del simulador se muestra y detiene la reproducción. */
    private void accion(Runnable r) {
        try {
            r.run();
        } catch (SimuladorException ex) {
            pausar();
            Alert a = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
            Label texto = new Label(ex.getMessage());
            texto.setWrapText(true);
            texto.setMaxWidth(480);
            a.getDialogPane().setContent(texto);
            a.setHeaderText("La simulación no pudo continuar");
            a.initOwner(ventana);
            a.getDialogPane().getStylesheets().setAll(ventana.getScene().getStylesheets());
            a.show();
        }
    }

    // ---------------------------------------------------------------- auxiliares

    /** Círculo con un número, como la insignia de prioridad de cada ficha. */
    private static Node insigniaMuestra() {
        Circle c = new Circle(8, Color.web("#0b1120"));
        c.setStroke(Color.web("#94a3b8"));
        c.setStrokeWidth(1.5);
        Label n = new Label("2");
        n.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 10px; -fx-font-weight: bold;");
        return new javafx.scene.layout.StackPane(c, n);
    }

    private static Node muestraDorada() {
        Rectangle r = new Rectangle(14, 12, Color.web("#33280f"));
        r.setArcWidth(5);
        r.setArcHeight(5);
        r.setStroke(Color.web("#ffc857"));
        r.setStrokeWidth(1.5);
        return r;
    }

    private static Separator separador() { return new Separator(Orientation.VERTICAL); }

    private static Button boton(String texto, String ayuda) {
        Button b = new Button(texto);
        b.setTooltip(new Tooltip(ayuda));
        b.getStyleClass().add("boton-icono");
        return b;
    }
}
