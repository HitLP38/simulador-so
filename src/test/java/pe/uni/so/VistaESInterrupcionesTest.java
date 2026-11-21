package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.interrupciones.TipoInterrupcion;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.motor.Kernel;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.procesos.Pcb;
import pe.uni.so.es.SolicitudES;
import pe.uni.so.sesion.RegistroES;
import pe.uni.so.sesion.RegistroES.Accion;
import pe.uni.so.sesion.RegistroES.Servicio;
import pe.uni.so.sesion.SesionSimulacion;
import pe.uni.so.sesion.TablaVectores;

class VistaESInterrupcionesTest {

    private static SesionSimulacion alFinal() {
        SesionSimulacion s = new SesionSimulacion(Maquina.porDefecto());
        s.hastaElFinal();
        return s;
    }

    @Test @DisplayName("[I1][I2][P2] IVT: 11 vectores en orden; las de hardware con IRQ y prioridad del PIC, las síncronas sin IRQ")
    void tablaVectores() {
        List<TablaVectores.Fila> f = TablaVectores.filas();
        assertEquals(11, f.size());
        for (int i = 1; i < f.size(); i++) assertTrue(f.get(i - 1).vector() < f.get(i).vector());
        TablaVectores.Fila timer = TablaVectores.de(TipoInterrupcion.TIMER);
        assertEquals(0, timer.irq());
        assertEquals(1, timer.prioridad());
        assertEquals("0x20", timer.vectorHex());
        assertEquals(14, TablaVectores.de(TipoInterrupcion.DISCO).irq());
        assertEquals(6, TablaVectores.de(TipoInterrupcion.DISCO).prioridad());           // IRQ 0,1,7,10,11,14
        assertEquals(Dispositivo.DISCO, TablaVectores.de(TipoInterrupcion.DISCO).dispositivo());
        TablaVectores.Fila fallo = TablaVectores.de(TipoInterrupcion.FALLO_PAGINA);
        assertTrue(fallo.sincrona());
        assertEquals(0, fallo.prioridad());
        assertEquals(7, f.stream().filter(TablaVectores.Fila::generada).count());          // 5 dispositivos (PIC) + TIMER + int 0x80
        assertTrue(timer.generada());
        assertTrue(TablaVectores.de(TipoInterrupcion.LLAMADA_SISTEMA).generada());          // cada pedido de E/S (momento a)
        assertFalse(TablaVectores.de(TipoInterrupcion.DIVISION_CERO).generada());
        assertTrue(f.stream().allMatch(x -> !x.rutina().isBlank()));
    }

    @Test @DisplayName("[E1][E6][P16] Gantt de dispositivos: si en la foto t el dispositivo trabajó para P, hay un servicio de P que cubre [t − 1, t)")
    void serviciosCoincidenConLasFotos() {
        SesionSimulacion s = alFinal();
        int fin = s.vista().t();
        RegistroES r = s.registroES();
        Map<Dispositivo, Integer> colaMax = new EnumMap<>(Dispositivo.class);
        Map<Dispositivo, int[]> previo = new EnumMap<>(Dispositivo.class);
        for (int t = 0; t <= fin; t++) {
            s.irA(t);
            for (Instantanea.FichaDispositivo d : s.vista().dispositivos()) {
                int espera = d.cola().size() - (d.atendiendo() == Instantanea.NINGUNO ? 0 : 1);
                colaMax.merge(d.dispositivo(), espera, Math::max);
                int[] p = previo.put(d.dispositivo(), new int[] {d.atendiendo(), d.restante()});
                boolean trabajo = d.atendiendo() != Instantanea.NINGUNO && t > 0
                        && (p == null || p[0] != d.atendiendo() || d.restante() < p[1]);
                if (!trabajo) continue;
                final int tick = t - 1;
                assertTrue(r.servicios(d.dispositivo(), fin).stream()
                                .anyMatch(x -> x.pid() == d.atendiendo() && x.desde() <= tick && tick < x.hasta()),
                        d.dispositivo() + " trabajó para P" + d.atendiendo() + " en [" + tick + ", " + t + ")");
            }
        }
        for (Dispositivo d : Dispositivo.values()) {
            List<Servicio> lista = r.servicios(d, fin);
            for (int i = 1; i < lista.size(); i++) assertTrue(lista.get(i - 1).hasta() <= lista.get(i).desde(), d + " sin solapes");
            RegistroES.ResumenDispositivo res = r.resumen(d, fin);
            assertEquals(lista.size(), res.servicios());
            assertEquals(100.0 * res.ticksOcupado() / fin, res.uso(), 1e-9);
            assertTrue(lista.stream().allMatch(x -> x.hasta() <= fin));
            assertEquals(colaMax.getOrDefault(d, 0), res.colaMaxima(), d.toString());
        }
        assertTrue(Dispositivo.values().length > 0 && r.servicios(Dispositivo.DISCO, fin).size() + r.servicios(Dispositivo.TECLADO, fin).size() > 0);
    }

