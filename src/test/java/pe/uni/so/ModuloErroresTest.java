package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.aleatorio.Ecuaciones;
import pe.uni.so.comparador.Comparador;
import pe.uni.so.comparador.Comparador.Escenario;
import pe.uni.so.comparador.Resultado;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.interrupciones.TipoInterrupcion;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.motor.Kernel;
import pe.uni.so.motor.Momento;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.PlanificadorLargoPlazo;
import pe.uni.so.procesos.CampoPcb;
import pe.uni.so.procesos.CodigoError;
import pe.uni.so.procesos.ModuloErrores;
import pe.uni.so.procesos.Pcb;
import pe.uni.so.procesos.Estado;
import pe.uni.so.sesion.PlanCarga;
import pe.uni.so.sesion.RegistroES;
import pe.uni.so.sesion.SesionSimulacion;

/** [P9][P10] Módulo de errores: ecuación 7, excepción síncrona, aborto con código e inyección manual. */
class ModuloErroresTest {

    private static final Configuracion BASE = Configuracion.porDefecto();

    /** La máquina por defecto con otra semilla (con 2026 el errado del primer bloque es P8, dentro del lote oficial). */
    private static Configuracion conSemilla(long semilla) {
        return new Configuracion(BASE.ram(), BASE.pagina(), BASE.cpu(), BASE.politica(), BASE.quantum(), BASE.costoDespacho(),
                BASE.grado(), BASE.estrategia(), BASE.carga(), BASE.reemplazo(), BASE.dispositivos(), semilla);
    }

    @Test @DisplayName("[P10] Ec. 7: exactamente un errado por bloque de 200, en su bloque, con instante 1..b−1 y código E01–E03")
    void unoPorBloque() {
        ModuloErrores m = new ModuloErrores(42);
        Set<CodigoError> codigos = new HashSet<>();
        for (int k = 0; k < 50; k++) {
            int e = m.posicionErrado(k);
            assertTrue(e >= 200 * k && e < 200 * (k + 1), "bloque " + k + ": " + e);
            int errados = 0;
            for (int i = 200 * k; i < 200 * (k + 1); i++) if (m.errado(i)) errados++;
            assertEquals(1, errados);
            ModuloErrores.Fallo f = m.fallo(e, 35).orElseThrow();
            assertTrue(f.instruccion() >= 1 && f.instruccion() <= 34, "instante " + f.instruccion());
            assertTrue(CodigoError.DE_EXCEPCION.contains(f.codigo()));
            codigos.add(f.codigo());
        }
        assertEquals(Set.copyOf(CodigoError.DE_EXCEPCION), codigos);           // en 50 bloques salen los tres códigos
        assertTrue(m.fallo((m.posicionErrado(0) + 1) % 200, 35).isEmpty());   // los demás del bloque no fallan
    }

    @Test @DisplayName("[P10] Ec. 7 por partes: eₖ = 200k + ⌊200U⌋, instante 1 + ⌊U(b−1)⌋ y código por tercios de U")
    void ecuacion7() {
        assertEquals(0, Ecuaciones.posicionErrado(0, 0.0));
        assertEquals(399, Ecuaciones.posicionErrado(1, 0.999));
        assertEquals(1, Ecuaciones.instanteFallo(11, 0.0));
        assertEquals(10, Ecuaciones.instanteFallo(11, 0.999));
        assertEquals(1, Ecuaciones.instanteFallo(1, 0.7));
        assertEquals(CodigoError.E01, CodigoError.conU(0.10));
        assertEquals(CodigoError.E02, CodigoError.conU(0.50));
        assertEquals(CodigoError.E03, CodigoError.conU(0.99));
        assertEquals(TipoInterrupcion.ACCESO_ILEGAL, CodigoError.E01.excepcion);
        assertNull(CodigoError.E04.excepcion);
    }

    @Test @DisplayName("[P10] Determinista: depende de la semilla y de la posición, no del orden de consulta")
    void determinista() {
        ModuloErrores a = new ModuloErrores(7), b = new ModuloErrores(7);
        int e3 = b.posicionErrado(3);                                          // b consulta primero el bloque 3
        for (int k = 0; k < 5; k++) assertEquals(a.posicionErrado(k), b.posicionErrado(k));
        assertEquals(e3, a.posicionErrado(3));
        assertTrue(java.util.stream.IntStream.range(0, 20).anyMatch(k -> new ModuloErrores(8).posicionErrado(k) != a.posicionErrado(k)));
        assertThrows(ParametroInvalidoException.class, () -> new ModuloErrores(-1));
        assertThrows(ParametroInvalidoException.class, () -> a.errado(-1));
    }

