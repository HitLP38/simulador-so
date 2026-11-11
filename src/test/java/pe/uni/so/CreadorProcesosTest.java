package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.SolicitudES.Operacion;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.Reemplazo;
import pe.uni.so.motor.Kernel;
import pe.uni.so.motor.Momento;
import pe.uni.so.procesos.CreadorProcesos;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;
import pe.uni.so.procesos.PedidoES;

class CreadorProcesosTest {

    private static final long KIB = 1024, B = 64 * KIB;

    private static Configuracion maquina(Configuracion.Cpu cpu) {
        Configuracion d = Configuracion.porDefecto();
        return new Configuracion(d.ram(), d.pagina(), cpu, Configuracion.Politica.FCFS, d.quantum(), d.costoDespacho(), d.grado(),
                d.estrategia(), d.carga(), d.reemplazo(), d.dispositivos(), d.semilla());
    }

    /** Un proceso de ráfaga 6 con un solo pedido de disco (4 ticks) tras 3 instrucciones. */
    private static Pcb conUnPedido() {
        Pcb p = new Pcb(1, 0, 6);
        p.asignarEspacio(new EspacioProceso(100 * KIB, 0.20, 0.10, B));
        p.asignarPlanES(List.of(new PedidoES(3, Dispositivo.DISCO, Operacion.LEER, 4)));
        return p;
    }

    @Test @DisplayName("[P14][P18] Manual y aleatorio pasan por el mismo crear(): PID consecutivo, espacio con la página de la máquina y plan de E/S")
    void mismoPuntoDeCreacion() {
        CreadorProcesos c = new CreadorProcesos(42, B);
        Pcb a = c.aleatorios(1, 0).get(0);
        Pcb m = c.manual(5, 30, 512 * KIB);
        assertEquals(1, a.pid);
        assertEquals(2, m.pid);
        assertEquals(5, m.llegada);
        assertEquals(30, m.burst);
        assertEquals(CreadorProcesos.PRIORIDAD_MANUAL, m.prioridad);
        assertEquals(B, m.espacio().bloque());
        assertEquals(512 * KIB, m.espacio().tamano(EspacioProceso.Segmento.TEXTO));
        assertFalse(a.planES().isEmpty());
        assertFalse(m.planES().isEmpty());
    }

    @Test @DisplayName("[P14] Generar N cumple la ecuación 8: ráfaga 11–50, ejecutable 128–2047 KiB, datos 20–40 %, heap 10–30 %, prioridad 1–5, llegadas crecientes")
    void rangosDeLaCargaAleatoria() {
        List<Pcb> carga = new CreadorProcesos(7, B).aleatorios(1000, 0);
        int anterior = 0;
        for (Pcb p : carga) {
            assertTrue(p.burst >= 11 && p.burst <= 50, "ráfaga " + p.burst);
            long texto = p.espacio().tamano(EspacioProceso.Segmento.TEXTO);
            assertTrue(texto >= 128 * KIB && texto <= 2047 * KIB, "texto " + texto);
            double datos = (double) p.espacio().tamano(EspacioProceso.Segmento.DATOS) / texto;
            double heap = (double) p.espacio().tamano(EspacioProceso.Segmento.HEAP) / texto;
            assertTrue(datos >= 0.199 && datos <= 0.401, "datos " + datos);
            assertTrue(heap >= 0.099 && heap <= 0.301, "heap " + heap);
            assertTrue(p.prioridad >= 1 && p.prioridad <= 5);
            assertTrue(p.llegada >= anterior);
            anterior = p.llegada;
        }
        assertEquals(1000, carga.get(999).pid);
    }

    @Test @DisplayName("[P11][P12][E10][E11] Cada proceso pide E/S entre 5 y 10 veces (10 a 20 interrupciones), en instantes distintos dentro de la ráfaga, de 5 a 20 ticks cada una")
    void planDeES() {
        for (Pcb p : new CreadorProcesos(99, B).aleatorios(2000, 0)) {
            List<PedidoES> plan = p.planES();
            assertTrue(plan.size() >= 5 && plan.size() <= 10, "pedidos " + plan.size());
            int anterior = 0;
            for (PedidoES pedido : plan) {
                assertTrue(pedido.instante() > anterior && pedido.instante() <= p.burst - 1);
                assertTrue(pedido.duracion() >= 5 && pedido.duracion() <= 20, "duración " + pedido.duracion());
                if (pedido.dispositivo() == Dispositivo.TECLADO) assertEquals(Operacion.LEER, pedido.operacion());
                if (pedido.dispositivo() == Dispositivo.PANTALLA) assertEquals(Operacion.ESCRIBIR, pedido.operacion());
                anterior = pedido.instante();
            }
        }
    }

