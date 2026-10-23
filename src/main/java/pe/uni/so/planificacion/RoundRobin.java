package pe.uni.so.planificacion;

import java.util.List;
import pe.uni.so.procesos.Pcb;

/** Round Robin: cola circular; el quantum se puede cambiar en plena simulación [P7]. */
public class RoundRobin implements PoliticaPlanificacion {
    private int quantum;

    public RoundRobin(int quantum) { setQuantum(quantum); }

    public void setQuantum(int quantum) {
        if (quantum < 1) throw new IllegalArgumentException("El quantum debe ser >= 1");
        this.quantum = quantum;
    }

    public String nombre() { return "RR q=" + quantum; }
    public Pcb elegir(List<Pcb> listos) { return listos.get(0); }
    @Override public int quantum() { return quantum; }
}
