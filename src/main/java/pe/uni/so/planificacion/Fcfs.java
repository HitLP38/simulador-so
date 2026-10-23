package pe.uni.so.planificacion;

import java.util.List;
import pe.uni.so.procesos.Pcb;

/** FCFS: el primero que entró a la cola de listos. No expropiativo por definición. */
public class Fcfs implements PoliticaPlanificacion {
    public String nombre() { return "FCFS"; }
    public Pcb elegir(List<Pcb> listos) { return listos.get(0); }
}
