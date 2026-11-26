package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.comparador.Columna;
import pe.uni.so.comparador.Comparador;
import pe.uni.so.comparador.Resultado;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Configuracion.Carga;
import pe.uni.so.es.SolicitudES;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.motor.Kernel;
import pe.uni.so.motor.Momento;
import pe.uni.so.procesos.Pcb;
import pe.uni.so.sesion.PlanCarga;
import pe.uni.so.sesion.SesionSimulacion;

/** [E7] Carga/descarga de la región de memoria de cada E/S por DMA: bits R y M, fallos de página y bitácora. */
class DmaTest {

    private static final Configuracion BASE = Configuracion.porDefecto();

    private static Configuracion conCarga(Carga carga) {
        return new Configuracion(BASE.ram(), BASE.pagina(), BASE.cpu(), BASE.politica(), BASE.quantum(), BASE.costoDespacho(),
                BASE.grado(), BASE.estrategia(), carga, BASE.reemplazo(), BASE.dispositivos(), BASE.semilla());
    }

    /** Página del búfer de E/S del proceso (inicio de su segmento de datos) en la tabla de la foto. */
    private static GestorMemoria.TramoPaginas paginaBufer(Instantanea f, Pcb p) {
        long pagina = p.espacio().base(EspacioProceso.Segmento.DATOS) / f.memoria().bloque();
        return f.memoria().procesos().get(p.pid).tabla().stream()
                .filter(x -> x.paginaInicial() <= pagina && pagina <= x.paginaFinal()).findFirst().orElseThrow();
    }

    @Test @DisplayName("[E7] Lectura: al cerrar la E/S los datos entran al búfer y su página queda con R y M; la bitácora lo dice")
    void lecturaMarcaModificada() {
        Kernel k = BASE.crearKernel(PlanCarga.loteOficial().procesos(BASE.pagina().bytes));
        Instantanea.Suceso dma = null;
        Pcb p = null;
        while (!k.terminado() && dma == null) {
            int antes = k.dmaLecturas();
            k.paso();
            if (k.dmaLecturas() > antes) {
                dma = k.instantanea().sucesos().stream().filter(s -> s.momento() == Momento.C && s.detalle().contains("del dispositivo al búfer"))
                        .findFirst().orElseThrow();
                int pid = dma.pid();
                p = k.procesos().stream().filter(x -> x.pid == pid).findFirst().orElseThrow();
            }
        }
        assertTrue(dma.detalle().contains("DMA: 512 B"), dma.detalle());
        GestorMemoria.TramoPaginas pagina = paginaBufer(k.instantanea(), p);
        assertTrue(pagina.valida() && pagina.referenciada() && pagina.modificada());
    }

    @Test @DisplayName("[E7] Escritura: el búfer se copia al dispositivo; si el proceso no leyó antes, su página queda con R y sin M")
    void escrituraSoloReferencia() {
        Kernel k = BASE.crearKernel(PlanCarga.loteOficial().procesos(BASE.pagina().bytes));
        java.util.Set<Integer> leyeron = new java.util.HashSet<>();
        boolean visto = false;
        while (!k.terminado() && !visto) {
            k.paso();
            for (Instantanea.Suceso s : k.instantanea().sucesos()) {
                if (s.detalle().contains("del dispositivo al búfer")) leyeron.add(s.pid());
                else if (s.detalle().contains("al dispositivo)") && !leyeron.contains(s.pid())) {
                    Pcb p = k.procesos().stream().filter(x -> x.pid == s.pid()).findFirst().orElseThrow();
                    GestorMemoria.TramoPaginas pagina = paginaBufer(k.instantanea(), p);
                    assertTrue(pagina.referenciada());
                    assertFalse(pagina.modificada());
                    visto = true;
                }
            }
        }
        assertTrue(visto);
    }

    @Test @DisplayName("[E7][M1] Carga total: ningún fallo; bajo demanda: el primer acceso al búfer de cada proceso es un fallo de página")
    void fallosBajoDemanda() {
        for (Carga c : Carga.values()) {
            Configuracion conf = conCarga(c);
            Kernel k = conf.crearKernel(PlanCarga.loteOficial().procesos(conf.pagina().bytes));
            k.ejecutarTodo();
            assertEquals(793, k.t());                                         // la DMA no cambia los tiempos
            assertEquals(68, k.dmaLecturas());
            assertEquals(80, k.dmaEscrituras());
            int fallos = k.memoria().orElseThrow().fallosPagina();
            assertEquals(c == Carga.TOTAL ? 0 : 20, fallos, c.etiqueta);     // 20 procesos, un búfer cada uno
        }
    }

    @Test @DisplayName("[E7] Sin gestor de memoria no hay región que cargar: la E/S termina sin transferencia")
    void sinRegion() {
        Pcb p = new Pcb(1, 0, 5);
        Kernel k = new Kernel(new pe.uni.so.planificacion.Fcfs(), List.of(p));
        k.paso();
        k.llamadaSistema(new SolicitudES(1, pe.uni.so.es.Dispositivo.DISCO, SolicitudES.Operacion.LEER, 0, 512, 2));
        k.ejecutarTodo();
        assertEquals(0, k.dmaLecturas() + k.dmaEscrituras());
    }

