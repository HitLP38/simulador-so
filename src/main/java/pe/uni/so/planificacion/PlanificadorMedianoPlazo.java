package pe.uni.so.planificacion;

import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.procesos.Pcb;

/**
 * [P5] Planificador de mediano plazo: suspende (swap-out) un proceso Listo o Bloqueado para
 * liberar memoria y lo reincorpora (swap-in) cuando vuelve a caber (taller 2026-2, ej. 16 b y c).
 * La suspensión es un atributo del proceso, no un sexto estado (decisión 8).
 */
public class PlanificadorMedianoPlazo {
    private final PlanificadorLargoPlazo memoria;

    public PlanificadorMedianoPlazo(PlanificadorLargoPlazo memoria) { this.memoria = memoria; }

    public void suspender(Pcb p) {
        if (!memoria.residente(p)) throw new EventoInvalidoException(p + " no fue admitido por este planificador");
        p.suspender();
        memoria.liberar(p);
    }

    /** Reincorpora el proceso si cabe; si no, sigue suspendido. */
    public boolean reanudar(Pcb p) {
        if (!memoria.cabe(p)) return false;
        p.reanudar();
        memoria.reservar(p);
        return true;
    }
}
