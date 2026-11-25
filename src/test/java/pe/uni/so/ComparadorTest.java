package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import pe.uni.so.procesos.Estado;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.PlanificadorLargoPlazo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.uni.so.comparador.Columna;
import pe.uni.so.comparador.Comparador;
import pe.uni.so.comparador.Comparador.Escenario;
import pe.uni.so.comparador.ExportadorCsv;
import pe.uni.so.comparador.Resultado;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Configuracion.Carga;
import pe.uni.so.configuracion.Configuracion.Estrategia;
import pe.uni.so.configuracion.Configuracion.Pagina;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.configuracion.Configuracion.Ram;
import pe.uni.so.es.VelocidadDispositivo;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.Reemplazo;
import pe.uni.so.motor.Kernel;
import pe.uni.so.planificacion.Metricas;
import pe.uni.so.procesos.Pcb;
import pe.uni.so.sesion.PlanCarga;

class ComparadorTest {

    private static final Configuracion BASE = Configuracion.porDefecto();

    @Test @DisplayName("[P17][M8] Escenarios: una corrida por política y una por estrategia; solo cambia lo comparado")
    void escenarios() {
        List<Escenario> e = Comparador.escenarios(BASE, EnumSet.allOf(Politica.class), EnumSet.allOf(Estrategia.class));
        assertEquals(Politica.values().length + Estrategia.values().length, e.size());
        for (int i = 0; i < Politica.values().length; i++) {
            Configuracion c = e.get(i).configuracion();
            assertEquals(Resultado.Serie.PLANIFICACION, e.get(i).serie());
            assertEquals(Politica.values()[i], c.politica());
            assertEquals(BASE.estrategia(), c.estrategia());
            assertEquals(BASE.semilla(), c.semilla());
            assertEquals(BASE.quantum(), c.quantum());
        }
        Escenario ultimo = e.get(e.size() - 1);
        assertEquals(Resultado.Serie.MEMORIA, ultimo.serie());
        assertEquals(Estrategia.PEOR_AJUSTE, ultimo.configuracion().estrategia());
        assertEquals(BASE.politica(), ultimo.configuracion().politica());
        assertThrows(ParametroInvalidoException.class, () -> Comparador.escenarios(BASE, Set.of(), Set.of()));
        assertThrows(ParametroInvalidoException.class, () -> Comparador.correr(List.of(), PlanCarga.loteOficial(), null, null));
    }

    @Test @DisplayName("[P17][P16] El modo rápido (sin fotos) da la misma corrida que la simulación con fotos")
    void modoRapidoIgualAlNormal() {
        List<Pcb> procesos = PlanCarga.loteOficial().procesos(BASE.pagina().bytes);
        Kernel normal = BASE.crearKernel(procesos);
        normal.ejecutarTodo();
        Resultado r = Comparador.correr(new Escenario(Resultado.Serie.PLANIFICACION, BASE), PlanCarga.loteOficial());
        assertTrue(r.completa(), r.nota());
        assertEquals(793, r.tFinal());                                    // el lote oficial en la máquina del curso
        assertEquals(normal.t(), r.tFinal());
        assertEquals(20, r.procesos());
        assertEquals(Metricas.esperaMedia(normal.procesos()), r.esperaMedia(), 1e-9);
        assertEquals(Metricas.respuestaMedia(normal.procesos()), r.respuestaMedia(), 1e-9);
        assertEquals(Metricas.retornoMedio(normal.procesos()), r.retornoMedio(), 1e-9);
        assertEquals(normal.despachador().despachos(), r.cambiosContexto());
        assertEquals(normal.desalojos(), r.expropiaciones());
        assertEquals(normal.finesDeQuantum(), r.finesQuantum());
        assertEquals(68, r.finesQuantum());                               // el TIMER del lote oficial (PR #25)
        long ocupados = java.util.Arrays.stream(normal.gantt().split(" ")).filter(x -> !x.equals("-")).count();
        assertEquals(100.0 * ocupados / normal.t(), r.usoCpu(), 1e-9);
        assertEquals(normal.memoria().orElseThrow().comparaciones(), r.comparaciones());
    }