    @Test @DisplayName("[E7][P17] Comparador: columna «Fallos de página» (0 con carga total, uno por proceso bajo demanda)")
    void columnaComparador() {
        Resultado total = Comparador.correr(new Comparador.Escenario(Resultado.Serie.MEMORIA, BASE), PlanCarga.loteOficial());
        Resultado demanda = Comparador.correr(new Comparador.Escenario(Resultado.Serie.MEMORIA, conCarga(Carga.BAJO_DEMANDA)),
                PlanCarga.loteOficial());
        assertEquals(0.0, Columna.FALLOS_PAGINA.valor(total));
        assertEquals(20.0, Columna.FALLOS_PAGINA.valor(demanda));
        assertTrue(Columna.FALLOS_PAGINA.menorEsMejor);
    }

    @Test @DisplayName("[E7][I3] Bajo demanda, cada fallo de página aparece en la bitácora de interrupciones (0x0E levantada y atendida)")
    void bitacoraDeInterrupciones() {
        SesionSimulacion s = new SesionSimulacion(
                new pe.uni.so.configuracion.Maquina("Bajo demanda", conCarga(Carga.BAJO_DEMANDA)));
        s.hastaElFinal();
        assertEquals(20, s.registroES().atendidas(pe.uni.so.interrupciones.TipoInterrupcion.FALLO_PAGINA, s.vista().t()));
    }

    /** Corre hasta que P2 tiene la CPU, le hace pedir la E/S dada y devuelve el detalle del cierre de esa E/S. */
    private static String cierreDePedidoManual(Kernel k, java.util.function.LongFunction<SolicitudES> pedido) {
        Pcb p2 = k.procesos().stream().filter(x -> x.pid == 2).findFirst().orElseThrow();
        int limiteCpu = k.t() + SesionSimulacion.LIMITE_TICKS;              // sin bucles infinitos si algo se rompe
        while (k.instantanea().cpu().enCpu() != 2) {
            if (k.t() >= limiteCpu) fail("P2 no obtuvo la CPU en " + SesionSimulacion.LIMITE_TICKS + " ticks (t = " + k.t() + ")");
            k.paso();
        }
        k.llamadaSistema(pedido.apply(p2.espacio().base(EspacioProceso.Segmento.DATOS)));
        int limiteCierre = k.t() + SesionSimulacion.LIMITE_TICKS;
        while (true) {
            if (k.t() >= limiteCierre)
                fail("Sin cierre de E/S de P2 tras " + SesionSimulacion.LIMITE_TICKS + " ticks (t = " + k.t() + ", estado " + p2.estado()
                        + ", disco: " + k.instantanea().dispositivos() + ")");
            k.paso();
            for (Instantanea.Suceso s : k.instantanea().sucesos())
                if (s.pid() == 2 && s.momento() == Momento.C) return s.detalle();
        }
    }

    @Test @DisplayName("[E7] Rango: solo la intersección de la solicitud con el segmento de datos (nada si no se cruzan)")
    void interseccion() {
        Kernel k = BASE.crearKernel(PlanCarga.loteOficial().procesos(BASE.pagina().bytes));
        String antes = cierreDePedidoManual(k, base -> new SolicitudES(2, pe.uni.so.es.Dispositivo.DISCO, SolicitudES.Operacion.LEER,
                (int) base - 100, 512, 3));
        assertTrue(antes.contains("DMA: 412 B del dispositivo al búfer"), antes);   // 100 B quedan antes del segmento

        Kernel fuera = BASE.crearKernel(PlanCarga.loteOficial().procesos(BASE.pagina().bytes));
        int lecturas = fuera.dmaLecturas();
        String nada = cierreDePedidoManual(fuera, base -> new SolicitudES(2, pe.uni.so.es.Dispositivo.DISCO, SolicitudES.Operacion.LEER,
                (int) base - 1000, 512, 3));
        assertFalse(nada.contains("DMA"), nada);
        assertEquals(lecturas, fuera.dmaLecturas());
    }

    @Test @DisplayName("[E7] Un búfer que cruza de página se copia por tramos de una página: la E/S se cierra (momento c) y ambas páginas quedan cargadas")
    void cruzaDePagina() {
        Configuracion demanda = conCarga(Carga.BAJO_DEMANDA);
        Kernel k = demanda.crearKernel(PlanCarga.loteOficial().procesos(demanda.pagina().bytes));
        long bloque = demanda.pagina().bytes;
        String d = cierreDePedidoManual(k, base -> new SolicitudES(2, pe.uni.so.es.Dispositivo.DISCO, SolicitudES.Operacion.LEER,
                (int) (base + 2 * bloque - 256), 512, 3));
        assertTrue(d.contains("DMA: 512 B") && d.contains("2 fallos de página"), d);
        Pcb p2 = k.procesos().stream().filter(x -> x.pid == 2).findFirst().orElseThrow();
        long pagina = (p2.espacio().base(EspacioProceso.Segmento.DATOS) + 2 * bloque) / bloque;
        for (long pg : new long[]{pagina - 1, pagina}) {
            GestorMemoria.TramoPaginas t = k.instantanea().memoria().procesos().get(2).tabla().stream()
                    .filter(x -> x.paginaInicial() <= pg && pg <= x.paginaFinal()).findFirst().orElseThrow();
            assertTrue(t.valida() && t.modificada(), "página " + pg);
        }
    }
}
