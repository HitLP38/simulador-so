package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.EstrategiaAsignacion;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.memoria.Reemplazo;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.motor.Kernel;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;
import pe.uni.so.sesion.MapaEstados;
import pe.uni.so.sesion.MapaEstados.Sala;
import pe.uni.so.sesion.PlanCarga;
import pe.uni.so.sesion.SesionSimulacion;
import pe.uni.so.sesion.SesionSimulacion.Origen;

class IngresoProcesosTest {

    private static final long KIB = 1024;

    private static SesionSimulacion lote() { return new SesionSimulacion(Maquina.porDefecto()); }

    private static Instantanea.Proceso ficha(Instantanea f, int pid) { return f.proceso(pid).orElseThrow(); }

    @Test @DisplayName("[P18] Ingreso manual en plena corrida: PID siguiente, aparece ya en la foto y entra por «Ingresado por usuario»")
    void ingresoManual() {
        SesionSimulacion s = lote();
        s.avanzar(10);
        int pid = s.ingresarManual(11, 20, 300 * KIB, 2);
        assertEquals(21, pid);
        assertEquals(10, s.vista().t());                                  // no avanza el tiempo
        Instantanea.Proceso p = ficha(s.vista(), pid);
        assertEquals(Estado.NUEVO, p.estado());
        assertTrue(p.usuario());
        assertEquals(2, p.prioridad());
        assertEquals(Sala.ORIGEN_USUARIO, Sala.de(p, s.vista().t()));
        assertEquals(1, MapaEstados.de(s.vista(), Politica.ROUND_ROBIN).sala(Sala.ORIGEN_USUARIO).cantidad());
        s.avanzar(1);
        assertEquals(Sala.CREACION, Sala.de(ficha(s.vista(), pid), s.vista().t()));   // llegó: pasa al módulo de creación
        s.hastaElFinal();
        assertTrue(s.terminada());
        assertEquals(Estado.TERMINADO, ficha(s.vista(), pid).estado());
        assertEquals(List.of(new SesionSimulacion.Ingreso(10, Origen.USUARIO, 11, 20, 300 * KIB, 2, 1)), s.ingresos());
    }

    @Test @DisplayName("[P14] «Generar N» en plena corrida: N procesos del SO que llegan desde el tick siguiente")
    void generarN() {
        SesionSimulacion s = lote();
        s.avanzar(5);
        List<Integer> pids = s.generar(5);
        assertEquals(List.of(21, 22, 23, 24, 25), pids);
        for (int pid : pids) {
            Instantanea.Proceso p = ficha(s.vista(), pid);
            assertFalse(p.usuario());
            assertTrue(p.llegada() >= 6);
            assertEquals(Sala.ORIGEN_SO, Sala.de(p, 5));
        }
        assertEquals(25, s.vista().procesos().size());
        s.hastaElFinal();
        assertTrue(s.terminada());
        assertThrows(ParametroInvalidoException.class, () -> s.generar(0));
        assertThrows(ParametroInvalidoException.class, () -> s.generar(SesionSimulacion.MAX_GENERAR + 1));
    }

    @Test @DisplayName("[P16][P18] Al volver a simular un tick antiguo se repiten los ingresos: las fotos son idénticas")
    void reSimularRepiteIngresos() {
        SesionSimulacion s = lote();
        s.avanzar(10);
        s.ingresarManual(12, 30, 500 * KIB, 1);
        s.avanzar(20);
        s.generar(3);
        s.avanzar(40);
        s.irA(15);
        Instantanea en15 = s.vista();
        s.irA(30);
        Instantanea en30 = s.vista();
        s.hastaElFinal();
        int fin = s.tActual();
        Instantanea ultima = s.vista();

        s.limitarHistorial(5);
        s.irA(15);                                                       // fuera del historial: re-simula
        assertEquals(en15.procesos(), s.vista().procesos());
        s.irA(30);                                                       // el ingreso de t = 30 se repite al llegar
        assertEquals(en30.procesos(), s.vista().procesos());
        assertEquals(24, s.vista().procesos().size());
        s.hastaElFinal();
        assertEquals(fin, s.tActual());
        assertEquals(ultima.procesos(), s.vista().procesos());
        assertEquals(2, s.ingresos().size());
    }

    @Test @DisplayName("[P18] Solo se ingresa desde el instante simulado y con llegada ≥ t; un ingreso inválido no consume PID")
    void validaciones() {
        SesionSimulacion s = lote();
        s.avanzar(10);
        s.retroceder(3);
        assertFalse(s.puedeIngresar());
        assertThrows(EventoInvalidoException.class, () -> s.ingresarManual(20, 20, 300 * KIB, 3));
        s.irA(10);
        assertTrue(s.puedeIngresar());
        assertThrows(ParametroInvalidoException.class, () -> s.ingresarManual(9, 20, 300 * KIB, 3));    // llegada pasada
        assertThrows(ParametroInvalidoException.class, () -> s.ingresarManual(10, 5, 300 * KIB, 3));    // ráfaga < 11
        assertThrows(ParametroInvalidoException.class, () -> s.ingresarManual(10, 20, 300 * KIB, 9));   // prioridad fuera de 1–5
        assertTrue(s.ingresos().isEmpty());
        assertEquals(21, s.ingresarManual(10, 20, 300 * KIB, 3));        // los intentos fallidos no gastaron el PID
    }