    @Test @DisplayName("[P17] Misma carga y configuración ⇒ mismo resultado (determinista con semilla)")
    void determinista() {
        Escenario e = new Escenario(Resultado.Serie.PLANIFICACION, BASE);
        PlanCarga carga = PlanCarga.aleatoria(60, 7);
        Resultado a = Comparador.correr(e, carga), b = Comparador.correr(e, carga);
        assertEquals(a.tFinal(), b.tFinal());
        assertEquals(a.esperaMedia(), b.esperaMedia());
        assertEquals(a.comparaciones(), b.comparaciones());
        assertEquals(a.fragExternaMedia(), b.fragExternaMedia());
    }

    @Test @DisplayName("[P17] Con todas las políticas, cada fila es completa y FCFS no expropia; SRTF responde en promedio mejor que FCFS")
    void todasLasPoliticas() {
        List<Resultado> rs = Comparador.correr(Comparador.escenarios(BASE, EnumSet.allOf(Politica.class), Set.of()),
                PlanCarga.loteOficial(), null, null);
        assertEquals(Politica.values().length, rs.size());
        assertTrue(rs.stream().allMatch(Resultado::completa));
        Resultado fcfs = rs.get(Politica.FCFS.ordinal()), srtf = rs.get(Politica.SRTF.ordinal());
        assertEquals(0, fcfs.expropiaciones());
        assertEquals(0, fcfs.finesQuantum());
        assertTrue(rs.get(Politica.ROUND_ROBIN.ordinal()).finesQuantum() > 0);
        assertTrue(srtf.esperaMedia() <= fcfs.esperaMedia());
        assertTrue(rs.get(Politica.ROUND_ROBIN.ordinal()).politica().contains("q = 4"));
        assertTrue(rs.stream().allMatch(r -> r.usoCpu() > 0 && r.usoCpu() <= 100));
    }

    @Test @DisplayName("[M8] Memoria: mejor y peor ajuste revisan todos los huecos (≥ comparaciones que primer ajuste); fragmentación ≥ 0")
    void estrategiasDeMemoria() {
        List<Resultado> rs = Comparador.correr(Comparador.escenarios(BASE, Set.of(), EnumSet.allOf(Estrategia.class)),
                PlanCarga.aleatoria(80, 11), null, null);
        assertEquals(3, rs.size());
        Resultado primer = rs.get(0), mejor = rs.get(1), peor = rs.get(2);
        assertEquals("Primer ajuste", primer.estrategia());
        assertTrue(mejor.comparaciones() >= primer.comparaciones());
        assertTrue(peor.comparaciones() >= primer.comparaciones());
        for (Resultado r : rs) {
            assertTrue(r.completa(), r.nota());
            assertEquals(Resultado.Serie.MEMORIA, r.serie());
            assertTrue(r.fragExternaMedia() >= 0 && r.fragExternaMaxima() >= r.fragExternaMedia());
            assertTrue(r.fragInternaMediaKib() >= 0);
            assertTrue(r.esperaronMemoria() >= 0 && r.esperaronMemoria() <= r.procesos());
        }
    }