    @Test @DisplayName("[I3][I4][E2] Bitácora de interrupciones: lo levantado y lo atendido suman lo que cuentan el PIC y la CPU; levantada antes que atendida")
    void bitacoraInterrupciones() {
        SesionSimulacion s = alFinal();
        int fin = s.vista().t();
        Instantanea.Pic pic = s.vista().pic();
        List<RegistroES.EventoInterrupcion> todos = s.registroES().bitacora(fin, Set.of(), Integer.MAX_VALUE);
        assertFalse(todos.isEmpty());
        for (int i = 1; i < todos.size(); i++) assertTrue(todos.get(i - 1).t() >= todos.get(i).t());
        for (TipoInterrupcion x : TipoInterrupcion.values()) {
            int atendidas = todos.stream().filter(e -> e.tipo() == x && e.accion() == Accion.ATENDIDA).mapToInt(RegistroES.EventoInterrupcion::veces).sum();
            int esperadas = switch (x) {
                case TIMER -> s.finesDeQuantum(fin);                                        // un fin de quantum, sin PIC
                case LLAMADA_SISTEMA -> s.vista().cpu().llamadasSistema();                  // síncrona: la cuenta la CPU
                default -> pic.atendidas(x);
            };
            assertEquals(esperadas, atendidas, x.toString());
            assertEquals(esperadas, s.registroES().atendidas(x, fin), x.toString());
            int levantadas = todos.stream().filter(e -> e.tipo() == x && e.accion() == Accion.LEVANTADA).mapToInt(RegistroES.EventoInterrupcion::veces).sum();
            assertEquals(x.clase == TipoInterrupcion.Clase.HARDWARE && x != TipoInterrupcion.TIMER ? pic.levantadas(x) : esperadas,
                    levantadas, x.toString());
            int primeraLevantada = todos.stream().filter(e -> e.tipo() == x && e.accion() == Accion.LEVANTADA)
                    .mapToInt(RegistroES.EventoInterrupcion::t).min().orElse(Integer.MAX_VALUE);
            int primeraAtendida = todos.stream().filter(e -> e.tipo() == x && e.accion() == Accion.ATENDIDA)
                    .mapToInt(RegistroES.EventoInterrupcion::t).min().orElse(Integer.MAX_VALUE);
            if (primeraAtendida != Integer.MAX_VALUE && primeraLevantada != Integer.MAX_VALUE)
                assertTrue(primeraLevantada <= primeraAtendida, x.toString());
        }
        List<RegistroES.EventoInterrupcion> disco = s.registroES().bitacora(fin, Set.of(TipoInterrupcion.DISCO), 1000);
        assertTrue(disco.stream().allMatch(e -> e.tipo() == TipoInterrupcion.DISCO));
        assertTrue(s.registroES().bitacora(50, Set.of(), 1000).stream().allMatch(e -> e.t() <= 50));
        assertEquals(5, s.registroES().bitacora(fin, Set.of(), 5).size());
        assertTrue(s.registroES().atendidas(TipoInterrupcion.LLAMADA_SISTEMA, fin) > 0);   // el lote oficial pide E/S
    }

    private static Instantanea foto(int t, int pid, int restante, int trabajados, Map<TipoInterrupcion, Integer> atendidas) {
        return foto(t, pid, restante, trabajados, atendidas, Map.of());
    }

    private static Instantanea foto(int t, int pid, int restante, int trabajados, Map<TipoInterrupcion, Integer> atendidas,
                                    Map<TipoInterrupcion, Integer> levantadas) {
        List<Instantanea.FichaDispositivo> ds = new java.util.ArrayList<>();
        for (Dispositivo d : Dispositivo.values())
            ds.add(d == Dispositivo.DISCO && pid != Instantanea.NINGUNO
                    ? new Instantanea.FichaDispositivo(d, pe.uni.so.es.VelocidadDispositivo.NORMAL, List.of(pid), pid, restante,
                            pe.uni.so.es.SolicitudES.Operacion.LEER, trabajados)
                    : new Instantanea.FichaDispositivo(d, pe.uni.so.es.VelocidadDispositivo.NORMAL, List.of(), Instantanea.NINGUNO, -1, null, -1));
        Instantanea.Pic pic = new Instantanea.Pic(List.of(), List.of(), List.of(), atendidas, levantadas);
        return new Instantanea(t, null, List.of(), List.of(), List.of(), ds, pic, null, List.of());
    }

