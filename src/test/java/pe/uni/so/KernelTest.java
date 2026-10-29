package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.motor.Kernel;
import pe.uni.so.motor.Momento;
import pe.uni.so.planificacion.*;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

class KernelTest {
    private static final double D = 0.01;

    private static Kernel correr(PoliticaPlanificacion politica, Pcb... procesos) {
        Kernel k = new Kernel(politica, List.of(procesos));
        k.ejecutarTodo();
        return k;
    }

    private static Kernel pasos(Kernel k, int n) {
        for (int i = 0; i < n; i++) k.paso();
        return k;
    }

    private static Pcb[] ej22() { return new Pcb[] {new Pcb(1, 0, 8), new Pcb(2, 1, 4), new Pcb(3, 2, 2), new Pcb(4, 3, 1)}; }

    @Test @DisplayName("[P6] El Kernel reproduce los ejercicios 22, 23, 24 y 26 del taller 2026-2")
    void mismosResultadosQueElTaller() {
        Kernel sjf = correr(new Sjf(), ej22());
        assertEquals(9.25, Metricas.retornoMedio(sjf.procesos()), D);
        assertEquals(5.50, Metricas.esperaMedia(sjf.procesos()), D);

        Kernel srtf = correr(new Srtf(), ej22());
        assertEquals(6.50, Metricas.retornoMedio(srtf.procesos()), D);
        assertEquals(2.75, Metricas.esperaMedia(srtf.procesos()), D);
        assertEquals(0.25, Metricas.respuestaMedia(srtf.procesos()), D);
        assertEquals(2, srtf.desalojos());

        Kernel prio = correr(new Prioridad(true), new Pcb(1, 0, 7, 3), new Pcb(2, 2, 4, 1), new Pcb(3, 3, 2, 2), new Pcb(4, 5, 1, 1));
        assertEquals(6.50, Metricas.retornoMedio(prio.procesos()), D);
        assertEquals(3.00, Metricas.esperaMedia(prio.procesos()), D);
        assertEquals(1.25, Metricas.respuestaMedia(prio.procesos()), D);

        Kernel rr = correr(new RoundRobin(2), new Pcb(1, 0, 7), new Pcb(2, 0, 4), new Pcb(3, 0, 2));
        assertEquals("1 1 2 2 3 3 1 1 2 2 1 1 1", rr.gantt());
        assertEquals(9.67, Metricas.retornoMedio(rr.procesos()), D);
    }

    @Test @DisplayName("[P6] Momento a: pedir E/S bloquea al proceso y el planificador elige a otro")
    void momentoA() {
        Pcb p1 = new Pcb(1, 0, 5), p2 = new Pcb(2, 0, 3);
        Kernel k = pasos(new Kernel(new Fcfs(), List.of(p1, p2)), 2);
        k.pedirES(Dispositivo.DISCO);

        assertEquals(Estado.BLOQUEADO, p1.estado());
        assertEquals(List.of(p1), k.colas().cola(Dispositivo.DISCO));
        assertNull(k.colas().enCpu());
        k.paso();
        assertEquals(p2, k.colas().enCpu());
        assertEquals("1 1 2", k.gantt());
        assertEquals(2, k.eventos(Momento.A).get(0).t());
    }

    @Test @DisplayName("[P6] Momento c no expropiativo: el que vuelve de E/S solo se encola")
    void momentoCNoExpropiativo() {
        Pcb p1 = new Pcb(1, 0, 6, 3), p2 = new Pcb(2, 0, 4, 1);
        Kernel k = pasos(new Kernel(new Prioridad(false), List.of(p1, p2)), 1);   // P2 (prioridad 1) ejecuta
        k.pedirES(Dispositivo.TECLADO);
        pasos(k, 1);                                                              // P1 ejecuta
        k.finES(Dispositivo.TECLADO);
        pasos(k, 1);

        assertEquals(p1, k.colas().enCpu());
        assertEquals(List.of(p2), k.colas().listos());
        assertEquals("2 1 1", k.gantt());
        assertEquals(0, k.desalojos());
    }

