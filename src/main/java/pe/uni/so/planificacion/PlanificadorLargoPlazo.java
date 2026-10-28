package pe.uni.so.planificacion;

import java.util.HashSet;
import java.util.Set;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.procesos.Colas;
import pe.uni.so.procesos.Pcb;

/**
 * [P5] Planificador de largo plazo: decide qué procesos nuevos se admiten según la memoria
 * libre y el grado máximo de multiprogramación (Sesión 6; taller 2026-2, ej. 16 c).
 * Lleva la cuenta de la memoria residente que comparte con el de mediano plazo.
 */
public class PlanificadorLargoPlazo {
    private final int memoriaTotal;
    private final int gradoMaximo;
    private final Set<Pcb> residentes = new HashSet<>();
    private int usada;

    public PlanificadorLargoPlazo(int memoriaTotal, int gradoMaximo) {
        if (memoriaTotal <= 0 || gradoMaximo <= 0)
            throw new ParametroInvalidoException("Memoria y grado de multiprogramación deben ser > 0");
        this.memoriaTotal = memoriaTotal;
        this.gradoMaximo = gradoMaximo;
    }

    public PlanificadorLargoPlazo(int memoriaTotal) { this(memoriaTotal, Integer.MAX_VALUE); }

    /** ¿Cabe el proceso ahora mismo, completamente residente? */
    public boolean cabe(Pcb p) {
        if (p.memoria > memoriaTotal)
            throw new ParametroInvalidoException(p + " pide " + p.memoria + " y la memoria total es " + memoriaTotal);
        return p.memoria <= libre() && residentes.size() < gradoMaximo;
    }

    /** Admisión (momento d): si cabe, reserva su memoria y lo pasa a Listo; si no, queda en Nuevo. */
    public boolean admitir(Pcb p, Colas colas) {
        if (!cabe(p)) return false;
        colas.admitir(p);
        reservar(p);
        return true;
    }

    void reservar(Pcb p) {
        if (!residentes.add(p)) throw new EventoInvalidoException(p + " ya está residente");
        usada += p.memoria;
    }

    /** Libera la memoria de un proceso que terminó o que fue suspendido. */
    public void liberar(Pcb p) {
        if (!residentes.remove(p)) throw new EventoInvalidoException(p + " no está residente");
        usada -= p.memoria;
    }

    public int usada() { return usada; }
    public int libre() { return memoriaTotal - usada; }
    public int residentes() { return residentes.size(); }
}
