package pe.uni.so.planificacion;

import java.util.HashSet;
import java.util.Set;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.procesos.Colas;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

/**
 * [P5] Planificador de largo plazo: decide qué procesos nuevos se admiten según la memoria
 * libre y el grado máximo de multiprogramación (Sesión 6; taller 2026-2, ej. 16 c).
 * Lleva la cuenta de la memoria residente que comparte con el de mediano plazo.
 *
 * <p>Dos modos: con unidades de memoria (taller ej. 16) o, [I6], con un {@link GestorMemoria}
 * real: admite solo si hay marcos libres para todas las páginas del proceso y, al admitirlo,
 * le asigna marcos y tabla de páginas.
 */
public class PlanificadorLargoPlazo {
    private final GestorMemoria gestor;                    // null: modo por unidades
    private final int memoriaTotal;
    private final int gradoMaximo;
    private final Set<Pcb> residentes = new HashSet<>();
    private int usada;

    public PlanificadorLargoPlazo(int memoriaTotal, int gradoMaximo) {
        if (memoriaTotal <= 0 || gradoMaximo <= 0)
            throw new ParametroInvalidoException("Memoria y grado de multiprogramación deben ser > 0");
        this.gestor = null;
        this.memoriaTotal = memoriaTotal;
        this.gradoMaximo = gradoMaximo;
    }

    public PlanificadorLargoPlazo(int memoriaTotal) { this(memoriaTotal, Integer.MAX_VALUE); }

    /** [I6] Admisión por marcos reales. */
    public PlanificadorLargoPlazo(GestorMemoria gestor, int gradoMaximo) {
        if (gestor == null) throw new ParametroInvalidoException("Falta el gestor de memoria");
        if (gradoMaximo <= 0) throw new ParametroInvalidoException("El grado de multiprogramación debe ser > 0");
        this.gestor = gestor;
        this.memoriaTotal = gestor.mapa().total();
        this.gradoMaximo = gradoMaximo;
    }

    public PlanificadorLargoPlazo(GestorMemoria gestor) { this(gestor, Integer.MAX_VALUE); }

    /** ¿Cabe el proceso ahora mismo, completamente residente? */
    public boolean cabe(Pcb p) {
        if (gestor != null) {
            EspacioProceso e = p.espacio();
            if (e == null) throw new EventoInvalidoException(p + " no tiene espacio de proceso para pedir marcos");
            if (!gestor.podriaCaber(e))
                throw new ParametroInvalidoException(p + " necesita " + e.paginasTotales() + " marcos y la memoria tiene " + memoriaTotal);
            return gestor.cabe(e) && residentes.size() < gradoMaximo;
        }
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
        if (gestor == null) { usada += p.memoria; return; }
        try {
            gestor.asignar(p.pid, p.espacio());
        } catch (RuntimeException ex) {
            residentes.remove(p);
            throw ex;
        }
    }

    /** Libera la memoria de un proceso que terminó o que fue suspendido; nunca la de uno que aún puede ejecutar. */
    public void liberar(Pcb p) {
        if (p.estado() != Estado.TERMINADO && !p.suspendido())
            throw new EventoInvalidoException("No se libera la memoria de " + p + ": está " + p.estado() + " y no suspendido");
        if (!residentes.remove(p)) throw new EventoInvalidoException(p + " no está residente");
        if (gestor == null) usada -= p.memoria;
        else gestor.liberar(p.pid);
    }

    public boolean residente(Pcb p) { return residentes.contains(p); }

    /** Unidades usadas, o marcos ocupados con gestor de memoria. */
    public int usada() { return gestor == null ? usada : memoriaTotal - gestor.mapa().libres(); }
    public int libre() { return memoriaTotal - usada(); }
    public GestorMemoria gestor() { return gestor; }
    public int residentes() { return residentes.size(); }
}