    @Test @DisplayName("[E10] Los dispositivos siguen la ruleta de la ecuación 6: 20 % teclado, 30 % disco, 15 % impresora, 20 % pantalla, 15 % red")
    void ruletaDeDispositivos() {
        Map<Dispositivo, Integer> cuenta = new EnumMap<>(Dispositivo.class);
        int total = 0;
        for (Pcb p : new CreadorProcesos(3, B).aleatorios(3000, 0))
            for (PedidoES pedido : p.planES()) { cuenta.merge(pedido.dispositivo(), 1, Integer::sum); total++; }
        Map<Dispositivo, Double> esperado = Map.of(Dispositivo.TECLADO, 0.20, Dispositivo.DISCO, 0.30,
                Dispositivo.IMPRESORA, 0.15, Dispositivo.PANTALLA, 0.20, Dispositivo.RED, 0.15);
        for (Dispositivo d : Dispositivo.values()) {
            double real = (double) cuenta.getOrDefault(d, 0) / total;
            assertTrue(Math.abs(real - esperado.get(d)) < 0.02, d + ": " + real);
        }
    }

    @Test @DisplayName("[P13] Misma semilla, misma carga; otra semilla, otra carga")
    void reproducible() {
        List<Pcb> a = new CreadorProcesos(2026, B).aleatorios(30, 0), b = new CreadorProcesos(2026, B).aleatorios(30, 0);
        List<Pcb> c = new CreadorProcesos(2027, B).aleatorios(30, 0);
        for (int i = 0; i < 30; i++) {
            assertEquals(a.get(i).burst, b.get(i).burst);
            assertEquals(a.get(i).llegada, b.get(i).llegada);
            assertEquals(a.get(i).planES(), b.get(i).planES());
        }
        boolean distinta = false;
        for (int i = 0; i < 30; i++) distinta |= a.get(i).burst != c.get(i).burst || !a.get(i).planES().equals(c.get(i).planES());
        assertTrue(distinta);
    }

    @Test @DisplayName("[P18] El ingreso manual rechaza ráfagas menores que 11, llegadas o tamaños inválidos")
    void manualInvalido() {
        CreadorProcesos c = new CreadorProcesos(1, B);
        assertThrows(ParametroInvalidoException.class, () -> c.manual(0, 10, 512 * KIB));     // b ≥ N + 1 exige 11
        assertThrows(ParametroInvalidoException.class, () -> c.manual(-1, 20, 512 * KIB));
        assertThrows(ParametroInvalidoException.class, () -> c.manual(0, 20, 0));
        assertThrows(ParametroInvalidoException.class, () -> c.crear(0, 20, 512 * KIB, 0.50, 0.20, 3));   // datos > 40 %
        assertThrows(ParametroInvalidoException.class, () -> c.crear(0, 20, 512 * KIB, 0.30, 0.20, 6));   // prioridad > 5
        assertEquals(1, c.manual(0, 11, 512 * KIB).pid);                                    // los rechazados no gastan PID
    }

    @Test @DisplayName("[E2] Un plan de E/S debe tener instantes crecientes dentro de la ráfaga y solo se asigna antes de admitir")
    void planInvalido() {
        Pcb p = new Pcb(1, 0, 6);
        assertThrows(ParametroInvalidoException.class, () -> p.asignarPlanES(List.of(new PedidoES(6, Dispositivo.DISCO, Operacion.LEER, 4))));
        assertThrows(ParametroInvalidoException.class, () -> p.asignarPlanES(List.of(
                new PedidoES(3, Dispositivo.DISCO, Operacion.LEER, 4), new PedidoES(3, Dispositivo.RED, Operacion.LEER, 4))));
        assertThrows(ParametroInvalidoException.class, () -> new PedidoES(0, Dispositivo.DISCO, Operacion.LEER, 4));
        assertThrows(EventoInvalidoException.class, p::marcarPedidoEmitido);
        p.pasarA(Estado.LISTO);
        assertThrows(EventoInvalidoException.class, () -> p.asignarPlanES(List.of()));
    }

