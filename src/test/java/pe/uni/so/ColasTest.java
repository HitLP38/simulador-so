package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.procesos.Colas;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

/** Capa TEORÍA: taller 2026-2, ejercicio 9 (movimiento entre colas con FCFS). */
class ColasTest {
    private final Fcfs fcfs = new Fcfs();

    private static String ids(List<Pcb> cola) {
        return cola.stream().map(Pcb::toString).collect(Collectors.joining(","));
    }

    private void verificar(Colas c, String cpu, String listos, String disco, String teclado) {
        assertEquals(cpu, c.enCpu() == null ? "" : c.enCpu().toString());
        assertEquals(listos, ids(c.listos()));
        assertEquals(disco, ids(c.cola(Dispositivo.DISCO)));
        assertEquals(teclado, ids(c.cola(Dispositivo.TECLADO)));
    }

    /** Estado inicial del ejercicio: P1 ejecuta; listos [P2, P3]; P4 espera disco; P5 espera teclado. */
    private Colas estadoInicial() {
        Colas c = new Colas();
        Pcb p1 = new Pcb(1, 0, 10), p2 = new Pcb(2, 0, 10), p3 = new Pcb(3, 0, 10);
        Pcb p4 = new Pcb(4, 0, 10), p5 = new Pcb(5, 0, 10);
        c.admitir(p4); c.despachar(fcfs); c.bloquearEnCpu(Dispositivo.DISCO);
        c.admitir(p5); c.despachar(fcfs); c.bloquearEnCpu(Dispositivo.TECLADO);
        c.admitir(p1); c.admitir(p2); c.admitir(p3);
        c.despachar(fcfs);
        return c;
    }

    @Test @DisplayName("[P4][E3][E5][E8] Taller ej. 9: movimiento entre colas con FCFS")
    void ej9() {
        Colas c = estadoInicial();
        verificar(c, "P1", "P2,P3", "P4", "P5");                       // t = 0

        c.bloquearEnCpu(Dispositivo.DISCO); c.despachar(fcfs);          // t = 1: P1 pide disco
        verificar(c, "P2", "P3", "P4,P1", "P5");

        c.terminarES(Dispositivo.DISCO); c.despachar(fcfs);             // t = 2: termina E/S de P4
        verificar(c, "P2", "P3,P4", "P1", "P5");

        c.terminarEnCpu(); c.despachar(fcfs);                           // t = 3: termina P2
        verificar(c, "P3", "P4", "P1", "P5");

        c.terminarES(Dispositivo.TECLADO); c.despachar(fcfs);           // t = 4: termina E/S de P5
        verificar(c, "P3", "P4,P5", "P1", "");

        c.bloquearEnCpu(Dispositivo.TECLADO); c.despachar(fcfs);        // t = 5: P3 pide teclado
        verificar(c, "P4", "P5", "P1", "P3");
    }

    @Test @DisplayName("[P4] La cola de trabajos conserva todos los procesos, incluso los terminados")
    void trabajosConservaTodos() {
        Colas c = estadoInicial();
        c.terminarEnCpu();
        assertEquals(5, c.trabajos().size());
        assertEquals(Estado.TERMINADO, c.trabajos().get(2).estado());
        assertNull(c.enCpu());
    }

    @Test @DisplayName("[P4][E1] Existen 5 colas de dispositivo, una por dispositivo")
    void cincoDispositivos() {
        Colas c = new Colas();
        assertEquals(5, Dispositivo.values().length);
        for (Dispositivo d : Dispositivo.values()) assertEquals(0, c.cola(d).size());
    }
}