    @Test @DisplayName("[P9] La instrucción del fallo no se completa: excepción, Terminado con su código en la PCB y memoria liberada")
    void abortaConCodigo() {
        Pcb p = new Pcb(1, 0, 10, 0, 6), otro = new Pcb(2, 0, 3, 0, 6);
        p.planificarFallo(4, CodigoError.E02);                                 // completa 3 y falla en la 4.ª
        Kernel k = new Kernel(new Fcfs(), List.of(p, otro), new PlanificadorLargoPlazo(10));
        for (int i = 0; i < 4; i++) k.paso();
        assertEquals(Estado.TERMINADO, p.estado());
        assertEquals(3, p.ejecutadas());
        assertEquals(4, p.fin());
        assertEquals(CodigoError.E02, p.error());
        assertEquals(2 << 16, p.leer(CampoPcb.ERROR_E_INTERRUPCIONES) & 0xFFFF0000);
        assertEquals(Map.of(TipoInterrupcion.DIVISION_CERO, 1), k.excepciones());
        assertEquals(1, k.abortados());
        k.ejecutarTodo();                                                      // P2 entra cuando P1 libera sus 6 de 10
        assertEquals(Estado.TERMINADO, otro.estado());
        assertNull(otro.error());
        Instantanea.Suceso s = k.instantanea(4).orElseThrow().sucesos().stream().filter(x -> x.pid() == 1).findFirst().orElseThrow();
        assertEquals(Momento.E, s.momento());
        assertTrue(s.detalle().contains("0x00") && s.detalle().contains("E02"), s.detalle());
    }

    @Test @DisplayName("[P9][G5] CPU rápida: completa las instrucciones previas en el mismo tick y falla en la indicada; lenta: espera a completarla")
    void velocidadDeCpu() {
        Pcb rapida = new Pcb(1, 0, 20);
        rapida.planificarFallo(6, CodigoError.E03);
        Kernel k = new Kernel(new Fcfs(), List.of(rapida), null, 0, 4);       // 4 instrucciones por tick
        k.paso();
        assertEquals(4, rapida.ejecutadas());
        k.paso();
        assertEquals(5, rapida.ejecutadas());
        assertEquals(CodigoError.E03, rapida.error());

        Pcb lenta = new Pcb(1, 0, 20);
        lenta.planificarFallo(1, CodigoError.E01);
        Kernel l = new Kernel(new Fcfs(), List.of(lenta), null, 0, 0.5);      // una instrucción cada 2 ticks
        l.paso();
        assertEquals(Estado.EJECUTANDO, lenta.estado());
        l.paso();
        assertEquals(CodigoError.E01, lenta.error());
        assertEquals(0, lenta.ejecutadas());
    }

    @Test @DisplayName("[P9] Validaciones del fallo: solo E01–E03, instrucción pendiente y proceso no terminado")
    void validaciones() {
        Pcb p = new Pcb(1, 0, 5);
        assertThrows(ParametroInvalidoException.class, () -> p.planificarFallo(2, CodigoError.E04));
        assertThrows(ParametroInvalidoException.class, () -> p.planificarFallo(0, CodigoError.E01));
        assertThrows(ParametroInvalidoException.class, () -> p.planificarFallo(6, CodigoError.E01));
        Kernel k = new Kernel(new Fcfs(), List.of(p));
        assertThrows(EventoInvalidoException.class, () -> k.provocarError(CodigoError.E01));   // CPU libre en t = 0
        k.ejecutarTodo();
        assertThrows(EventoInvalidoException.class, () -> p.planificarFallo(5, CodigoError.E01));
        k.activarErrores(new ModuloErrores(1));
        assertThrows(EventoInvalidoException.class, () -> k.activarErrores(new ModuloErrores(1)));
    }

