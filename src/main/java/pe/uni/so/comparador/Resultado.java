package pe.uni.so.comparador;

/**
 * [P17][M8] Resultado de una corrida del comparador: una configuración con la misma carga que las
 * demás. Los promedios se toman sobre los procesos terminados; los de memoria, sobre los ticks.
 *
 * @param serie               qué se compara en esta fila: la planificación o la memoria
 * @param politica            política de planificación usada
 * @param estrategia          estrategia de asignación de huecos usada
 * @param procesos            procesos de la carga
 * @param terminados          procesos que terminaron (normalmente o abortados por un error)
 * @param conError            [P9][P10] procesos abortados por una excepción (E01–E03): el mismo en todas las corridas
 * @param tFinal              instante en que terminó la corrida (o en que se detuvo)
 * @param esperaMedia         tiempo medio en la cola de listos
 * @param respuestaMedia      primera CPU − llegada, promedio
 * @param retornoMedio        fin − llegada, promedio
 * @param usoCpu              ticks con un proceso en CPU / t, en %
 * @param throughput          procesos terminados por cada 100 ticks
 * @param cambiosContexto     despachos (cambios de contexto)
 * @param expropiaciones      desalojos por la llegada de uno con más derecho (políticas expropiativas)
 * @param finesQuantum        interrupciones del TIMER por fin de quantum (Round Robin)
 * @param comparaciones       huecos revisados por la estrategia al asignar (costo de búsqueda)
 * @param fragExternaMedia    marcos libres fuera del hueco mayor, promedio por tick
 * @param fragExternaMaxima   el peor valor de la fragmentación externa
 * @param fragInternaMediaKib desperdicio dentro de las páginas asignadas, promedio por tick (KiB)
 * @param esperaronMemoria    procesos que no pudieron entrar al menos un tick por falta de memoria (no por el grado)
 * @param esperaMemoriaMedia  ticks sin entrar por falta de memoria de los que esperaron, promedio
 * @param fallosPagina        [E7][M1] fallos de página (0x0E): bajo demanda, al cargar el búfer de cada E/S por DMA
 * @param milisegundos        lo que tardó la corrida en la computadora real
 * @param nota                vacía si terminó bien; si no, por qué se detuvo
 */
public record Resultado(Serie serie, String politica, String estrategia, int procesos, int terminados, int conError, int tFinal,
                        double esperaMedia, double respuestaMedia, double retornoMedio, double usoCpu, double throughput,
                        int cambiosContexto, int expropiaciones, int finesQuantum, long comparaciones, double fragExternaMedia,
                        int fragExternaMaxima, double fragInternaMediaKib, int esperaronMemoria, double esperaMemoriaMedia,
                        int fallosPagina, long milisegundos, String nota) {

    /** Qué se varía en la fila. */
    public enum Serie {
        PLANIFICACION("Planificación"), MEMORIA("Memoria");

        public final String etiqueta;

        Serie(String etiqueta) { this.etiqueta = etiqueta; }

        @Override public String toString() { return etiqueta; }
    }

    /** ¿La corrida terminó con todos los procesos? */
    public boolean completa() { return nota.isEmpty() && terminados == procesos; }

    /** Nombre corto de la configuración: lo que cambia en su serie. */
    public String configuracion() { return serie == Serie.PLANIFICACION ? politica : estrategia; }
}
