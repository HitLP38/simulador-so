package pe.uni.so.planificacion;

import java.util.Comparator;
import java.util.List;
import pe.uni.so.procesos.Pcb;

/** Prioridades (menor número = mayor prioridad), en contexto expropiativo o no. */
public class Prioridad implements PoliticaPlanificacion {
    private static final Comparator<Pcb> MAYOR_PRIORIDAD = Comparator.comparingInt((Pcb p) -> p.prioridad)
            .thenComparingInt(p -> p.llegada).thenComparingInt(p -> p.pid);
    private final boolean expropiativa;

    public Prioridad(boolean expropiativa) { this.expropiativa = expropiativa; }

    public String nombre() { return expropiativa ? "Prioridad (E)" : "Prioridad (NE)"; }
    public Pcb elegir(List<Pcb> listos) { return listos.stream().min(MAYOR_PRIORIDAD).orElseThrow(); }
    @Override public boolean expropia(Pcb candidato, Pcb actual) {
        return expropiativa && candidato.prioridad < actual.prioridad;
    }
}
