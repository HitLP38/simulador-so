package pe.uni.so.planificacion;

import java.util.Comparator;
import java.util.List;
import pe.uni.so.procesos.Pcb;

/** SJF no expropiativo. Empates: llegada más antigua y luego menor PID (taller 2026-2). */
public class Sjf implements PoliticaPlanificacion {
    static final Comparator<Pcb> MENOR_RESTANTE = Comparator.comparingInt(Pcb::restante)
            .thenComparingInt(p -> p.llegada).thenComparingInt(p -> p.pid);

    public String nombre() { return "SJF"; }
    public Pcb elegir(List<Pcb> listos) { return listos.stream().min(MENOR_RESTANTE).orElseThrow(); }
}
