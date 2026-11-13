package pe.uni.so.sesion;

import java.util.List;
import pe.uni.so.excepciones.ParametroInvalidoException;

/**
 * [G5] Control de reproducción de la línea de tiempo (▶/⏸ y velocidad), sin JavaFX. La
 * interfaz le pasa el tiempo real transcurrido entre cuadros y recibe cuántos ticks simular;
 * la fracción que sobra se acumula para el cuadro siguiente, así la velocidad es exacta aunque
 * los cuadros no lleguen a intervalos regulares.
 */
public class Reproductor {

    /** Velocidades ofrecidas, en ticks por segundo. */
    public static final List<Integer> VELOCIDADES = List.of(1, 2, 5, 10, 20, 50, 100);
    public static final int VELOCIDAD_INICIAL = 5;
    /** Tope de ticks por cuadro: tras una pausa larga del equipo no se salta media simulación. */
    public static final int MAXIMO_POR_CUADRO = 25;

    private boolean reproduciendo;
    private int velocidad = VELOCIDAD_INICIAL;
    private double acumulado;

    public boolean reproduciendo() { return reproduciendo; }

    public void reproducir() {
        reproduciendo = true;
        acumulado = 0;
    }

    public void pausar() {
        reproduciendo = false;
        acumulado = 0;
    }

    /** Cambia entre reproducir y pausar; devuelve el nuevo estado. */
    public boolean alternar() {
        if (reproduciendo) pausar();
        else reproducir();
        return reproduciendo;
    }

    public int velocidad() { return velocidad; }

    public void velocidad(int ticksPorSegundo) {
        if (!VELOCIDADES.contains(ticksPorSegundo))
            throw new ParametroInvalidoException("Velocidad no ofrecida: " + ticksPorSegundo + " ticks/s (opciones: " + VELOCIDADES + ")");
        velocidad = ticksPorSegundo;
    }

    /**
     * Ticks a simular tras {@code nanos} nanosegundos de tiempo real.
     *
     * @return 0 si está en pausa; como máximo {@link #MAXIMO_POR_CUADRO}
     */
    public int ticks(long nanos) {
        if (!reproduciendo || nanos <= 0) return 0;
        acumulado += velocidad * (nanos / 1e9);
        int n = (int) Math.floor(acumulado);
        if (n >= MAXIMO_POR_CUADRO) {
            acumulado = 0;
            return MAXIMO_POR_CUADRO;
        }
        acumulado -= n;
        return n;
    }

    /** «10 ticks/s», como se muestra en la lista. */
    public static String etiqueta(int ticksPorSegundo) { return ticksPorSegundo + " ticks/s"; }
}
