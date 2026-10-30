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
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.motor.Kernel;
import pe.uni.so.motor.Modo;
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

    @Test @DisplayName("[P7] Cambiar el quantum en plena simulación rige desde el siguiente despacho")
    void quantumEnCaliente() {
        RoundRobin rr = new RoundRobin(2);
        Kernel k = pasos(new Kernel(rr, List.of(new Pcb(1, 0, 6), new Pcb(2, 0, 6))), 3);   // P2 lleva 1 de 2
        rr.setQuantum(3);
        k.ejecutarTodo();
        // P2 termina su quantum de 2 (t = 3); desde t = 4 cada despacho usa q = 3
        assertEquals("1 1 2 2 1 1 1 2 2 2 1 2", k.gantt());
        assertTrue(k.eventos(Momento.B).get(1).detalle().contains("quantum de 2"));
        assertTrue(k.eventos(Momento.B).get(2).detalle().contains("quantum de 3"));
    }

    @Test @DisplayName("[P1][P6] Las 6 transiciones quedan en la bitácora con su causa")
    void seisTransicionesConCausa() {
        Pcb p1 = new Pcb(1, 0, 4, 3), p2 = new Pcb(2, 1, 2, 1);
        Kernel k = pasos(new Kernel(new Prioridad(true), List.of(p1, p2)), 2);   // P2 llega y expropia a P1
        k.pedirES(Dispositivo.DISCO);                                              // P2 pide E/S
        pasos(k, 1);                                                               // P1 vuelve a la CPU
        k.finES(Dispositivo.DISCO);                                                // P2 vuelve y expropia
        k.ejecutarTodo();

        for (Momento m : Momento.values()) assertTrue(!k.eventos(m).isEmpty(), "falta el momento " + m);
        assertTrue(k.bitacora().stream().allMatch(e -> !e.detalle().isBlank()));
        // el despacho (Listo → Ejecutando) lo registra el despachador con sus 4 pasos
        assertTrue(k.despachador().bitacora().stream().anyMatch(l -> l.contains("pasa a Ejecutando")));
        assertEquals(Estado.TERMINADO, p1.estado());
        assertEquals(Estado.TERMINADO, p2.estado());
    }

    @Test @DisplayName("[P8] El bit de modo pasa a KERNEL con interrupciones y llamadas al sistema, y a USUARIO al despachar")
    void bitDeModo() {
        Kernel k = new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 3)));
        assertEquals(Modo.KERNEL, k.modo());                   // arranque
        k.paso();
        assertEquals(Modo.USUARIO, k.modo());                  // P1 ejecuta
        k.pedirES(Dispositivo.DISCO);
        assertEquals(Modo.KERNEL, k.modo());                   // llamada al sistema 0x80
        k.paso();
        assertEquals(Modo.KERNEL, k.modo());                   // CPU ociosa: se queda en el kernel
        k.finES(Dispositivo.DISCO);
        k.paso();
        assertEquals(Modo.USUARIO, k.modo());
        assertTrue(k.despachador().bitacora().stream().anyMatch(l -> l.contains("modo usuario")));
    }

    @Test @DisplayName("[P8] Al terminar el último proceso la CPU queda en modo KERNEL")
    void modoAlTerminar() {
        Kernel k = new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 1)));
        k.paso();                                              // ejecuta su única instrucción y termina
        assertNull(k.colas().enCpu());
        assertEquals(Modo.KERNEL, k.modo());
    }

    @Test @DisplayName("[P7] Un proceso solo conserva su quantum aunque se cambie; el nuevo rige en el siguiente despacho")
    void quantumSoloHastaElSiguienteDespacho() {
        RoundRobin rr = new RoundRobin(2);
        Kernel k = pasos(new Kernel(rr, List.of(new Pcb(1, 0, 6), new Pcb(2, 3, 4))), 1);
        rr.setQuantum(3);
        k.ejecutarTodo();
        // P1 sigue con q = 2: corta en t = 4 (P2 ya llegó en t = 3); desde ahí cada despacho usa q = 3
        assertEquals("1 1 1 1 2 2 2 1 1 2", k.gantt());
        assertTrue(k.eventos(Momento.B).get(0).detalle().contains("quantum de 2"));
    }

    @Test @DisplayName("[P3] El Kernel lleva en la PCB el tiempo bloqueado y el quantum restante")
    void camposQueLlevaElKernel() {
        Pcb p1 = new Pcb(1, 0, 3), p2 = new Pcb(2, 0, 4);
        Kernel k = pasos(new Kernel(new RoundRobin(3), List.of(p1, p2)), 1);
        assertEquals(2, p1.quantumRestante());                  // usó 1 de 3
        k.pedirES(Dispositivo.DISCO);
        pasos(k, 2);
        assertEquals(2, p1.bloqueado());
        assertEquals(1, p2.quantumRestante());
    }

    @Test @DisplayName("[P3][P6] Un burst de 0 o negativo se rechaza al crear el proceso")
    void burstInvalido() {
        assertThrows(ParametroInvalidoException.class, () -> new Pcb(1, 0, 0));
        assertThrows(ParametroInvalidoException.class, () -> new Pcb(2, 0, -3));
        assertThrows(ParametroInvalidoException.class, () -> new Pcb(3, 0, Pcb.BURST_MAXIMO + 1));   // no cabe en 16 bits
        assertEquals(0xFFFF_FFFF, new Pcb(4, 0, Pcb.BURST_MAXIMO).leer(pe.uni.so.procesos.CampoPcb.BURST));
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

    @Test @DisplayName("[P5][P6] Si el que llegó no cabe porque un bloqueado retiene la memoria, la simulación avisa")
    void llegadaSinMemoriaConResidenteBloqueado() {
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(100);
        Pcb p1 = new Pcb(1, 0, 3, 0, 60), p2 = new Pcb(2, 0, 2, 0, 60);
        Kernel k = pasos(new Kernel(new Fcfs(), List.of(p1, p2), largo), 1);
        k.pedirES(Dispositivo.DISCO);                      // P1 retiene 60 de 100; P2 (60) no cabe

        assertThrows(EventoInvalidoException.class, k::ejecutarTodo);
        assertEquals(Estado.NUEVO, p2.estado());
    }
}