    @Test @DisplayName("[E2][P6] La E/S planificada se pide sola al llegar a su instante: momento a al cerrar el tick y Listo en t + 1 tras la IRQ")
    void pedidoAutomatico() {
        Pcb p = conUnPedido();
        Kernel k = maquina(Configuracion.Cpu.GHZ_1).crearKernel(List.of(p));
        k.ejecutarTodo();
        assertEquals("1 1 1 - - - - 1 1 1", k.gantt());                 // 3 instrucciones, disco 3–6, vuelve en 7
        assertEquals(4, p.bloqueado());
        assertEquals(10, p.fin());
        assertEquals(2, p.interrupciones());                            // la 0x80 y la IRQ de fin
        assertEquals(3, k.eventos(Momento.A).get(0).t());
        assertEquals(p.retorno(), p.cpu() + p.espera() + p.bloqueado());
    }

    @Test @DisplayName("[G5][E2] Una CPU de 2 GHz no se salta el instante de un pedido de E/S")
    void cpuRapidaRespetaElPedido() {
        Pcb p = conUnPedido();
        Kernel k = maquina(Configuracion.Cpu.GHZ_2).crearKernel(List.of(p));
        k.ejecutarTodo();
        assertEquals("1 1 - - - - 1 1", k.gantt());                     // 2 + 1 instrucciones, pide en t = 2, luego 2 + 1
        assertEquals(8, p.fin());
        assertEquals(4, p.bloqueado());
    }

    @Test @DisplayName("[P17] Lote oficial: 20 procesos fijos que llegan en secuencia, idénticos en cada corrida")
    void loteOficial() {
        List<Pcb> lote = CreadorProcesos.loteOficial(B), otra = CreadorProcesos.loteOficial(B);
        assertEquals(20, lote.size());
        assertEquals(0, lote.get(0).llegada);
        assertEquals(11, lote.get(0).burst);
        assertEquals(56, lote.get(19).llegada);
        assertEquals(49, lote.get(19).burst);
        assertEquals(642, lote.stream().mapToInt(p -> p.burst).sum());
        for (int i = 0; i < 20; i++) {
            assertEquals(i + 1, lote.get(i).pid);
            assertEquals(lote.get(i).planES(), otra.get(i).planES());
            if (i > 0) assertTrue(lote.get(i).llegada >= lote.get(i - 1).llegada);
        }
    }

    @Test @DisplayName("[P17][E10] El lote oficial corre completo en la máquina por defecto: todos terminan con 10 a 20 interrupciones de E/S")
    void loteOficialCorre() {
        List<Pcb> lote = CreadorProcesos.loteOficial(B);
        Kernel k = Configuracion.porDefecto().crearKernel(lote);
        k.ejecutarTodo();
        for (Pcb p : lote) {
            assertEquals(Estado.TERMINADO, p.estado());
            assertTrue(p.interrupciones() >= 10 && p.interrupciones() <= 20, p + ": " + p.interrupciones());
            assertEquals((long) p.planES().size(), k.eventos(Momento.A).stream().filter(e -> e.proceso() == p).count());
            assertTrue(p.retorno() >= p.cpu() + p.espera() + p.bloqueado());   // el resto es la espera en Nuevo (grado 10)
        }
    }

    @Test @DisplayName("[G5][I6] Capacidad antes de iniciar: el grado y la RAM dicen cuántos caben a la vez y cuántos esperarían en Nuevo")
    void capacidad() {
        List<Pcb> carga = new CreadorProcesos(5, B).aleatorios(300, 0);
        Configuracion.Capacidad c = Configuracion.porDefecto().capacidad(carga);
        assertEquals(300, c.procesos());
        assertEquals(10, c.porGrado());
        assertEquals(10, c.simultaneos());
        assertEquals(290, c.enEspera());
        assertTrue(c.porRam() > 10);
        assertTrue(c.resumen().contains("limita el grado"));

        Configuracion d = Configuracion.porDefecto();
        Configuracion modesta = new Configuracion(Configuracion.Ram.MIB_128, Configuracion.Pagina.MIB_2, d.cpu(), d.politica(),
                d.quantum(), d.costoDespacho(), 50, d.estrategia(), d.carga(), Reemplazo.LRU, d.dispositivos(), d.semilla());
        List<Pcb> grandes = new CreadorProcesos(5, 2048 * KIB).aleatorios(300, 0);
        Configuracion.Capacidad m = modesta.capacidad(grandes);                           // 64 marcos de 2 MiB
        assertTrue(m.porRam() < 50);
        assertEquals(m.porRam(), m.simultaneos());
        assertTrue(m.resumen().contains("limita la RAM"));
    }
}
