package pe.uni.so.comparador;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * [P17][M8][G1] Columnas numéricas de la comparación, compartidas por la tabla de la interfaz y
 * el CSV: etiqueta, decimales, si un valor menor es mejor y de qué serie es propia (la otra serie
 * también la muestra, pero la mejor celda solo se marca en su serie).
 */
public enum Columna {
    T_FINAL("t final", 0, true, null, r -> r.tFinal()),
    ESPERA("Espera media", 2, true, Resultado.Serie.PLANIFICACION, Resultado::esperaMedia),
    RESPUESTA("Respuesta media", 2, true, Resultado.Serie.PLANIFICACION, Resultado::respuestaMedia),
    RETORNO("Retorno medio", 2, true, null, Resultado::retornoMedio),
    USO_CPU("Uso de CPU (%)", 2, false, Resultado.Serie.PLANIFICACION, Resultado::usoCpu),
    THROUGHPUT("Terminados / 100 ticks", 3, false, Resultado.Serie.PLANIFICACION, Resultado::throughput),
    CAMBIOS("Cambios de contexto", 0, true, Resultado.Serie.PLANIFICACION, r -> r.cambiosContexto()),
    EXPROPIACIONES("Expropiaciones", 0, true, Resultado.Serie.PLANIFICACION, r -> r.expropiaciones()),
    FINES_QUANTUM("Fines de quantum", 0, true, Resultado.Serie.PLANIFICACION, r -> r.finesQuantum()),
    COMPARACIONES("Comparaciones de búsqueda", 0, true, Resultado.Serie.MEMORIA, r -> r.comparaciones()),
    FRAG_EXTERNA("Frag. externa media (marcos)", 2, true, Resultado.Serie.MEMORIA, Resultado::fragExternaMedia),
    FRAG_EXTERNA_MAX("Frag. externa máx. (marcos)", 0, true, Resultado.Serie.MEMORIA, r -> r.fragExternaMaxima()),
    FRAG_INTERNA("Frag. interna media (KiB)", 2, true, Resultado.Serie.MEMORIA, Resultado::fragInternaMediaKib),
    ESPERARON_MEMORIA("Esperaron memoria", 0, true, Resultado.Serie.MEMORIA, r -> r.esperaronMemoria()),
    ESPERA_MEMORIA("Espera por memoria (media)", 2, true, Resultado.Serie.MEMORIA, Resultado::esperaMemoriaMedia),
    FALLOS_PAGINA("Fallos de página", 0, true, null, r -> r.fallosPagina()),
    CON_ERROR("Con error", 0, true, null, r -> r.conError()),
    MILISEGUNDOS("Tiempo real (ms)", 0, true, null, r -> r.milisegundos());

    public final String etiqueta;
    public final int decimales;
    public final boolean menorEsMejor;
    /** Serie en la que se marca la mejor celda; null = en ambas. Tiempo real y «Con error» no se marcan. */
    public final Resultado.Serie serie;
    private final ToDoubleFunction<Resultado> valor;

    Columna(String etiqueta, int decimales, boolean menorEsMejor, Resultado.Serie serie, ToDoubleFunction<Resultado> valor) {
        this.etiqueta = etiqueta;
        this.decimales = decimales;
        this.menorEsMejor = menorEsMejor;
        this.serie = serie;
        this.valor = valor;
    }

    public double valor(Resultado r) { return valor.applyAsDouble(r); }

    /** ¿Se marca la mejor celda de esta columna en la serie dada? */
    public boolean seCompara(Resultado.Serie s) { return comparable() && (serie == null || serie == s); }

    /**
     * ¿Tiene sentido marcar la mejor celda? El tiempo real depende de la computadora y «Con error» es el mismo
     * en todas las corridas (el módulo de errores elige por la carga, no por la configuración).
     */
    public boolean comparable() { return this != MILISEGUNDOS && this != CON_ERROR; }

    /**
     * El mejor valor de la columna entre las corridas completas de la serie, o NaN si no hay
     * ninguna o si dos o más comparten ese mejor valor (solo se marca un ganador único).
     */
    public double mejor(List<Resultado> resultados, Resultado.Serie s) {
        if (!seCompara(s)) return Double.NaN;
        double mejor = Double.NaN;
        int empatan = 0;
        for (Resultado r : resultados) {
            if (r.serie() != s || !r.completa()) continue;
            double v = valor(r);
            if (Double.isNaN(mejor) || (menorEsMejor ? v < mejor - 1e-9 : v > mejor + 1e-9)) { mejor = v; empatan = 1; }
            else if (Math.abs(v - mejor) < 1e-9) empatan++;
        }
        return empatan == 1 ? mejor : Double.NaN;
    }

    /** ¿Esta celda es la mejor de su columna en su serie? */
    public boolean esMejor(Resultado r, List<Resultado> resultados) {
        if (!r.completa()) return false;
        double m = mejor(resultados, r.serie());
        return !Double.isNaN(m) && Math.abs(valor(r) - m) < 1e-9;
    }
}