    private static final int NADIE = Instantanea.NINGUNO;
    private static final pe.uni.so.es.SolicitudES.Operacion LEER = pe.uni.so.es.SolicitudES.Operacion.LEER;

    @Test @DisplayName("[E1][E6] Servicio de duración conocida: ocupa [t − 1, t) por cada tick trabajado y levanta su IRQ al terminar")
    void servicioDeDuracionConocida() {
        RegistroES r = new RegistroES();
        Map<TipoInterrupcion, Integer> cero = Map.of();
        r.registrar(foto(0, NADIE, -1, -1, cero));
        r.registrar(foto(1, NADIE, -1, -1, cero));
        r.registrar(foto(2, 5, 2, 1, cero));                               // P5 empieza: trabajó en [1, 2)
        r.registrar(foto(3, 5, 1, 2, cero));
        Map<TipoInterrupcion, Integer> una = Map.of(TipoInterrupcion.DISCO, 1), dos = Map.of(TipoInterrupcion.DISCO, 2);
        r.registrar(foto(4, 5, 0, 3, cero, una));                          // termina en [3, 4): IRQ levantada
        r.registrar(foto(5, 5, 0, 3, cero, una));                          // espera que la atiendan: no trabaja
        r.registrar(foto(6, 7, 0, 1, una, dos));                           // atendida; P7 dura 1 tick y termina en [5, 6)
        r.registrar(foto(7, 7, 0, 1, una, dos));                           // IRQ de P7 sigue pendiente
        r.registrar(foto(8, 8, 0, 1, dos, Map.of(TipoInterrupcion.DISCO, 3)));   // P8 también dura 1 tick
        assertEquals(List.of(new Servicio(Dispositivo.DISCO, 5, LEER, 1, 4), new Servicio(Dispositivo.DISCO, 7, LEER, 5, 6),
                new Servicio(Dispositivo.DISCO, 8, LEER, 7, 8)), r.servicios(Dispositivo.DISCO, 8));
        assertEquals(List.of(new Servicio(Dispositivo.DISCO, 5, LEER, 1, 3)), r.servicios(Dispositivo.DISCO, 3));   // recortado a t = 3
        RegistroES.ResumenDispositivo res = r.resumen(Dispositivo.DISCO, 8);
        assertEquals(5, res.ticksOcupado());
        assertEquals(100.0 * 5 / 8, res.uso(), 1e-9);
        assertEquals(0, r.resumen(Dispositivo.DISCO, 0).uso(), 1e-9);
        List<Integer> levantadas = r.bitacora(8, Set.of(TipoInterrupcion.DISCO), 100).stream()
                .filter(e -> e.accion() == Accion.LEVANTADA).map(RegistroES.EventoInterrupcion::t).toList();
        assertEquals(List.of(8, 6, 4), levantadas);                        // una por fin de servicio, aunque la IRQ siga pendiente
        assertEquals(2, r.atendidas(TipoInterrupcion.DISCO, 8));
    }

    @Test @DisplayName("[E9][E6] Fin forzado de un pedido sin servicio previo: levanta la IRQ pero no suma trabajo al Gantt")
    void finForzadoSinTrabajo() {
        RegistroES r = new RegistroES();
        r.registrar(foto(0, NADIE, -1, -1, Map.of()));
        Map<TipoInterrupcion, Integer> una = Map.of(TipoInterrupcion.DISCO, 1);
        r.registrar(foto(1, 9, 0, 0, Map.of(), una));                      // el usuario pulsó antes de que empezara
        r.registrar(foto(2, 9, 0, 0, Map.of(), una));                      // IRQ enmascarada: sigue a la cabeza
        r.registrar(foto(3, 4, 1, 1, una, una));                           // atendida; P4 empieza de verdad
        assertEquals(List.of(new Servicio(Dispositivo.DISCO, 4, LEER, 2, 3)), r.servicios(Dispositivo.DISCO, 3));
        assertEquals(1, r.resumen(Dispositivo.DISCO, 3).ticksOcupado());
        assertEquals(List.of(1), r.bitacora(3, Set.of(TipoInterrupcion.DISCO), 100).stream()
                .filter(e -> e.accion() == Accion.LEVANTADA).map(RegistroES.EventoInterrupcion::t).toList());
    }

