package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.planificacion.*;
import pe.uni.so.procesos.Pcb;

/** Capa TEORÍA: el motor debe dar exactamente las soluciones del taller 2026-2 (parte 2). */
class TallerPlanificacionTest {
    private static final double D = 0.01;

    private static SimuladorCpu.Resultado correr(PoliticaPlanificacion p, Pcb... procesos) {
        return new SimuladorCpu(p, List.of(procesos)).ejecutar();
    }

    private static Pcb[] ej22() { return new Pcb[] {new Pcb(1, 0, 8), new Pcb(2, 1, 4), new Pcb(3, 2, 2), new Pcb(4, 3, 1)}; }

    @Test @DisplayName("[P5][P7] Taller ej. 21: FCFS depende del orden de inserción (efecto convoy)")
    void ej21() {
        var a = correr(new Fcfs(), new Pcb(1, 0, 18), new Pcb(2, 0, 3), new Pcb(3, 0, 2));
        assertEquals(20.67, a.retornoMedio(), D);
        assertEquals(13.00, a.esperaMedia(), D);
        var b = correr(new Fcfs(), new Pcb(3, 0, 2), new Pcb(2, 0, 3), new Pcb(1, 0, 18));
        assertEquals(10.00, b.retornoMedio(), D);
        assertEquals(2.33, b.esperaMedia(), D);
    }

    @Test @DisplayName("[P5][P7] Taller ej. 22: SJF no expropiativo")
    void ej22Sjf() {
        var r = correr(new Sjf(), ej22());
        assertEquals(9.25, r.retornoMedio(), D);
        assertEquals(5.50, r.esperaMedia(), D);
        assertEquals(5.50, r.respuestaMedia(), D);
    }

    @Test @DisplayName("[P5][P7][P6] Taller ej. 23: SRTF, el empate conserva al actual")
    void ej23Srtf() {
        var r = correr(new Srtf(), ej22());
        assertEquals(6.50, r.retornoMedio(), D);
        assertEquals(2.75, r.esperaMedia(), D);
        assertEquals(0.25, r.respuestaMedia(), D);
        assertEquals(2, r.desalojos());
    }

    @Test @DisplayName("[P5][P7] Taller ej. 24: prioridades expropiativas y no expropiativas")
    void ej24Prioridad() {
        var e = correr(new Prioridad(true), new Pcb(1, 0, 7, 3), new Pcb(2, 2, 4, 1), new Pcb(3, 3, 2, 2), new Pcb(4, 5, 1, 1));
        assertEquals(6.50, e.retornoMedio(), D);
        assertEquals(3.00, e.esperaMedia(), D);
        assertEquals(1.25, e.respuestaMedia(), D);
        var ne = correr(new Prioridad(false), new Pcb(1, 0, 7, 3), new Pcb(2, 2, 4, 1), new Pcb(3, 3, 2, 2), new Pcb(4, 5, 1, 1));
        assertEquals(5, ne.procesos().get(1).respuesta());
    }

    @Test @DisplayName("[P5][P7] Taller ej. 26: Round Robin q = 2")
    void ej26RoundRobin() {
        var r = correr(new RoundRobin(2), new Pcb(1, 0, 7), new Pcb(2, 0, 4), new Pcb(3, 0, 2));
        assertEquals(9.67, r.retornoMedio(), D);
        assertEquals(5.33, r.esperaMedia(), D);
        assertEquals(2.00, r.respuestaMedia(), D);
        assertEquals(5, r.transiciones());
        assertEquals("1 1 2 2 3 3 1 1 2 2 1 1 1", r.gantt());
    }

    @Test @DisplayName("[P7][P8] Taller ej. 27: quantum y costo de los cambios de contexto")
    void ej27Quantum() {
        int[] q = {1, 2, 8};
        int[] cambios = {10, 5, 2};
        double[] util = {92.86, 96.30, 98.48};
        for (int i = 0; i < q.length; i++) {
            var r = correr(new RoundRobin(q[i]), new Pcb(1, 0, 7), new Pcb(2, 0, 4), new Pcb(3, 0, 2));
            assertEquals(cambios[i], r.transiciones());
            assertEquals(util[i], 100.0 * 13 / (13 + 0.1 * r.transiciones()), D);
        }
    }
}