    @Test @DisplayName("[P18] Ingresar con la simulación terminada la reanuda; reiniciar descarta los ingresos")
    void reanudaYReinicia() {
        SesionSimulacion s = lote();
        s.hastaElFinal();
        assertFalse(s.puedeAvanzar());
        int fin = s.tActual();
        s.ingresarManual(fin, 15, 200 * KIB, 3);
        assertFalse(s.terminada());
        assertTrue(s.puedeAvanzar());
        s.hastaElFinal();
        assertTrue(s.terminada());
        assertTrue(s.tActual() > fin);
        s.reiniciar();
        assertTrue(s.ingresos().isEmpty());
        assertEquals(20, s.vista().procesos().size());
    }

    @Test @DisplayName("[P16][P18] Ingresar tras volver a simular el pasado descarta los ingresos de ese otro futuro")
    void ingresoEnElPasadoReSimulado() {
        SesionSimulacion s = lote();
        s.avanzar(30);
        s.ingresarManual(31, 20, 300 * KIB, 3);
        s.avanzar(30);
        s.limitarHistorial(5);
        s.irA(20);                                                       // re-simula hasta 20: el ingreso de t = 30 queda pendiente
        assertTrue(s.puedeIngresar());
        int pid = s.ingresarManual(21, 25, 300 * KIB, 4);
        assertEquals(21, pid);
        assertEquals(1, s.ingresos().size());
        assertEquals(20, s.ingresos().get(0).t());
        assertEquals(20, s.maximoAlcanzado());
        s.avanzar(20);
        assertEquals(21, s.vista().procesos().size());                   // el de t = 30 ya no se repite
    }

    @Test @DisplayName("[P18] Kernel.ingresar rechaza PID repetidos, procesos ya admitidos y llegadas pasadas")
    void kernelValida() {
        Pcb a = new Pcb(1, 0, 5);
        Kernel k = new Kernel(new Fcfs(), List.of(a));
        k.paso();
        k.paso();
        assertThrows(ParametroInvalidoException.class, () -> k.ingresar(List.of(new Pcb(1, 3, 4))));
        assertThrows(ParametroInvalidoException.class, () -> k.ingresar(List.of(a)));
        assertThrows(ParametroInvalidoException.class, () -> k.ingresar(List.of(new Pcb(2, 1, 4))));
        assertThrows(ParametroInvalidoException.class, () -> k.ingresar(List.of()));
        k.ingresar(List.of(new Pcb(2, 2, 4)));
        assertEquals(2, k.instantanea().procesos().size());
        assertEquals(2, k.instantanea(2).orElseThrow().procesos().size());   // la foto actual se actualiza
        k.ejecutarTodo();
        assertTrue(k.terminado());
        assertEquals(Estado.TERMINADO, k.procesos().get(1).estado());
        assertThrows(UnsupportedOperationException.class, () -> k.procesos().add(new Pcb(3, 9, 4)));
    }

    @Test @DisplayName("[M7][P18] Bajo demanda, el proceso que entra en plena corrida recibe una cuota proporcional sin tocar las demás")
    void cuotaEnCaliente() {
        long b = Configuracion.porDefecto().pagina().bytes;
        GestorMemoria g = new GestorMemoria(100, b, EstrategiaAsignacion.primerAjuste(), Reemplazo.FIFO);
        EspacioProceso e1 = new EspacioProceso(160 * KIB, 0.25, 0.25, b), e2 = new EspacioProceso(320 * KIB, 0.25, 0.25, b);
        g.repartirCuotas(java.util.Map.of(1, e1));
        int antes = g.cuota(1);
        g.agregarCuota(2, e2);
        assertEquals(antes, g.cuota(1));
        long s = e1.paginasTotales() + e2.paginasTotales();
        assertEquals(Math.min(e2.paginasTotales(), Math.max(1, (int) (e2.paginasTotales() * 100L / s))), g.cuota(2));
        assertThrows(EventoInvalidoException.class, () -> g.agregarCuota(2, e2));
        GestorMemoria total = new GestorMemoria(100, b, EstrategiaAsignacion.primerAjuste());
        assertThrows(EventoInvalidoException.class, () -> total.agregarCuota(3, e1));
    }