    @Test @DisplayName("[P10] Lote oficial con la semilla 2026: falla P8 (el errado del primer bloque); con la 42, ninguno de los 20")
    void loteOficial() {
        Kernel k = conSemilla(2026).crearKernel(PlanCarga.loteOficial().procesos(BASE.pagina().bytes));
        k.ejecutarTodo();
        List<Pcb> errados = k.procesos().stream().filter(p -> p.error() != null).toList();
        assertEquals(1, errados.size());
        assertEquals(8, errados.get(0).pid);
        assertEquals(1, k.abortados());

        Kernel d = BASE.crearKernel(PlanCarga.loteOficial().procesos(BASE.pagina().bytes));
        d.ejecutarTodo();
        assertEquals(0, d.abortados());
        assertEquals(793, d.t());
    }

    @Test @DisplayName("[P10] Corrida de 1000 procesos: 5 abortados (0,5 %), los mismos con todas las políticas y estrategias")
    void milProcesos() {
        PlanCarga carga = PlanCarga.aleatoria(1000, 42);
        List<Resultado> rs = Comparador.correr(Comparador.escenarios(BASE, List.of(Configuracion.Politica.FCFS, Configuracion.Politica.SRTF),
                List.of(Configuracion.Estrategia.PEOR_AJUSTE)), carga, null, null);
        for (Resultado r : rs) {
            assertTrue(r.completa(), r.nota());
            assertEquals(5, r.conError(), r.configuracion());
            assertEquals(1000, r.terminados());
        }
    }

    @Test @DisplayName("[P9][I3] La excepción aparece en la bitácora de interrupciones (levantada y atendida) y en las métricas")
    void bitacoraYMetricas() {
        SesionSimulacion s = new SesionSimulacion(new Maquina("Semilla 2026", conSemilla(2026)));
        s.hastaElFinal();
        RegistroES r = s.registroES();
        int t = s.vista().t();
        int excepciones = 0;
        for (TipoInterrupcion x : List.of(TipoInterrupcion.DIVISION_CERO, TipoInterrupcion.INSTRUCCION_INVALIDA, TipoInterrupcion.ACCESO_ILEGAL))
            excepciones += r.atendidas(x, t);
        assertEquals(1, excepciones);
        assertEquals(1, s.registro().metricas(s.vista()).conError());
        assertTrue(s.vista().proceso(8).orElseThrow().tiene(Instantanea.Insignia.ERROR));
    }

    @Test @DisplayName("[P9] Provocar error: el proceso en CPU falla en su próxima instrucción y se repite igual al re-simular")
    void provocarYRepetir() {
        SesionSimulacion s = new SesionSimulacion(Maquina.porDefecto());
        s.avanzar(5);
        assertTrue(s.puedeProvocarError());
        int pid = s.vista().cpu().enCpu();
        int hechas = s.vista().proceso(pid).orElseThrow().burst() - s.vista().proceso(pid).orElseThrow().restante();
        assertEquals(pid, s.provocarError(CodigoError.E01));
        assertThrows(ParametroInvalidoException.class, () -> s.provocarError(CodigoError.E04));
        s.avanzar(1);
        Instantanea.Proceso p = s.vista().proceso(pid).orElseThrow();
        assertEquals(Estado.TERMINADO, p.estado());
        assertEquals(CodigoError.E01, p.error());
        assertEquals(hechas, p.burst() - p.restante());                       // la instrucción que falló no se completó
        assertEquals(1, s.erroresProvocados().size());
        s.hastaElFinal();
        int fin = s.vista().t();

        s.limitarHistorial(1);                                                 // volver a t = 3 obliga a re-simular
        s.irA(3);
        s.hastaElFinal();
        assertEquals(fin, s.vista().t());
        assertEquals(CodigoError.E01, s.vista().proceso(pid).orElseThrow().error());

        s.irA(3);                                                              // re-simula hasta t = 3: el error de t = 5 aún no se repitió
        assertTrue(s.enVivo());
        assertTrue(s.puedeProvocarError());
        s.provocarError(CodigoError.E03);                                      // actuar antes descarta lo que venía después
        assertEquals(1, s.erroresProvocados().size());
        assertEquals(new SesionSimulacion.ErrorProvocado(3, s.vista().cpu().enCpu(), CodigoError.E03), s.erroresProvocados().get(0));
        s.reiniciar();
        assertTrue(s.erroresProvocados().isEmpty());
        assertFalse(s.puedeProvocarError());                                   // t = 0: CPU libre
    }
}