    @Test @DisplayName("[M8] RAM chica: peor ajuste fragmenta más que primer ajuste; la espera por el grado no cuenta como espera de memoria")
    void ramChica() {
        Configuracion chica = new Configuracion(Ram.MIB_128, Pagina.KIB_64, Configuracion.Cpu.GHZ_1, Politica.ROUND_ROBIN, 4, 0.1, 10,
                Estrategia.PRIMER_AJUSTE, Carga.TOTAL, Reemplazo.LRU, Configuracion.dispositivos(VelocidadDispositivo.NORMAL), 42);
        List<Resultado> rs = Comparador.correr(Comparador.escenarios(chica, Set.of(), EnumSet.allOf(Estrategia.class)),
                PlanCarga.aleatoria(200, 3), null, null);
        assertTrue(rs.stream().allMatch(Resultado::completa));
        assertEquals(0, rs.get(0).esperaronMemoria());                   // esperan en Nuevo, pero por el grado (10), no por memoria
        assertTrue(rs.get(2).fragExternaMedia() > rs.get(0).fragExternaMedia());
        Configuracion demanda = new Configuracion(Ram.MIB_128, Pagina.KIB_64, Configuracion.Cpu.GHZ_1, Politica.ROUND_ROBIN, 4, 0.1, 10,
                Estrategia.PRIMER_AJUSTE, Carga.BAJO_DEMANDA, Reemplazo.LRU, Configuracion.dispositivos(VelocidadDispositivo.NORMAL), 42);
        Resultado r = Comparador.correr(new Escenario(Resultado.Serie.MEMORIA, demanda), PlanCarga.aleatoria(120, 3));
        assertTrue(r.completa(), r.nota());
        assertTrue(r.comparaciones() > 0);
    }

    @Test @DisplayName("[P10][P17] Estrés: 1000 procesos aleatorios terminan todos en el modo rápido y se mide el tiempo real")
    void estresMilProcesos() {
        Resultado r = Comparador.correr(new Escenario(Resultado.Serie.PLANIFICACION, BASE), PlanCarga.aleatoria(1000, 42));
        assertTrue(r.completa(), r.nota());
        assertEquals(1000, r.procesos());
        assertEquals(1000, r.terminados());
        assertTrue(r.tFinal() > 10_000);
        assertTrue(r.milisegundos() >= 0);
        assertTrue(r.milisegundos() < 60_000, "1000 procesos en menos de un minuto (" + r.milisegundos() + " ms)");
    }

    @Test @DisplayName("[P17] Una carga que no cabe en la máquina no detiene la comparación: la fila lleva la nota")
    void cargaQueNoCabe() {
        Configuracion diminuta = new Configuracion(Ram.MIB_128, Pagina.MIB_2, Configuracion.Cpu.GHZ_1, Politica.FCFS, 4, 0.1, 10,
                Estrategia.PRIMER_AJUSTE, Carga.BAJO_DEMANDA, Reemplazo.FIFO, Configuracion.dispositivos(VelocidadDispositivo.NORMAL), 42);
        Resultado r = Comparador.correr(new Escenario(Resultado.Serie.PLANIFICACION, diminuta), PlanCarga.aleatoria(100, 1));
        assertFalse(r.completa());
        assertFalse(r.nota().isEmpty());
        assertEquals(100, r.procesos());
        assertEquals(0, r.terminados());
    }

    @Test @DisplayName("[P17] Cancelar: se devuelven las corridas hechas y se avisa de cada una al terminar")
    void cancelar() {
        AtomicInteger avisos = new AtomicInteger();
        List<Resultado> rs = Comparador.correr(Comparador.escenarios(BASE, EnumSet.allOf(Politica.class), Set.of()),
                PlanCarga.loteOficial(), r -> avisos.incrementAndGet(), () -> avisos.get() >= 2);
        assertEquals(2, rs.size());
        assertEquals(2, avisos.get());
    }

    @Test @DisplayName("[P17][P16] Kernel: ejecutarHasta se detiene en el límite; sin fotos la última foto queda donde se apagaron")
    void kernelHastaYSinFotos() {
        Kernel k = BASE.crearKernel(PlanCarga.loteOficial().procesos(BASE.pagina().bytes));
        k.paso();
        k.apagarFotos();
        assertFalse(k.ejecutarHasta(100));
        assertEquals(100, k.t());
        assertEquals(1, k.instantanea().t());
        assertTrue(k.ejecutarHasta(Integer.MAX_VALUE));
        assertEquals(793, k.t());
    }

