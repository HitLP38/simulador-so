package pe.uni.so.ui;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntConsumer;
import javafx.animation.AnimationTimer;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.sesion.AnimacionArena;
import pe.uni.so.sesion.Camara;
import pe.uni.so.sesion.MapaEstados;
import pe.uni.so.sesion.MapaEstados.Rect;
import pe.uni.so.sesion.MapaEstados.Sala;
import pe.uni.so.sesion.Vista;

/**
 * [G5] Lienzo central: la arena del kernel con el flujo «Ingreso de un proceso» del curso. Salas
 * con baldosas y borde luminoso, pasillos, la cola de procesos, el recuadro de planificación, la
 * puerta de E/S (el detalle está en {@link PanelEventos}) y, con prioridades, los 5 carriles de
 * listos con su mapa de bits. Las fichas
 * caminan por los pasillos al cambiar de sala, con estela. Rueda = zoom al cursor, arrastrar =
 * mover, doble clic = ajustar, clic en una ficha = elegir el proceso. La geometría la da
 * {@link MapaEstados} y el movimiento {@link AnimacionArena}; aquí solo se dibuja.
 */
final class LienzoMapa extends Pane {

    private static final Color SELECCION = Color.web("#22d3ee"), EXPROPIADO = Color.web("#b39dff"), MARCA = Color.web("#fde047");
    private static final double UMBRAL_ARRASTRE = 4, APAGAR_DESTELLO = 1.1;   // destello perdido por segundo
    private static final String MONO = "Consolas";
    /** Largo de la ruta más larga más un margen: al reproducir, cada recorrido cabe en un tick. */
    private static final double RECORRIDO_POR_TICK = 900;

    private final Canvas canvas = new Canvas();
    private final Camara camara = new Camara();
    private final AnimacionArena animacion = new AnimacionArena();
    private final Map<Sala, Double> destellos = new EnumMap<>(Sala.class);
    private final IntConsumer alElegir;
    private final Runnable alCambiarZoom;
    private Politica politica;
    private MapaEstados.Diagrama diagrama;
    private Vista vista = Vista.MAPA_ESTADOS;
    private int seleccionado = Instantanea.NINGUNO;
    private boolean ajusteAutomatico = true, sucio = true;   // se reajusta al cambiar de tamaño hasta que el usuario mueva o haga zoom
    private double xPrevio, yPrevio, recorrido;
    private long ultimoCuadro;
    private final javafx.beans.value.ChangeListener<Boolean> oyenteTema = (o, a, v) -> sucio = true;

    private final AnimationTimer cuadros = new AnimationTimer() {
        @Override public void handle(long ahora) {
            double dt = ultimoCuadro == 0 ? 0 : Math.min(0.1, (ahora - ultimoCuadro) / 1e9);
            ultimoCuadro = ahora;
            boolean mueve = animacion.avanzar(dt);
            boolean destella = false;
            for (Map.Entry<Sala, Double> e : destellos.entrySet()) {
                double v = Math.max(0, e.getValue() - APAGAR_DESTELLO * dt);
                e.setValue(v);
                destella |= v > 0;
            }
            if (mueve || destella || sucio) {
                sucio = false;
                redibujar();
            }
        }
    };

