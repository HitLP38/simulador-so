package pe.uni.so.procesos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.planificacion.PoliticaPlanificacion;

/**
 * [P4] Las 3 colas de la Sesión 4: trabajos (todos los procesos), listos y una cola por
 * dispositivo. El primero de cada cola de dispositivo es el que el dispositivo atiende.
 * Cada operación valida primero la transición de estado y solo después mueve el proceso.
 */
public class Colas {
    private final List<Pcb> trabajos = new ArrayList<>();
    private final List<Pcb> listos = new ArrayList<>();
    private final Map<Dispositivo, Deque<Pcb>> dispositivos = new EnumMap<>(Dispositivo.class);
    private Pcb enCpu;

    public Colas() {
        for (Dispositivo d : Dispositivo.values()) dispositivos.put(d, new ArrayDeque<>());
    }

    /** Ingreso: el proceso pasa a Listo y entra a trabajos y a listos (momento d). */
    public void admitir(Pcb p) {
        p.pasarA(Estado.LISTO);
        trabajos.add(p);
        listos.add(p);
    }

    /** Despacho: la política elige de listos y el proceso pasa a Ejecutando. */
    public Pcb despachar(PoliticaPlanificacion politica) {
        if (enCpu != null || listos.isEmpty()) return enCpu;
        Pcb elegido = politica.elegir(listos);
        elegido.pasarA(Estado.EJECUTANDO);
        listos.remove(elegido);
        enCpu = elegido;
        return enCpu;
    }

    /** Expropiación o fin de quantum (momento b): el proceso en CPU vuelve al final de listos. */
    public Pcb expropiarEnCpu() {
        Pcb p = exigirEnCpu("expropiar");
        p.pasarA(Estado.LISTO);
        listos.add(p);
        enCpu = null;
        return p;
    }

    /** Pedido de E/S (momento a): el proceso en CPU se bloquea al final de la cola del dispositivo. */
    public void bloquearEnCpu(Dispositivo d) {
        Pcb p = exigirEnCpu("pedir E/S a " + d);
        p.pasarA(Estado.BLOQUEADO);
        dispositivos.get(d).addLast(p);
        enCpu = null;
    }

    /** Fin de E/S (momento c): el primero del dispositivo vuelve al final de listos. */
    public Pcb terminarES(Dispositivo d) {
        Pcb p = dispositivos.get(d).peekFirst();
        if (p == null) throw new EventoInvalidoException("Fin de E/S en " + d + " con la cola vacía");
        p.pasarA(Estado.LISTO);
        dispositivos.get(d).pollFirst();
        listos.add(p);
        return p;
    }

    /** Finalización (momento e): el proceso en CPU termina y la libera. */
    public void terminarEnCpu() {
        exigirEnCpu("terminar").pasarA(Estado.TERMINADO);
        enCpu = null;
    }

    private Pcb exigirEnCpu(String accion) {
        if (enCpu == null) throw new EventoInvalidoException("No se puede " + accion + ": la CPU está libre");
        return enCpu;
    }

    public Pcb enCpu() { return enCpu; }
    public List<Pcb> trabajos() { return Collections.unmodifiableList(trabajos); }
    public List<Pcb> listos() { return Collections.unmodifiableList(listos); }
    public List<Pcb> cola(Dispositivo d) { return List.copyOf(dispositivos.get(d)); }
}