    @Test @DisplayName("[M8] Esperar memoria ≠ esperar por el grado: con RAM de sobra y grado 1 nadie espera memoria")
    void esperasPorGradoNoCuentan() {
        Configuracion grado1 = new Configuracion(BASE.ram(), BASE.pagina(), BASE.cpu(), BASE.politica(), BASE.quantum(), BASE.costoDespacho(),
                Configuracion.GRADO_MINIMO, BASE.estrategia(), BASE.carga(), BASE.reemplazo(), BASE.dispositivos(), BASE.semilla());
        Resultado r = Comparador.correr(new Escenario(Resultado.Serie.MEMORIA, grado1), PlanCarga.aleatoria(60, 5));
        assertTrue(r.completa(), r.nota());
        assertEquals(0, r.esperaronMemoria());                              // esperaron, pero por el grado
        assertEquals(0.0, r.esperaMemoriaMedia());
        Kernel k = grado1.crearKernel(PlanCarga.aleatoria(60, 5).procesos(grado1.pagina().bytes));
        assertTrue(k.ejecutarHasta(Integer.MAX_VALUE));
        assertTrue(k.esperasPorMemoria().isEmpty());
    }

    @Test @DisplayName("[M8][P16] Kernel: cuenta una vez por instante los ticks sin entrar por memoria (no por el grado), con ejecutarTodo o con paso")
    void esperasPorMemoriaEnElKernel() {
        // P2 espera a que P1 libere sus 8 de 10: t = 0 y t = 1, exactamente 2 ticks, se corra como se corra.
        Kernel memoria = new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 2, 0, 8), new Pcb(2, 0, 2, 0, 8)), new PlanificadorLargoPlazo(10));
        memoria.ejecutarTodo();                                           // ejecutarHasta: momentoD y luego paso en el mismo t
        assertFalse(memoria.esperasPorMemoria().containsKey(1));
        assertEquals(2, memoria.esperasPorMemoria().get(2));
        Kernel aPasos = new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 2, 0, 8), new Pcb(2, 0, 2, 0, 8)), new PlanificadorLargoPlazo(10));
        while (!aPasos.terminado()) aPasos.paso();
        assertEquals(2, aPasos.esperasPorMemoria().get(2));
        Kernel grado = new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 2, 0, 8), new Pcb(2, 0, 2, 0, 8)), new PlanificadorLargoPlazo(100, 1));
        grado.ejecutarTodo();
        assertTrue(grado.esperasPorMemoria().isEmpty());                 // P2 también espera, pero por el grado 1
        assertEquals(2, grado.procesos().stream().filter(p -> p.estado() == Estado.TERMINADO).count());
    }

    private static Resultado fila(Resultado.Serie serie, String nombre, double espera, long comparaciones, String nota) {
        return new Resultado(serie, nombre, nombre, 5, nota.isEmpty() ? 5 : 2, 0, 100, espera, 1, 2, 50, 1, 3, 4, 0, comparaciones,
                0, 0, 0, 0, 0, 0, 10, nota);
    }

    @Test @DisplayName("[G1] Mejor celda: por serie, solo entre corridas completas y solo si el ganador es único")
    void mejorCelda() {
        Resultado a = fila(Resultado.Serie.PLANIFICACION, "A", 3.0, 9, "");
        Resultado b = fila(Resultado.Serie.PLANIFICACION, "B", 1.5, 9, "");
        Resultado incompleta = fila(Resultado.Serie.PLANIFICACION, "C", 0.5, 1, "Atascada");
        Resultado m = fila(Resultado.Serie.MEMORIA, "M", 0.1, 2, "");
        Resultado n = fila(Resultado.Serie.MEMORIA, "N", 0.1, 7, "");
        List<Resultado> todas = List.of(a, b, incompleta, m, n);
        assertTrue(Columna.ESPERA.esMejor(b, todas));
        assertFalse(Columna.ESPERA.esMejor(a, todas));
        assertFalse(Columna.ESPERA.esMejor(incompleta, todas));            // la incompleta no compite
        assertFalse(Columna.ESPERA.esMejor(m, todas));                     // espera se marca solo en planificación
        assertTrue(Columna.COMPARACIONES.esMejor(m, todas));
        assertFalse(Columna.COMPARACIONES.esMejor(a, todas));             // empate A = B: no se marca
        Resultado d = fila(Resultado.Serie.PLANIFICACION, "D", 1.5, 9, "");   // B y D comparten la mejor espera (1,5 · 1,5 · 3)
        List<Resultado> empate = List.of(a, b, d);
        assertTrue(Double.isNaN(Columna.ESPERA.mejor(empate, Resultado.Serie.PLANIFICACION)));
        assertFalse(Columna.ESPERA.esMejor(b, empate));
        assertFalse(Columna.ESPERA.esMejor(d, empate));
        assertTrue(Double.isNaN(Columna.CAMBIOS.mejor(todas, Resultado.Serie.PLANIFICACION)));
        assertFalse(Columna.MILISEGUNDOS.seCompara(Resultado.Serie.MEMORIA));
        assertFalse(Columna.USO_CPU.menorEsMejor);
    }

    @Test @DisplayName("[G1] CSV: BOM, «;», coma decimal, parámetros, una fila por corrida, celdas protegidas y reemplazo sin temporales")
    void csv(@TempDir Path dir) throws Exception {
        Resultado a = fila(Resultado.Serie.PLANIFICACION, "FCFS", 12.345, 9, "");
        Resultado b = fila(Resultado.Serie.PLANIFICACION, "SJF", 1.5, 9, "Se detuvo; ver \"tope\"");
        ExportadorCsv csv = new ExportadorCsv(',');
        Path archivo = dir.resolve("salida/comparacion.csv");
        csv.escribir(archivo, new ExportadorCsv.Parametros("=Máquina rara", "lote oficial de 20 procesos", 42, 20), List.of(a, b));
        byte[] bytes = Files.readAllBytes(archivo);
        assertEquals((byte) 0xEF, bytes[0]);                               // BOM UTF-8
        String texto = new String(bytes, StandardCharsets.UTF_8).substring(1);
        String[] lineas = texto.split("\r\n");
        assertEquals("Máquina;'=Máquina rara", lineas[1]);                 // no se toma como fórmula
        assertEquals("Semilla;42", lineas[3]);
        assertEquals("", lineas[5]);
        String[] cabecera = lineas[6].split(";", -1);
        assertEquals("Serie", cabecera[0]);
        assertEquals(5 + Columna.values().length + 2, cabecera.length);
        assertTrue(lineas[7].startsWith("Planificación;FCFS;FCFS;5;5;100;12,35;"));
        assertEquals(cabecera.length, lineas[7].split(";", -1).length);
        assertTrue(lineas[8].endsWith(";\"Se detuvo; ver \"\"tope\"\"\""));
        assertEquals(9, lineas.length);
        assertEquals("1.50", new ExportadorCsv('.').texto(new ExportadorCsv.Parametros("M", "c", 1, 1), List.of(b))
                .split("\r\n")[7].split(";")[6]);
        csv.escribir(archivo, new ExportadorCsv.Parametros("Otra", "c", 7, 1), List.of(a));     // reemplaza el anterior
        assertTrue(new String(Files.readAllBytes(archivo), StandardCharsets.UTF_8).contains("Máquina;Otra"));
        try (var dentro = Files.list(archivo.getParent())) {
            assertEquals(List.of("comparacion.csv"), dentro.map(x -> x.getFileName().toString()).toList());   // sin temporales
        }
        assertThrows(ParametroInvalidoException.class, () -> new ExportadorCsv(';'));
        assertThrows(ParametroInvalidoException.class, () -> csv.texto(new ExportadorCsv.Parametros("M", "c", 1, 1), List.of()));
    }
}
