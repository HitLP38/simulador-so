package pe.uni.so.planificacion;

import pe.uni.so.procesos.Pcb;

/** SJF expropiativo (SRTF): compara tiempo restante; un empate no desaloja al actual. */
public class Srtf extends Sjf {
    @Override public String nombre() { return "SRTF"; }
    @Override public boolean expropia(Pcb candidato, Pcb actual) { return candidato.restante() < actual.restante(); }
}