    @Test @DisplayName("[M7][P18] Una máquina con paginación bajo demanda admite y termina procesos ingresados en plena corrida")
    void sesionBajoDemanda() {
        Configuracion d = Configuracion.porDefecto();
        Configuracion c = new Configuracion(d.ram(), d.pagina(), d.cpu(), d.politica(), d.quantum(), d.costoDespacho(), d.grado(),
                d.estrategia(), Configuracion.Carga.BAJO_DEMANDA, d.reemplazo(), d.dispositivos(), d.semilla());
        SesionSimulacion s = new SesionSimulacion(new Maquina("Bajo demanda", c));
        s.avanzar(15);
        s.generar(4);
        s.ingresarManual(16, 25, 400 * KIB, 2);
        assertTrue(ficha(s.vista(), 25).cuota() > 0);
        s.hastaElFinal();
        assertTrue(s.terminada());
        assertEquals(25, s.vista().procesos().size());
    }

    @Test @DisplayName("[P14][P17][P18] Ventana de carga: lote oficial, N aleatorios con semilla o tabla manual; cada llamada da procesos iguales")
    void planesDeCarga() {
        long b = Configuracion.porDefecto().pagina().bytes;
        assertEquals(20, PlanCarga.loteOficial().procesos(b).size());
        PlanCarga al = PlanCarga.aleatoria(50, 7);
        List<Pcb> a1 = al.procesos(b), a2 = al.procesos(b);
        assertEquals(50, a1.size());
        assertEquals(0, a1.get(0).llegada);
        for (int i = 0; i < a1.size(); i++) {
            assertEquals(a1.get(i).llegada, a2.get(i).llegada);
            assertEquals(a1.get(i).burst, a2.get(i).burst);
            assertFalse(a1.get(i).ingresadoPorUsuario());
        }
        PlanCarga ma = PlanCarga.manual(List.of(new PlanCarga.Fila(0, 20, 300, 2), new PlanCarga.Fila(4, 15, 128, 5)), 3);
        List<Pcb> m = ma.procesos(b);
        assertEquals(List.of(1, 2), m.stream().map(p -> p.pid).toList());
        assertTrue(m.stream().allMatch(Pcb::ingresadoPorUsuario));
        assertEquals("2 procesos ingresados por el usuario", ma.descripcion());
        assertEquals("50 procesos aleatorios (semilla 7)", al.descripcion());
        assertThrows(ParametroInvalidoException.class, () -> PlanCarga.aleatoria(0, 1));
        assertThrows(ParametroInvalidoException.class, () -> PlanCarga.aleatoria(PlanCarga.MAX_ALEATORIOS + 1, 1));
        assertThrows(ParametroInvalidoException.class, () -> PlanCarga.aleatoria(10, -1));
        assertThrows(ParametroInvalidoException.class, () -> PlanCarga.manual(List.of(), 1));
        assertThrows(ParametroInvalidoException.class, () -> new PlanCarga.Fila(0, 10, 300, 3));     // ráfaga < 11
        assertThrows(ParametroInvalidoException.class, () -> new PlanCarga.Fila(0, 20, 64, 3));      // ejecutable < 128 KiB
        assertThrows(ParametroInvalidoException.class, () -> new PlanCarga.Fila(-1, 20, 300, 3));
        assertThrows(ParametroInvalidoException.class, () -> new PlanCarga.Fila(0, 20, 300, 6));
    }

    @Test @DisplayName("[P18][G5] Una carga manual corre en la máquina y sus procesos entran por «Ingresado por usuario»")
    void sesionConCargaManual() {
        Maquina maq = Maquina.porDefecto();
        PlanCarga plan = PlanCarga.manual(List.of(new PlanCarga.Fila(0, 20, 300, 2), new PlanCarga.Fila(6, 15, 400, 1)), 5);
        SesionSimulacion s = new SesionSimulacion(maq, () -> plan.procesos(maq.configuracion().pagina().bytes));
        assertEquals(Sala.ORIGEN_USUARIO, Sala.de(ficha(s.vista(), 2), 0));
        s.hastaElFinal();
        assertTrue(s.terminada());
    }

    @Test @DisplayName("[P10][P14] Con 1000 procesos aleatorios la arena muestra los que caben y cuenta el resto (+N)")
    void milProcesos() {
        Maquina maq = Maquina.porDefecto();
        PlanCarga plan = PlanCarga.aleatoria(1000, 11);
        SesionSimulacion s = new SesionSimulacion(maq, () -> plan.procesos(maq.configuracion().pagina().bytes));
        s.avanzar(30);
        MapaEstados.Diagrama d = MapaEstados.de(s.vista(), Politica.ROUND_ROBIN);
        assertEquals(1000, d.sala(Sala.PCB).cantidad());
        int visibles = 0, ocultos = 0;
        for (MapaEstados.SalaDibujo sd : d.salas()) if (sd.sala() != Sala.PCB) { visibles += sd.cantidad(); ocultos += sd.exceso(); }
        assertEquals(1000, visibles);
        assertTrue(d.sala(Sala.ORIGEN_SO).exceso() > 0);
        assertTrue(ocultos > 0);
    }
}
