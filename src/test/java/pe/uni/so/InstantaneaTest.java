package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.SolicitudES;
import pe.uni.so.es.VelocidadDispositivo;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.interrupciones.TipoInterrupcion;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.Reemplazo;
import pe.uni.so.motor.ColorEstado;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.motor.Instantanea.Insignia;
import pe.uni.so.motor.Kernel;
import pe.uni.so.motor.Modo;
import pe.uni.so.motor.Momento;
import pe.uni.so.motor.ObservadorTick;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.PlanificadorLargoPlazo;
import pe.uni.so.planificacion.RoundRobin;
import pe.uni.so.planificacion.Srtf;
import pe.uni.so.procesos.CreadorProcesos;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

class InstantaneaTest {
    private static final long B = 64 * 1024;

    private static Kernel pasos(Kernel k, int n) {
        for (int i = 0; i < n; i++) k.paso();
        return k;
    }

    @Test @DisplayName("[P16] Al crear el Kernel hay una foto inicial en t = 0 con todos en Nuevo (gris) y la CPU libre")
    void fotoInicial() {
        Kernel k = new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 3), new Pcb(2, 2, 2)));
        Instantanea f = k.instantanea();

        assertEquals(0, f.t());
        assertEquals(1, k.historial().size());
        assertEquals(2, f.en(Estado.NUEVO).size());
        assertTrue(f.procesos().stream().allMatch(p -> p.color().equals(ColorEstado.NUEVO) && !p.cambio()));
        assertTrue(f.cpu().libre());
        assertEquals(Modo.KERNEL, f.cpu().modo());
        assertTrue(f.sucesos().isEmpty());
        assertNull(f.memoria());                                   // sin gestor de memoria
        assertEquals(Dispositivo.values().length, f.dispositivos().size());
    }

    @Test @DisplayName("[P16] Cada tick deja una foto: quién ejecutó, quién tiene la CPU, colas, cambios de estado y su motivo")
    void fotoPorTick() {
        Kernel k = pasos(new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 3), new Pcb(2, 0, 2))), 1);
        Instantanea f = k.instantanea();

        assertEquals(1, f.t());
        assertEquals(1, f.cpu().ejecuto());
        assertEquals(1, f.cpu().enCpu());
        assertEquals(Modo.USUARIO, f.cpu().modo());
        assertEquals(List.of(2), f.listos());

        Instantanea.Proceso p1 = f.proceso(1).orElseThrow(), p2 = f.proceso(2).orElseThrow();
        assertEquals(Estado.NUEVO, p1.anterior());
        assertEquals(Estado.EJECUTANDO, p1.estado());
        assertEquals(ColorEstado.EJECUTANDO, p1.color());
        assertEquals(Momento.D, p1.momento());
        assertTrue(p1.motivo().contains("admitido") && p1.motivo().contains("despachador"), p1.motivo());
        assertEquals(2, p1.restante());
        assertEquals("0x00000004", p1.pcHex());                    // una instrucción: PC avanza 4 bytes
        assertEquals(ColorEstado.LISTO, p2.color());
        assertEquals(2, f.cambios().size());
        assertEquals(2, f.sucesos().size());                       // las dos admisiones del momento d
    }

    @Test @DisplayName("[P16] Una foto vieja no cambia aunque la simulación siga (es inmutable)")
    void inmutable() {
        Kernel k = pasos(new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 4))), 1);
        Instantanea vieja = k.instantanea();
        k.ejecutarTodo();

        assertEquals(3, vieja.proceso(1).orElseThrow().restante());
        assertEquals(Estado.EJECUTANDO, vieja.proceso(1).orElseThrow().estado());
        assertEquals(Estado.TERMINADO, k.instantanea().proceso(1).orElseThrow().estado());
        assertTrue(k.instantanea().terminada());
        assertThrows(UnsupportedOperationException.class, () -> vieja.procesos().clear());
        assertThrows(UnsupportedOperationException.class, () -> k.historial().clear());
    }

    @Test @DisplayName("[P16] Observer: el suscrito recibe la misma foto que guarda el historial, una por tick y en orden")
    void observador() {
        Kernel k = new Kernel(new RoundRobin(2), List.of(new Pcb(1, 0, 3), new Pcb(2, 0, 3)));
        List<Instantanea> recibidas = new ArrayList<>();
        ObservadorTick vista = recibidas::add;
        k.observar(vista);
        k.ejecutarTodo();

        assertEquals(k.t(), recibidas.size());
        for (int i = 0; i < recibidas.size(); i++) {
            assertEquals(i + 1, recibidas.get(i).t());
            assertTrue(recibidas.get(i) == k.instantanea(i + 1).orElseThrow());
        }
        StringBuilder gantt = new StringBuilder();
        for (Instantanea f : recibidas) gantt.append(f.cpu().ejecuto() == Instantanea.NINGUNO ? "-" : f.cpu().ejecuto()).append(' ');
        assertEquals(k.gantt(), gantt.toString().trim());          // la columna «ejecutó» reconstruye el Gantt

        k.dejarDeObservar(vista);
        k.paso();                                                  // ya no recibe fotos
        assertEquals(k.t() - 1, recibidas.size());
        assertThrows(ParametroInvalidoException.class, () -> k.observar(null));
    }

    @Test @DisplayName("[P16] Insignias: fin de quantum en Round Robin y expropiación en SRTF, sin agregar estados nuevos")
    void insignias() {
        Kernel rr = pasos(new Kernel(new RoundRobin(2), List.of(new Pcb(1, 0, 5), new Pcb(2, 0, 5))), 3);
        Instantanea.Proceso p1 = rr.instantanea().proceso(1).orElseThrow();
        assertEquals(Estado.LISTO, p1.estado());
        assertEquals(Estado.EJECUTANDO, p1.anterior());
        assertTrue(p1.tiene(Insignia.FIN_QUANTUM));
        assertEquals(Momento.B, p1.momento());
        assertFalse(rr.instantanea(4).isPresent());
        assertFalse(pasos(rr, 1).instantanea().proceso(1).orElseThrow().tiene(Insignia.FIN_QUANTUM));   // dura un tick

        Kernel srtf = pasos(new Kernel(new Srtf(), List.of(new Pcb(1, 0, 8), new Pcb(2, 1, 4))), 2);
        Instantanea.Proceso e = srtf.instantanea().proceso(1).orElseThrow();
        assertTrue(e.tiene(Insignia.EXPROPIADO));
        assertTrue(e.motivo().contains("expropiado por P2"), e.motivo());
        assertEquals(2, srtf.instantanea().cpu().enCpu());
        assertEquals(1, srtf.instantanea().cpu().desalojos());
    }

    @Test @DisplayName("[P16] El que llegó y no cabe en memoria sigue en Nuevo con la insignia de espera")
    void esperaMemoria() {
        Kernel k = pasos(new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 2, 0, 8), new Pcb(2, 0, 2, 0, 8)),
                new PlanificadorLargoPlazo(10)), 1);
        Instantanea f = k.instantanea();

        assertEquals(List.of(2), f.nuevos());
        assertTrue(f.proceso(2).orElseThrow().tiene(Insignia.ESPERA_MEMORIA));
        assertEquals(Estado.NUEVO, f.proceso(2).orElseThrow().estado());
        k.ejecutarTodo();
        assertTrue(k.instantanea().nuevos().isEmpty());
    }

    @Test @DisplayName("[P16][E5][E6] La foto sigue una E/S: cola del disco, ticks restantes, IRQ pendiente en el PIC y vuelta a Listo")
    void entradaSalida() {
        Pcb p1 = new Pcb(1, 0, 6), p2 = new Pcb(2, 0, 6);
        Kernel k = pasos(new Kernel(new Fcfs(), List.of(p1, p2)), 1);
        k.llamadaSistema(new SolicitudES(1, Dispositivo.DISCO, SolicitudES.Operacion.LEER, 0x1000, 512, 2));
        k.paso();

        Instantanea f = k.instantanea();
        Instantanea.FichaDispositivo disco = f.dispositivo(Dispositivo.DISCO);
        assertEquals(List.of(1), disco.cola());
        assertEquals(1, disco.atendiendo());
        assertEquals(1, disco.restante());
        assertEquals(SolicitudES.Operacion.LEER, disco.operacion());
        assertEquals(14, disco.irq());
        assertEquals(VelocidadDispositivo.NORMAL, disco.velocidad());
        Instantanea.Proceso b = f.proceso(1).orElseThrow();
        assertEquals(Estado.BLOQUEADO, b.estado());
        assertEquals(ColorEstado.BLOQUEADO, b.color());
        assertEquals(Dispositivo.DISCO, b.dispositivo());
        assertEquals(Momento.A, b.momento());
        assertTrue(f.dispositivo(Dispositivo.TECLADO).libre());

        k.paso();                                                  // el disco termina: levanta IRQ14
        assertEquals(List.of(TipoInterrupcion.DISCO), k.instantanea().pic().pendientes());
        assertEquals(0, k.instantanea().dispositivo(Dispositivo.DISCO).restante());

        k.paso();                                                  // el kernel la atiende: P1 vuelve a Listo
        Instantanea g = k.instantanea();
        assertTrue(g.pic().pendientes().isEmpty());
        assertTrue(g.pic().enServicio().isEmpty());                // ya se envió el EOI
        assertEquals(1, g.pic().atendidas(TipoInterrupcion.DISCO));
        assertEquals(Estado.LISTO, g.proceso(1).orElseThrow().estado());
        assertEquals(Momento.C, g.proceso(1).orElseThrow().momento());
        assertTrue(g.dispositivo(Dispositivo.DISCO).libre());
    }

    @Test @DisplayName("[P16][M7] Con paginación bajo demanda la foto muestra cuota, marcos ocupados y fallos de página")
    void memoriaBajoDemanda() {
        Configuracion base = Configuracion.porDefecto();
        Configuracion c = new Configuracion(base.ram(), base.pagina(), base.cpu(), base.politica(), base.quantum(),
                base.costoDespacho(), base.grado(), base.estrategia(), Configuracion.Carga.BAJO_DEMANDA, Reemplazo.LRU,
                base.dispositivos(), base.semilla());
        List<Pcb> lote = CreadorProcesos.loteOficial(B);
        Kernel k = c.crearKernel(lote);
        while (k.instantanea().cpu().libre()) k.paso();          // hasta que el primero llegue a la CPU
        Pcb p = lote.stream().filter(x -> x.pid == k.instantanea().cpu().enCpu()).findFirst().orElseThrow();
        k.acceder(p.espacio().base(EspacioProceso.Segmento.TEXTO), 4, false);
        k.paso();

        Instantanea.Memoria m = k.instantanea().memoria();
        assertEquals(c.marcos(), m.marcos());
        assertEquals(Reemplazo.LRU, m.reemplazo());
        assertEquals(1, m.fallosPagina());
        assertEquals(m.marcos(), m.muestra().libres() + m.muestra().ocupados());
        Instantanea.Proceso f = k.instantanea().proceso(p.pid).orElseThrow();
        assertEquals(1, f.marcos());
        assertTrue(f.cuota() >= f.marcos());
    }

    @Test @DisplayName("[P16][P17] El lote oficial produce una foto por tick, con t consecutivo y el historial acotado")
    void loteOficialEHistorial() {
        Kernel k = Configuracion.porDefecto().crearKernel(CreadorProcesos.loteOficial(B));
        k.limitarHistorial(50);
        Set<Integer> atendidas = new HashSet<>();
        k.observar(f -> { if (f.cpu().enCpu() != Instantanea.NINGUNO) atendidas.add(f.cpu().enCpu()); });
        k.ejecutarTodo();

        List<Instantanea> h = k.historial();
        assertEquals(50, h.size());
        assertEquals(k.t(), h.get(h.size() - 1).t());
        for (int i = 1; i < h.size(); i++) assertEquals(h.get(i - 1).t() + 1, h.get(i).t());
        assertFalse(k.instantanea(0).isPresent());                 // ya se descartó: se reproduce re-simulando
        assertEquals(20, atendidas.size());
        Instantanea ultima = k.instantanea();
        assertTrue(ultima.terminada());
        assertTrue(ultima.procesos().stream().allMatch(p -> p.marcos() == 0));   // todos liberaron su memoria
        int irqs = ultima.pic().atendidas().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(CreadorProcesos.loteOficial(B).stream().mapToInt(p -> p.planES().size()).sum(), irqs);
        assertThrows(ParametroInvalidoException.class, () -> k.limitarHistorial(0));
    }

    @Test @DisplayName("[P16] Cada estado tiene su color y son distintos entre sí")
    void colores() {
        Set<String> colores = new HashSet<>();
        for (Estado e : Estado.values()) assertTrue(colores.add(ColorEstado.de(e)), e + " repite color");
        assertTrue(colores.stream().allMatch(c -> c.matches("#[0-9A-F]{6}")));
        assertEquals(Map.of(), new Instantanea.Pic(List.of(), List.of(), List.of(), Map.of()).atendidas());
    }
}