    LienzoMapa(Politica politica, IntConsumer alElegir, Runnable alCambiarZoom) {
        this.politica = politica;
        this.alElegir = alElegir;
        this.alCambiarZoom = alCambiarZoom;
        getStyleClass().add("lienzo");
        getChildren().add(canvas);
        canvas.setManaged(false);                                           // el canvas no fija el tamaño del panel: puede encogerse
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener((o, a, v) -> alCambiarTamano());
        canvas.heightProperty().addListener((o, a, v) -> alCambiarTamano());
        setMinSize(200, 150);
        App.temaClaro().addListener(oyenteTema);

        setOnScroll(e -> {
            if (e.getDeltaY() == 0) return;
            camara.zoom(e.getDeltaY() > 0 ? Camara.PASO_ZOOM : 1 / Camara.PASO_ZOOM, e.getX(), e.getY());
            ajusteAutomatico = false;
            sucio = true;
            alCambiarZoom.run();
        });
        setOnMousePressed(e -> {
            xPrevio = e.getX();
            yPrevio = e.getY();
            recorrido = 0;
        });
        setOnMouseDragged(e -> {
            double dx = e.getX() - xPrevio, dy = e.getY() - yPrevio;
            recorrido += Math.abs(dx) + Math.abs(dy);
            camara.mover(dx, dy);
            ajusteAutomatico = false;
            xPrevio = e.getX();
            yPrevio = e.getY();
            sucio = true;
        });
        setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY || recorrido > UMBRAL_ARRASTRE) return;   // fue un arrastre
            if (e.getClickCount() == 2) {
                ajustar();
                return;
            }
            if (diagrama == null || !vista.disponible) return;
            alElegir.accept(pidEn(camara.aMundoX(e.getX()), camara.aMundoY(e.getY())));
        });
        cuadros.start();
    }

    Camara camara() { return camara; }

    /**
     * Proceso bajo el punto (mx, my) del mundo, con el tamaño con que se dibuja cada ficha (la
     * miniatura del origen es más chica que la de la CPU). Los que esperan tras la puerta de E/S
     * no se dibujan y no se pueden elegir; gana la ficha dibujada encima.
     */
    private int pidEn(double mx, double my) {
        java.util.List<AnimacionArena.Sprite> lista = new java.util.ArrayList<>(animacion.sprites());
        for (int i = lista.size() - 1; i >= 0; i--) {
            AnimacionArena.Sprite s = lista.get(i);
            MapaEstados.Ficha f = diagrama.ficha(s.pid()).orElse(null);
            if (f == null || (f.sala() == Sala.ES && !s.enMovimiento())) continue;
            boolean mini = f.sala() == Sala.ORIGEN_SO && !s.enMovimiento(), cpu = f.sala() == Sala.CPU && !s.enMovimiento();
            double w = cpu ? MapaEstados.FICHA_CPU_ANCHO : mini ? MapaEstados.FICHA_MINI_ANCHO : MapaEstados.FICHA_ANCHO;
            double h = cpu ? MapaEstados.FICHA_CPU_ALTO : mini ? MapaEstados.FICHA_MINI_ALTO : MapaEstados.FICHA_ALTO;
            if (Math.abs(mx - s.x()) <= w / 2 && Math.abs(my - s.y()) <= h / 2) return s.pid();
        }
        return Instantanea.NINGUNO;
    }

    /** Muestra una foto: si es la siguiente, las fichas caminan; si no, se colocan. */
    void mostrar(Instantanea nueva) {
        MapaEstados.Diagrama d = MapaEstados.de(nueva, politica);
        boolean primera = diagrama == null;
        boolean siguiente = !primera && d.t() > diagrama.t() && d.t() - diagrama.t() <= AnimacionArena.SALTO_ANIMADO;
        diagrama = d;
        animacion.mostrar(d);
        if (siguiente)
            for (MapaEstados.SalaDibujo s : d.salas()) if (s.llegada()) destellos.put(s.sala(), 1.0);
        if (primera && getWidth() > 0) ajustar();
        sucio = true;
    }

    /** Ajusta la rapidez de las fichas a la velocidad de reproducción (ticks por segundo). */
    void velocidadReproduccion(int ticksPorSegundo) {
        animacion.velocidad(Math.max(AnimacionArena.VELOCIDAD_INICIAL, RECORRIDO_POR_TICK * ticksPorSegundo));
    }

    void vista(Vista v) {
        vista = v;
        sucio = true;
    }

    void seleccionar(int pid) {
        seleccionado = pid;
        sucio = true;
    }

    /** Detiene el ciclo de dibujo (al cerrar la ventana). */
    void detener() {
        cuadros.stop();
        App.temaClaro().removeListener(oyenteTema);                  // la propiedad global no retiene este lienzo
    }

    /** Encaja la arena en el lienzo (botón «Ajustar», doble clic o tecla F). */
    void ajustar() {
        ajusteAutomatico = true;
        if (diagrama == null) return;
        camara.ajustar(diagrama.ancho(), diagrama.alto(), getWidth(), getHeight(), 12);
        sucio = true;
        alCambiarZoom.run();
    }

    void zoom(double factor) {
        ajusteAutomatico = false;
        camara.zoom(factor, getWidth() / 2, getHeight() / 2);
        sucio = true;
        alCambiarZoom.run();
    }

    private void alCambiarTamano() {
        if (ajusteAutomatico && getWidth() > 0 && getHeight() > 0) ajustar();
        sucio = true;
    }

    // ---------------------------------------------------------------- dibujo

    private void redibujar() {
        Paleta p = Paleta.de(App.temaClaro().get());
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.setGlobalAlpha(1);
        g.setLineDashes();
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, p.fondoArriba()), new Stop(1, p.fondoAbajo())));
        g.fillRect(0, 0, w, h);
        if (!vista.disponible) {
            g.setFill(p.tenue());
            g.setFont(Font.font("Segoe UI", 16));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText(vista.etiqueta + "\n\n" + Vista.AVISO_EN_CONSTRUCCION, w / 2, h / 2);
            return;
        }
        if (diagrama == null) return;
        g.setTransform(camara.escala(), 0, 0, camara.escala(), camara.x(), camara.y());
        for (Rect r : diagrama.pasillos()) pasillo(g, p, r);
        grupoPlanificacion(g, p);
        for (MapaEstados.SalaDibujo s : diagrama.salas()) sala(g, p, s);
        contenidoSalas(g, p);
        for (AnimacionArena.Sprite s : animacion.sprites())
            diagrama.ficha(s.pid()).ifPresent(f -> ficha(g, p, f, s));
        for (MapaEstados.SalaDibujo s : diagrama.salas())                  // «+N» encima de las fichas apiladas
            if (s.exceso() > 0) {
                Rect r = s.rect();
                etiqueta(g, p, "+" + s.exceso() + " más", r.x() + r.ancho() - 10, r.y() + r.alto() - 14, Color.web(s.sala().acento));
            }
        g.setTransform(1, 0, 0, 1, 0, 0);
    }

    private static void pasillo(GraphicsContext g, Paleta p, Rect r) {
        g.save();
        redondeado(g, r.x(), r.y(), r.ancho(), r.alto(), 8);
        g.clip();
        baldosas(g, r, 20, p.pasillo1(), p.pasillo2());
        g.restore();
        g.setStroke(p.bordePasillo());
        g.setLineWidth(2);
        g.strokeRoundRect(r.x(), r.y(), r.ancho(), r.alto(), 16, 16);
    }

    /** Recuadro punteado que agrupa planificar y otorgar CPU, como en el diagrama del curso. */
    private static void grupoPlanificacion(GraphicsContext g, Paleta p) {
        Rect r = MapaEstados.GRUPO_PLANIFICACION;
        g.setStroke(p.grupo());
        g.setLineWidth(2);
        g.setLineDashes(9, 6);
        g.strokeRoundRect(r.x(), r.y(), r.ancho(), r.alto(), 18, 18);
        g.setLineDashes();
        texto(g, "PLANIFICACIÓN", r.x() + r.ancho() / 2, r.y() + 14, p.tenue(), 9.5, FontWeight.BOLD, TextAlignment.CENTER);
    }

    private void sala(GraphicsContext g, Paleta p, MapaEstados.SalaDibujo s) {
        Rect r = s.rect();
        double destello = destellos.getOrDefault(s.sala(), 0.0);
        Color acento = Color.web(s.sala().acento);
        if (s.sala() == Sala.ES) {                                           // puerta: el detalle está en el panel inferior
            g.setFill(acento.deriveColor(0, 1, 1, 0.18));
            g.fillRoundRect(r.x(), r.y(), r.ancho(), r.alto(), 14, 14);
            brillo(g, destello > 0 ? p.oro().interpolate(acento, 1 - destello) : acento, 2, destello > 0 ? 1.6 : 0.8,
                    () -> g.strokeRoundRect(r.x(), r.y(), r.ancho(), r.alto(), 14, 14));
            texto(g, "E/S ↓ " + s.cantidad(), r.x() + r.ancho() / 2, r.y() + r.alto() / 2, acento, 11, FontWeight.BOLD, TextAlignment.CENTER);
            return;
        }
        g.save();
        redondeado(g, r.x(), r.y(), r.ancho(), r.alto(), 14);
        g.setFill(new LinearGradient(r.x(), r.y(), r.x(), r.y() + r.alto(), false, CycleMethod.NO_CYCLE,
                new Stop(0, p.salaArriba()), new Stop(1, p.salaAbajo())));
        g.fill();
        g.clip();
        baldosas(g, r, 26, p.baldosa1(), p.baldosa2());
        g.restore();
        Color borde = destello > 0 ? p.oro().interpolate(acento, 1 - destello) : acento;
        brillo(g, borde, destello > 0 ? 3.5 : 2.5, destello > 0 ? 1.6 : (s.sala().modulo ? 0.6 : 1),
                () -> g.strokeRoundRect(r.x(), r.y(), r.ancho(), r.alto(), 28, 28));
        texto(g, s.sala().titulo, r.x() + 10, r.y() + 14, p.texto(), 9.5, FontWeight.BOLD, TextAlignment.LEFT);
        texto(g, s.sala().tecnico, r.x() + 10, r.y() + 27, p.tenue(), 8.5, FontWeight.NORMAL, TextAlignment.LEFT);   // término técnico
        if (!s.sala().modulo || s.sala() == Sala.PCB)
            texto(g, Integer.toString(s.cantidad()), r.x() + r.ancho() - 10, r.y() + 14, acento, 12, FontWeight.BOLD, TextAlignment.RIGHT);
    }

    /** Lo que cada sala muestra además de sus fichas. */
    private void contenidoSalas(GraphicsContext g, Paleta p) {
        MapaEstados.Cpu c = diagrama.cpu();
        Rect usuario = MapaEstados.rect(Sala.ORIGEN_USUARIO);
        if (diagrama.sala(Sala.ORIGEN_USUARIO).cantidad() == 0)
            texto(g, "sin ingresos manuales", usuario.x() + usuario.ancho() / 2, usuario.y() + 58, p.apagado(), 9, FontWeight.NORMAL, TextAlignment.CENTER);

        for (MapaEstados.CeldaPcb k : diagrama.pcb()) {                       // cola de procesos: un PCB por celda
            Rect r = k.rect();
            g.setFill(Color.web(k.color()).deriveColor(0, 1, 1, 0.85));
            g.fillRoundRect(r.x(), r.y(), r.ancho(), r.alto(), 6, 6);
            if (k.pid() == seleccionado) {
                g.setStroke(SELECCION);
                g.setLineWidth(2);
                g.strokeRoundRect(r.x() - 1, r.y() - 1, r.ancho() + 2, r.alto() + 2, 6, 6);
            }
            if (r.ancho() >= 22 && r.alto() >= 14)
                texto(g, "P" + k.pid(), r.x() + r.ancho() / 2, r.y() + r.alto() / 2 + 0.5, Color.WHITE, 9, FontWeight.BOLD, TextAlignment.CENTER);
        }

        Rect pl = MapaEstados.rect(Sala.PLANIFICAR), ot = MapaEstados.rect(Sala.OTORGAR_CPU), cpu = MapaEstados.rect(Sala.CPU);
        texto(g, c.politica(), pl.x() + pl.ancho() / 2, pl.y() + pl.alto() - 16, p.texto(), 10, FontWeight.BOLD, TextAlignment.CENTER);
        texto(g, "despachos " + c.despachos(), ot.x() + ot.ancho() / 2, ot.y() + ot.alto() - 16, p.texto(), 10, FontWeight.BOLD, TextAlignment.CENTER);

        boolean kernel = c.modo().equals("KERNEL");                          // chip del modo de la CPU
        Color chip = kernel ? Color.web("#7C5CFF") : Color.web("#2FA84F");
        g.setFill(chip.deriveColor(0, 1, 1, 0.25));
        g.fillRoundRect(cpu.x() + 10, cpu.y() + 40, 76, 18, 9, 9);
        g.setStroke(chip);
        g.setLineWidth(1.2);
        g.strokeRoundRect(cpu.x() + 10, cpu.y() + 40, 76, 18, 9, 9);
        texto(g, c.modo(), cpu.x() + 48, cpu.y() + 49, p.texto(), 10, FontWeight.BOLD, TextAlignment.CENTER);
        if (c.enCpu() == Instantanea.NINGUNO) {
            texto(g, "CPU libre", MapaEstados.PUNTO_CPU.x(), MapaEstados.PUNTO_CPU.y(), p.apagado(), 12, FontWeight.NORMAL, TextAlignment.CENTER);
        } else {
            String q = c.quantum() > 0 ? "quantum " + c.usado() + "/" + c.quantum() : "sin quantum";
            texto(g, q + " · rest " + c.restante(), cpu.x() + cpu.ancho() / 2, cpu.y() + cpu.alto() - 34,
                    Color.web("#E8457C"), 10, FontWeight.BOLD, TextAlignment.CENTER);
            g.setFill(p.fondoBadge());
            g.fillRoundRect(cpu.x() + 20, cpu.y() + cpu.alto() - 24, cpu.ancho() - 40, 8, 8, 8);
            g.setFill(Color.web("#E8457C"));
            g.fillRoundRect(cpu.x() + 20, cpu.y() + cpu.alto() - 24, (cpu.ancho() - 40) * c.avance(), 8, 8, 8);
        }

        for (MapaEstados.Carril k : diagrama.carriles()) carril(g, p, k);
        if (diagrama.porPrioridad()) {                                       // mapa de bits de niveles ocupados
            Rect l = MapaEstados.rect(Sala.LISTOS);
            String bits = diagrama.mapaBitsTexto();
            double x0 = l.x() + l.ancho() - 40 - bits.length() * 13;
            texto(g, "mapa", x0 - 8, l.y() + 16, p.tenue(), 9, FontWeight.BOLD, TextAlignment.RIGHT);
            for (int i = 0; i < bits.length(); i++) {
                boolean on = bits.charAt(i) == '1';
                g.setFill(on ? Color.web("#2FA84F") : p.carril());
                g.fillRoundRect(x0 + i * 13, l.y() + 9, 11, 14, 4, 4);
                g.setStroke(on ? Color.web("#2FA84F") : p.bordeCarril());
                g.setLineWidth(1);
                g.strokeRoundRect(x0 + i * 13, l.y() + 9, 11, 14, 4, 4);
                texto(g, on ? "1" : "0", x0 + i * 13 + 5.5, l.y() + 16.5, on ? Color.WHITE : p.apagado(), 8.5, FontWeight.BOLD, TextAlignment.CENTER);
            }
        }

        diagrama.fichas().stream().filter(MapaEstados.Ficha::siguiente).findFirst().ifPresent(f -> {
            double x = f.destino().x(), y = f.destino().y();                // casilla dorada: el que elegiría el planificador
            g.setFill(p.fondoOro());
            g.fillRoundRect(x - 29, y - 22, 58, 44, 16, 16);
            brillo(g, p.oro(), 1.6, 1, () -> g.strokeRoundRect(x - 29, y - 22, 58, 44, 16, 16));
        });
    }

    private static void carril(GraphicsContext g, Paleta p, MapaEstados.Carril k) {
        Rect r = k.rect();
        boolean ocupado = k.atendiendo() != Instantanea.NINGUNO;
        Color naranja = Color.web("#F28C28");
        g.setFill(ocupado ? naranja.deriveColor(0, 1, 1, 0.16) : p.carril());
        g.fillRoundRect(r.x(), r.y(), r.ancho(), r.alto(), 14, 14);
        brillo(g, ocupado ? naranja : p.bordeCarril(), 1.4, ocupado ? 0.8 : 0, () -> g.strokeRoundRect(r.x(), r.y(), r.ancho(), r.alto(), 14, 14));
        if (k.sala() == Sala.LISTOS) {
            texto(g, k.titulo(), r.x() + 16, r.y() + r.alto() / 2, k.cantidad() > 0 ? Color.web("#2FA84F") : p.apagado(), 10,
                    FontWeight.BOLD, TextAlignment.CENTER);
            return;
        }
        texto(g, k.titulo(), r.x() + 10, r.y() + r.alto() / 2 - (ocupado ? 6 : 0), ocupado ? naranja : p.tenue(), 9.5,
                FontWeight.BOLD, TextAlignment.LEFT);
        if (ocupado) texto(g, "faltan " + k.restante() + " t", r.x() + 10, r.y() + r.alto() / 2 + 7, naranja, 8.5,
                FontWeight.NORMAL, TextAlignment.LEFT);
    }

    private void ficha(GraphicsContext g, Paleta p, MapaEstados.Ficha f, AnimacionArena.Sprite s) {
        Color col = Color.web(f.color());
        double x = s.x(), y = s.y();
        for (AnimacionArena.Rastro r : s.estela()) {                         // estela
            g.setGlobalAlpha(r.alfa() * 0.45);
            g.setFill(col);
            g.fillOval(r.x() - 4, r.y() - 4, 8, 8);
        }
        g.setGlobalAlpha(1);
        boolean quieta = !s.enMovimiento();
        if (f.sala() == Sala.ES && quieta) return;                           // bloqueado: espera tras la puerta de E/S
        boolean mini = f.sala() == Sala.ORIGEN_SO && quieta;
        boolean cpu = f.sala() == Sala.CPU && quieta, dorada = f.siguiente() && quieta;
        double w = cpu ? MapaEstados.FICHA_CPU_ANCHO : mini ? MapaEstados.FICHA_MINI_ANCHO : MapaEstados.FICHA_ANCHO;
        double h = cpu ? MapaEstados.FICHA_CPU_ALTO : mini ? MapaEstados.FICHA_MINI_ALTO : MapaEstados.FICHA_ALTO;
        if (!mini) {
            g.setFill(p.sombra());                                           // sombra en el piso
            g.fillOval(x - 20, y + h / 2 - 2, 40, 9);
        }
        if (cpu) {                                                           // halo de la CPU
            g.setFill(new RadialGradient(0, 0, x, y, 52, false, CycleMethod.NO_CYCLE,
                    new Stop(0, col.deriveColor(0, 1, 1, 0.55)), new Stop(1, col.deriveColor(0, 1, 1, 0))));
            g.fillOval(x - 52, y - 52, 104, 104);
        }
        Color borde = dorada ? p.oro() : f.insignias().contains(Instantanea.Insignia.EXPROPIADO) ? EXPROPIADO : col;
        double radio = mini ? 8 : 18;
        g.setFill(col.deriveColor(0, 1, 1, 0.25));
        g.fillRoundRect(x - w / 2, y - h / 2, w, h, radio, radio);
        brillo(g, borde, mini ? 1.2 : (dorada || cpu) ? 3 : 2, mini ? 0 : (dorada || cpu) ? 1 : 0.5,
                () -> g.strokeRoundRect(x - w / 2, y - h / 2, w, h, radio, radio));
        if (f.pid() == seleccionado) {
            g.setStroke(SELECCION);
            g.setLineWidth(2.5);
            g.strokeRoundRect(x - w / 2 - 5, y - h / 2 - 5, w + 10, h + 10, radio + 4, radio + 4);
        }
        texto(g, "P" + f.pid(), x, y + 1, p.textoFicha(), mini ? 9 : cpu ? 16 : 14, mini ? FontWeight.BOLD : FontWeight.BLACK, TextAlignment.CENTER);
        if (mini) return;
        double bx = x + w / 2 - 2, by = y - h / 2 + 2;                       // insignia con la prioridad
        g.setFill(p.fondoBadge());
        g.fillOval(bx - 9, by - 9, 18, 18);
        g.setStroke(dorada ? p.oro() : borde);
        g.setLineWidth(2);
        g.strokeOval(bx - 9, by - 9, 18, 18);
        texto(g, Integer.toString(f.prioridad()), bx, by + 1, p.texto(), 10, FontWeight.BOLD, TextAlignment.CENTER);
        if (!f.insignias().isEmpty()) {                                      // marca (expropiado, fin de quantum, …)
            g.setFill(MARCA);
            g.fillOval(x - w / 2 - 3, y - h / 2 - 3, 8, 8);
        }
        if (f.sala() == Sala.LISTOS && quieta)                               // ráfaga restante: lo que decide SJF/SRTF
            texto(g, "rest " + f.restante(), x, y + h / 2 + 8, p.tenue(), 8.5, FontWeight.BOLD, TextAlignment.CENTER);
    }

    // ---------------------------------------------------------------- utilidades de dibujo

    /**
     * Traza con halo luminoso: primero el mismo trazo más ancho y transparente (el resplandor) y
     * encima el trazo nítido. Imita el {@code shadowBlur} del diseño sin efectos de JavaFX, que en
     * un canvas con zoom dejan manchas.
     *
     * @param intensidad 0 = sin halo, 1 = halo normal, &gt;1 = destello
     */
    private static void brillo(GraphicsContext g, Color c, double ancho, double intensidad, Runnable trazo) {
        if (intensidad > 0) {
            g.setStroke(c);
            double[][] capas = {{ancho + 9, 0.07}, {ancho + 5, 0.13}, {ancho + 2.5, 0.22}};
            for (double[] capa : capas) {
                g.setLineWidth(capa[0] * Math.min(intensidad, 1.6));
                g.setGlobalAlpha(Math.min(1, capa[1] * intensidad));
                trazo.run();
            }
            g.setGlobalAlpha(1);
        }
        g.setStroke(c);
        g.setLineWidth(ancho);
        trazo.run();
    }

    private static void etiqueta(GraphicsContext g, Paleta p, String t, double xDerecha, double y, Color c) {
        double ancho = t.length() * 6.2 + 12;
        g.setFill(p.fondoBadge());
        g.fillRoundRect(xDerecha - ancho, y - 9, ancho, 18, 9, 9);
        g.setStroke(c);
        g.setLineWidth(1.2);
        g.strokeRoundRect(xDerecha - ancho, y - 9, ancho, 18, 9, 9);
        texto(g, t, xDerecha - ancho / 2, y, c, 9.5, FontWeight.BOLD, TextAlignment.CENTER);
    }

    private static String lista(java.util.List<String> l) { return l.isEmpty() ? "—" : String.join(", ", l); }

    private static void texto(GraphicsContext g, String t, double x, double y, Color c, double tam, FontWeight peso, TextAlignment al) {
        g.setFill(c);
        g.setFont(Font.font(MONO, peso, tam));
        g.setTextAlign(al);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(t, x, y);
    }

    /** Baldosas en damero, como el piso de las salas del diseño. */
    private static void baldosas(GraphicsContext g, Rect r, double lado, Color c1, Color c2) {
        for (double gx = r.x(); gx < r.x() + r.ancho(); gx += lado)
            for (double gy = r.y(); gy < r.y() + r.alto(); gy += lado) {
                g.setFill((((int) ((gx - r.x()) / lado) + (int) ((gy - r.y()) / lado)) & 1) == 1 ? c1 : c2);
                g.fillRect(gx, gy, lado - 1, lado - 1);
            }
    }

    /** Trazado de rectángulo redondeado (para recortar con clip). */
    private static void redondeado(GraphicsContext g, double x, double y, double w, double h, double r) {
        g.beginPath();
        g.moveTo(x + r, y);
        g.arcTo(x + w, y, x + w, y + h, r);
        g.arcTo(x + w, y + h, x, y + h, r);
        g.arcTo(x, y + h, x, y, r);
        g.arcTo(x, y, x + w, y, r);
        g.closePath();
    }
}