    @Test @DisplayName("[P6] Momento c expropiativo: el que vuelve de E/S con más prioridad quita la CPU")
    void momentoCExpropiativo() {
        Pcb p1 = new Pcb(1, 0, 6, 3), p2 = new Pcb(2, 0, 4, 1);
        Kernel k = pasos(new Kernel(new Prioridad(true), List.of(p1, p2)), 1);
        k.pedirES(Dispositivo.TECLADO);
        pasos(k, 1);
        k.finES(Dispositivo.TECLADO);
        pasos(k, 1);

        assertEquals(p2, k.colas().enCpu());
        assertEquals(Estado.LISTO, p1.estado());
        assertEquals("2 1 2", k.gantt());
        assertEquals(1, k.desalojos());
        assertTrue(k.eventos(Momento.B).get(0).detalle().contains("expropiado por P2"));
    }

    @Test @DisplayName("[P6] Momento d: la llegada de un proceso más corto solo expropia con SRTF")
    void momentoD() {
        Kernel sjf = correr(new Sjf(), new Pcb(1, 0, 6), new Pcb(2, 2, 1));
        assertEquals("1 1 1 1 1 1 2", sjf.gantt());
        assertEquals(0, sjf.desalojos());

        Kernel srtf = correr(new Srtf(), new Pcb(1, 0, 6), new Pcb(2, 2, 1));
        assertEquals("1 1 2 1 1 1 1", srtf.gantt());
        assertEquals(1, srtf.desalojos());
        assertEquals(2, srtf.eventos(Momento.D).size());
    }

    @Test @DisplayName("[P6] Momento b: el timer expropia al agotar el quantum, salvo que el proceso esté solo")
    void momentoB() {
        Kernel solo = correr(new RoundRobin(2), new Pcb(1, 0, 5));
        assertEquals("1 1 1 1 1", solo.gantt());
        assertTrue(solo.eventos(Momento.B).isEmpty());

        Kernel dos = correr(new RoundRobin(2), new Pcb(1, 0, 3), new Pcb(2, 0, 2));
        assertEquals("1 1 2 2 1", dos.gantt());
        assertEquals(1, dos.eventos(Momento.B).size());
        assertEquals(2, dos.eventos(Momento.B).get(0).t());
    }

    @Test @DisplayName("[P5][P6] Momento e: el que termina libera CPU y memoria, y el que esperaba entra en ese instante")
    void momentoE() {
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(100);
        Pcb p1 = new Pcb(1, 0, 2, 0, 60), p2 = new Pcb(2, 0, 2, 0, 60);
        Kernel k = pasos(new Kernel(new Fcfs(), List.of(p1, p2), largo), 2);

        assertEquals(Estado.TERMINADO, p1.estado());
        assertEquals(Estado.NUEVO, p2.estado());           // no cupo: espera en Nuevo
        assertEquals(0, largo.usada());
        assertEquals(2, k.eventos(Momento.E).get(0).t());
        k.paso();
        assertEquals(p2, k.colas().enCpu());
        assertEquals(2, p2.respuesta());
    }

    @Test @DisplayName("[P6] Sin proceso en la CPU no se puede pedir E/S")
    void pedirESSinCpu() {
        Kernel k = new Kernel(new Fcfs(), List.of(new Pcb(1, 3, 2)));
        assertThrows(EventoInvalidoException.class, () -> k.pedirES(Dispositivo.DISCO));
    }

    @Test @DisplayName("[P6] Si todos quedan bloqueados sin fin de E/S, la simulación avisa en vez de colgarse")
    void todosBloqueados() {
        Kernel k = pasos(new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 3))), 1);
        k.pedirES(Dispositivo.IMPRESORA);
        assertThrows(EventoInvalidoException.class, k::ejecutarTodo);
    }
}
