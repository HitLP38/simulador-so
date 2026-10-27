package pe.uni.so.procesos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.planificacion.PoliticaPlanificacion;

/**
 * [P4] Las 3 colas de la Sesión 4: trabajos (todos los procesos), listos y una cola por
 * dispositivo. El primero de cada cola de dispositivo es el que el dispositivo atiende.
 * Toda entrada y salida de una cola va acompañada de su transición de estado.
 */
public class Colas {
    private final List<Pcb> trabajos = new ArrayList<>();
    private final List<Pcb> listos = new ArrayList<>();
    private final Map<Dispositivo, Deque<Pcb>> dispositivos = new EnumMap<>(Dispositivo.class);
    private Pcb enCpu;

    public Colas() {
        for (Dispositivo d : Dispositivo.values()) dispositivos.put(d, new ArrayDeque<>());
    }

    /** Ingreso: el proceso entra a la cola de trabajos y pasa a Listo (momento d). */
    public void admitir(Pcb p) {
        trabajos.add(p);
        p.pasarA(Estado.LISTO);
        listos.add(p);
    }

    /** Despacho: la política elige de listos y el proceso pasa a Ejecutando. */
    public Pcb despachar(PoliticaPlanificacion politica) {
        if (enCpu != null || listos.isEmpty()) return enCpu;
        enCpu = politica.elegir(listos);
        listos.remove(enCpu);
        enCpu.pasarA(Estado.EJECUTANDO);
        return enCpu;
    }

    /** Pedido de E/S (momento a): el proceso en CPU se bloquea al final de la cola del dispositivo. */
    public void bloquearEnCpu(Dispositivo d) {
        Pcb p = enCpu;
        p.pasarA(Estado.BLOQUEADO);
        dispositivos.get(d).addLast(p);
        enCpu = null;
    }

    /** Fin de E/S (momento c): el primero del dispositivo vuelve al final de listos. */
    public Pcb terminarES(Dispositivo d) {
        Pcb p = dispositivos.get(d).pollFirst();
        p.pasarA(Estado.LISTO);
        listos.add(p);
        return p;
    }

    /** Finalización (momento e): el proceso en CPU termina y la libera. */
    public void terminarEnCpu() {
        enCpu.pasarA(Estado.TERMINADO);
        enCpu = null;
    }

    public Pcb enCpu() { return enCpu; }
    public List<Pcb> trabajos() { return Collections.unmodifiableList(trabajos); }
    public List<Pcb> listos() { return Collections.unmodifiableList(listos); }
    public List<Pcb> cola(Dispositivo d) { return List.copyOf(dispositivos.get(d)); }
}