    @Test @DisplayName("[E9][E6] Tecla sin máscara: el kernel atiende la IRQ1 antes de la foto siguiente y la bitácora conserva el levantamiento")
    void teclaAtendidaAntesDeLaFoto() {
        Kernel k = new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 2), new Pcb(2, 0, 5)));
        RegistroES r = new RegistroES();
        k.paso();                                                          // P1 ejecuta en t = 0
        k.llamadaSistema(new SolicitudES(1, Dispositivo.TECLADO, SolicitudES.Operacion.LEER, 0x200, 1, 10));
        k.paso();                                                          // t = 2: el teclado trabajó en [1, 2)
        assertTrue(k.teclado('\n'));                                       // fin forzado: IRQ1 levantada
        k.paso();                                                          // t = 3: atendida al inicio del tick
        for (Instantanea f : k.historial()) r.registrar(f);
        Instantanea ultima = k.instantanea();
        assertEquals(Instantanea.NINGUNO, ultima.dispositivos().stream()
                .filter(d -> d.dispositivo() == Dispositivo.TECLADO).findFirst().orElseThrow().atendiendo());   // servicio ya cerrado
        List<RegistroES.EventoInterrupcion> teclado = r.bitacora(3, Set.of(TipoInterrupcion.TECLADO), 100);
        assertEquals(List.of(new RegistroES.EventoInterrupcion(3, TipoInterrupcion.TECLADO, Accion.ATENDIDA, 1),
                new RegistroES.EventoInterrupcion(3, TipoInterrupcion.TECLADO, Accion.LEVANTADA, 1)), teclado);
        assertEquals(List.of(new Servicio(Dispositivo.TECLADO, 1, SolicitudES.Operacion.LEER, 1, 2)),
                r.servicios(Dispositivo.TECLADO, 3));                      // la tecla no suma trabajo
        assertEquals(1, r.atendidas(TipoInterrupcion.LLAMADA_SISTEMA, 3)); // la int 0x80 del pedido
        assertEquals(1, ultima.cpu().llamadasSistema());
    }

    @Test @DisplayName("[E9][E6] Tecla con IRQ1 enmascarada: se levanta una vez y se atiende al quitar la máscara")
    void teclaEnmascarada() {
        Kernel k = new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 2), new Pcb(2, 0, 8)));
        RegistroES r = new RegistroES();
        k.pic().enmascarar(TipoInterrupcion.TECLADO);
        k.paso();
        k.llamadaSistema(new SolicitudES(1, Dispositivo.TECLADO, SolicitudES.Operacion.LEER, 0x200, 1, 10));
        k.paso();
        assertTrue(k.teclado('\n'));
        k.paso();                                                          // t = 3: sigue pendiente
        k.paso();                                                          // t = 4
        k.pic().desenmascarar(TipoInterrupcion.TECLADO);
        k.paso();                                                          // t = 5: atendida
        for (Instantanea f : k.historial()) r.registrar(f);
        List<RegistroES.EventoInterrupcion> teclado = r.bitacora(5, Set.of(TipoInterrupcion.TECLADO), 100);
        assertEquals(List.of(new RegistroES.EventoInterrupcion(5, TipoInterrupcion.TECLADO, Accion.ATENDIDA, 1),
                new RegistroES.EventoInterrupcion(3, TipoInterrupcion.TECLADO, Accion.LEVANTADA, 1)), teclado);
        assertEquals(1, r.resumen(Dispositivo.TECLADO, 5).ticksOcupado());   // solo [1, 2): la espera no es trabajo
    }

    @Test @DisplayName("[I1] IVT: pedir el vector de null es un error del simulador, no una excepción genérica")
    void vectorInvalido() {
        org.junit.jupiter.api.Assertions.assertThrows(pe.uni.so.excepciones.ParametroInvalidoException.class, () -> TablaVectores.de(null));
    }

    @Test @DisplayName("[E1][P16] Al re-simular un tick antiguo, servicios y bitácora salen idénticos")
    void reSimular() {
        SesionSimulacion s = new SesionSimulacion(Maquina.porDefecto());
        s.avanzar(300);
        s.irA(150);
        Map<Dispositivo, List<Servicio>> antes = new EnumMap<>(Dispositivo.class);
        for (Dispositivo d : Dispositivo.values()) antes.put(d, s.registroES().servicios(d, 150));
        List<RegistroES.EventoInterrupcion> bit = s.registroES().bitacora(150, Set.of(), 10_000);
        s.irA(300);
        s.limitarHistorial(5);
        s.irA(150);
        for (Dispositivo d : Dispositivo.values()) assertEquals(antes.get(d), s.registroES().servicios(d, 150), d.toString());
        assertEquals(bit, s.registroES().bitacora(150, Set.of(), 10_000));
    }
}
