package pe.uni.so.sesion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.sesion.MapaEstados.Punto;
import pe.uni.so.sesion.MapaEstados.Sala;

/**
 * [G5] Movimiento de las fichas de la arena, sin JavaFX. Cada proceso es un «sprite» con su
 * posición actual; al llegar una foto nueva, el que cambió de sala recibe una ruta por los
 * pasillos ({@link MapaEstados#ruta}) y el que solo cambió de lugar en su sala, una línea recta.
 * El lienzo llama a {@link #avanzar(double)} en cada cuadro y dibuja las posiciones y estelas.
 *
 * <p>Si la foto nueva no es la siguiente (retroceder, saltar con la línea de tiempo, reiniciar),
 * las fichas se colocan directamente en su lugar, sin caminar.
 */
public class AnimacionArena {

    /** Velocidad de las fichas por defecto, en unidades del mundo por segundo. */
    public static final double VELOCIDAD_INICIAL = 700;
    /** Saltos de hasta este número de ticks hacia adelante se animan; los demás se colocan. */
    public static final int SALTO_ANIMADO = 5;
    static final int LARGO_ESTELA = 10;
    static final double DESVANECER_ESTELA = 2.2;          // alfa perdido por segundo

    /** Punto de la estela con su opacidad (1 → 0). */
    public record Rastro(double x, double y, double alfa) {}

    /** Ficha en movimiento: posición actual, sala hacia la que va, puntos por recorrer y estela. */
    public static final class Sprite {
        private final int pid;
        private double x, y;
        private Sala sala;
        private final Deque<Punto> ruta = new ArrayDeque<>();
        private final List<Rastro> estela = new ArrayList<>();

        Sprite(int pid, Punto p, Sala sala) {
            this.pid = pid;
            this.x = p.x();
            this.y = p.y();
            this.sala = sala;
        }

        public int pid() { return pid; }
        public double x() { return x; }
        public double y() { return y; }
        public Sala sala() { return sala; }
        public boolean enMovimiento() { return !ruta.isEmpty(); }
        public List<Punto> ruta() { return List.copyOf(ruta); }
        public List<Rastro> estela() { return List.copyOf(estela); }
    }

    private final Map<Integer, Sprite> sprites = new LinkedHashMap<>();
    private double velocidad = VELOCIDAD_INICIAL;
    private int tUltimo = Integer.MIN_VALUE;

    public double velocidad() { return velocidad; }

    public void velocidad(double unidadesPorSegundo) {
        if (!(unidadesPorSegundo > 0)) throw new ParametroInvalidoException("La velocidad debe ser positiva (" + unidadesPorSegundo + ")");
        velocidad = unidadesPorSegundo;
    }

    /**
     * Recibe la disposición de una foto nueva. Si es 1 a {@link #SALTO_ANIMADO} ticks después de
     * la anterior, las fichas caminan; si no, se colocan en su lugar.
     */
    public void mostrar(MapaEstados.Diagrama d) {
        if (d == null) throw new ParametroInvalidoException("Falta la disposición de la arena");
        int salto = d.t() - tUltimo;
        boolean animar = tUltimo != Integer.MIN_VALUE && salto >= 1 && salto <= SALTO_ANIMADO;
        tUltimo = d.t();
        Set<Integer> presentes = d.fichas().stream().map(MapaEstados.Ficha::pid).collect(Collectors.toSet());
        sprites.keySet().removeIf(pid -> !presentes.contains(pid));
        for (MapaEstados.Ficha f : d.fichas()) {
            Sprite s = sprites.get(f.pid());
            if (s == null || !animar) {
                sprites.put(f.pid(), new Sprite(f.pid(), f.destino(), f.sala()));
                continue;
            }
            Punto actual = new Punto(s.x, s.y);
            Punto destinoPrevio = s.ruta.isEmpty() ? actual : s.ruta.peekLast();
            if (s.sala != f.sala()) {
                s.ruta.clear();
                s.ruta.addAll(MapaEstados.ruta(s.sala, f.sala(), actual, f.destino()));
                s.sala = f.sala();
            } else if (destinoPrevio.distancia(f.destino()) > 0.5) {
                s.ruta.clear();
                s.ruta.add(f.destino());                          // se reacomodó dentro de su sala
            }
        }
    }

    /**
     * Mueve las fichas {@code segundos} de tiempo real y desvanece las estelas.
     *
     * @return true si alguna ficha sigue caminando o queda alguna estela (hay que redibujar)
     */
    public boolean avanzar(double segundos) {
        if (segundos <= 0) return hayMovimiento();
        for (Sprite s : sprites.values()) {
            double recorrido = velocidad * segundos;
            boolean movio = false;
            while (recorrido > 0 && !s.ruta.isEmpty()) {
                Punto w = s.ruta.peekFirst();
                double d = Math.hypot(w.x() - s.x, w.y() - s.y);
                if (d <= recorrido) {
                    s.x = w.x();
                    s.y = w.y();
                    s.ruta.removeFirst();
                    recorrido -= d;
                } else {
                    s.x += (w.x() - s.x) / d * recorrido;
                    s.y += (w.y() - s.y) / d * recorrido;
                    recorrido = 0;
                }
                movio = true;
            }
            List<Rastro> viejas = new ArrayList<>(s.estela.size());
            for (Rastro r : s.estela) {
                double a = r.alfa() - DESVANECER_ESTELA * segundos;
                if (a > 0) viejas.add(new Rastro(r.x(), r.y(), a));
            }
            s.estela.clear();
            s.estela.addAll(viejas);
            if (movio) {
                s.estela.add(new Rastro(s.x, s.y, 1));
                while (s.estela.size() > LARGO_ESTELA) s.estela.remove(0);
            }
        }
        return hayMovimiento();
    }

    /** Coloca todas las fichas en su destino (por ejemplo, al pausar o al ajustar). */
    public void terminarRecorridos() {
        for (Sprite s : sprites.values()) {
            if (!s.ruta.isEmpty()) {
                Punto fin = s.ruta.peekLast();
                s.x = fin.x();
                s.y = fin.y();
                s.ruta.clear();
            }
            s.estela.clear();
        }
    }

    public boolean hayMovimiento() {
        return sprites.values().stream().anyMatch(s -> s.enMovimiento() || !s.estela.isEmpty());
    }

    public Collection<Sprite> sprites() { return List.copyOf(sprites.values()); }

    public Optional<Sprite> sprite(int pid) { return Optional.ofNullable(sprites.get(pid)); }

    /** PID de la ficha bajo el punto (mx, my) del mundo, según dónde está ahora. */
    public Optional<Integer> pidEn(double mx, double my, double ancho, double alto) {
        List<Sprite> lista = new ArrayList<>(sprites.values());
        for (int i = lista.size() - 1; i >= 0; i--) {                // la dibujada encima gana
            Sprite s = lista.get(i);
            if (Math.abs(mx - s.x) <= ancho / 2 && Math.abs(my - s.y) <= alto / 2) return Optional.of(s.pid);
        }
        return Optional.empty();
    }
}
