package pe.uni.so.procesos;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import pe.uni.so.aleatorio.Ecuaciones;
import pe.uni.so.aleatorio.Lcg;
import pe.uni.so.excepciones.ParametroInvalidoException;

/**
 * [P9][P10] Módulo de errores: decide, con la ecuación 7, qué procesos del acumulado fallan, en qué
 * instrucción y con qué código. En cada bloque de {@value Ecuaciones#BLOQUE_ERRORES} procesos falla
 * exactamente uno (0,5 %), el de la posición eₖ = 200k + ⌊200U⌋; falla en la instrucción
 * 1 + ⌊U(b − 1)⌋ con E01, E02 o E03 según U.
 *
 * <p>Cada bloque usa su propia secuencia del LCG, derivada de la semilla de la máquina y del número de
 * bloque: el resultado depende solo de la posición del proceso en el acumulado (su PID − 1), no del
 * orden en que el kernel lo consulte ni de la política. Por eso, con la misma carga, falla el mismo
 * proceso en todas las corridas del comparador.
 */
public final class ModuloErrores {

    /** Fallo planificado de un proceso: instrucción (1 a b) en la que ocurre la excepción y su código. */
    public record Fallo(int instruccion, CodigoError codigo) {}

    /** Sorteo de un bloque: posición del errado y las dos U que fijan su instante y su código. */
    private record Sorteo(int posicion, double uInstante, double uCodigo) {}

    private final long semilla;
    private final Map<Integer, Sorteo> bloques = new HashMap<>();

    public ModuloErrores(long semilla) {
        if (semilla < 0) throw new ParametroInvalidoException("La semilla debe ser ≥ 0 (" + semilla + ")");
        this.semilla = semilla;
    }

    /** ¿El proceso en la posición dada del acumulado (desde 0) es el errado de su bloque? */
    public boolean errado(int posicion) {
        if (posicion < 0) throw new ParametroInvalidoException("La posición en el acumulado debe ser ≥ 0 (" + posicion + ")");
        return sorteo(posicion / Ecuaciones.BLOQUE_ERRORES).posicion == posicion;
    }

    /** Fallo del proceso en la posición dada (desde 0) y con ráfaga {@code burst}, si es el errado de su bloque. */
    public Optional<Fallo> fallo(int posicion, int burst) {
        if (!errado(posicion)) return Optional.empty();
        if (burst < 1) throw new ParametroInvalidoException("La ráfaga debe ser ≥ 1 (" + burst + ")");
        Sorteo s = sorteo(posicion / Ecuaciones.BLOQUE_ERRORES);
        return Optional.of(new Fallo(Ecuaciones.instanteFallo(burst, s.uInstante), CodigoError.conU(s.uCodigo)));
    }

    /** Fallo de un proceso según su PID (posición en el acumulado = PID − 1). */
    public Optional<Fallo> fallo(Pcb p) {
        return p.pid < 1 ? Optional.empty() : fallo(p.pid - 1, p.burst);
    }

    /** Posición (desde 0) del errado del bloque k. */
    public int posicionErrado(int k) { return sorteo(k).posicion; }

    /**
     * Semilla del bloque k: SplitMix64 (finalizador estándar) sobre la semilla de la máquina y k, para
     * que semillas o bloques vecinos no den secuencias del LCG parecidas.
     */
    static long mezclar(long semilla, int k) {
        long z = semilla + 0x9E3779B97F4A7C15L * (k + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return (z ^ (z >>> 31)) >>> 32;                                  // 32 bits: el estado del LCG
    }

    private Sorteo sorteo(int k) {
        return bloques.computeIfAbsent(k, b -> {
            Lcg rng = new Lcg(mezclar(semilla, b));                     // una secuencia propia por bloque
            return new Sorteo(Ecuaciones.posicionErrado(b, rng.uniforme()), rng.uniforme(), rng.uniforme());
        });
    }
}
