package pe.uni.so.planificacion;

import java.util.List;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

/**
 * [P17] Indicadores con las definiciones del taller 2026-2 (tabla de convenciones):
 * retorno = fin − llegada; espera = tiempo en la cola de listos; respuesta = primera CPU − llegada;
 * utilización útil = CPU útil / intervalo × 100; throughput = finalizados / intervalo.
 */
public final class Metricas {
    private Metricas() { }

    public static double retornoMedio(List<Pcb> procesos) {
        return procesos.stream().mapToInt(Pcb::retorno).average().orElse(0);
    }

    public static double esperaMedia(List<Pcb> procesos) {
        return procesos.stream().mapToInt(Pcb::espera).average().orElse(0);
    }

    public static double respuestaMedia(List<Pcb> procesos) {
        return procesos.stream().mapToInt(Pcb::respuesta).average().orElse(0);
    }

    /** Porcentaje del intervalo observado en que la CPU hizo trabajo útil. */
    public static double utilizacionUtil(List<Pcb> procesos, int intervalo) {
        validar(intervalo);
        int cpuUtil = procesos.stream().mapToInt(Pcb::cpu).sum();
        return 100.0 * cpuUtil / intervalo;
    }

    /** Procesos finalizados dentro del intervalo, por unidad de tiempo. */
    public static double throughput(List<Pcb> procesos, int intervalo) {
        validar(intervalo);
        long finalizados = procesos.stream()
                .filter(p -> p.estado() == Estado.TERMINADO && p.fin() <= intervalo).count();
        return (double) finalizados / intervalo;
    }

    private static void validar(int intervalo) {
        if (intervalo <= 0) throw new ParametroInvalidoException("El intervalo observado debe ser > 0: " + intervalo);
    }
}
