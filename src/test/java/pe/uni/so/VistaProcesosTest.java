package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.motor.Momento;
import pe.uni.so.procesos.Estado;
import pe.uni.so.sesion.DiagramaGantt;
import pe.uni.so.sesion.DiagramaGantt.Filtro;
import pe.uni.so.sesion.RegistroProcesos;
import pe.uni.so.sesion.RegistroProcesos.FilaMetricas;
import pe.uni.so.sesion.RegistroProcesos.Tramo;
import pe.uni.so.sesion.SesionSimulacion;

class VistaProcesosTest {

    private static SesionSimulacion lote() { return new SesionSimulacion(Maquina.porDefecto()); }

    @Test @DisplayName("[P16] Gantt: cada proceso tiene tramos contiguos desde su llegada hasta Terminado")
    void tramosContiguos() {
        SesionSimulacion s = lote();
        s.hastaElFinal();
        Instantanea f = s.vista();
        for (Instantanea.Proceso p : f.procesos()) {
            List<Tramo> tr = s.registro().tramos(p.pid(), f.t());
            assertFalse(tr.isEmpty());
            assertEquals(p.llegada(), tr.get(0).desde(), "P" + p.pid() + " empieza en su llegada");
            for (int i = 1; i < tr.size(); i++) {
                assertEquals(tr.get(i - 1).hasta(), tr.get(i).desde(), "P" + p.pid() + " sin huecos");
                assertTrue(tr.get(i - 1).estado() != tr.get(i).estado(), "P" + p.pid() + " tramos ya fusionados");
            }
            Tramo ultimo = tr.get(tr.size() - 1);
            assertEquals(Estado.TERMINADO, ultimo.estado());
            assertEquals(1, ultimo.duracion());
            assertTrue(tr.stream().anyMatch(x -> x.estado() == Estado.EJECUTANDO));
        }
    }

    @Test @DisplayName("[P17][P16] Métricas: retorno = fin − llegada, respuesta ≥ 0 y promedios de los 20 terminados")
    void metricasFinales() {
        SesionSimulacion s = lote();
        s.hastaElFinal();
        RegistroProcesos.Metricas m = s.registro().metricas(s.vista());
        assertEquals(20, m.terminados());
        double suma = 0;
        for (FilaMetricas x : m.filas()) {
            assertTrue(x.terminado());
            assertEquals(x.fin() - x.llegada(), x.retorno());
            assertTrue(x.respuesta() >= 0);
            assertTrue(x.retorno() >= x.respuesta());
            suma += x.retorno();
        }
        assertEquals(suma / 20, m.retornoMedio(), 1e-9);
        assertTrue(m.usoCpu() > 0 && m.usoCpu() <= 100);
    }

    @Test @DisplayName("[P16] Al mirar un instante pasado, Gantt y métricas se recortan a ese instante")
    void recorteAlPasado() {
        SesionSimulacion s = lote();
        s.hastaElFinal();
        s.irA(100);
        Instantanea f = s.vista();
        DiagramaGantt g = DiagramaGantt.de(s.registro(), f, 0, Filtro.TODOS);
        assertEquals(0, g.desde());
        assertEquals(101, g.hasta());
        g.filas().forEach(fila -> fila.barras().forEach(b -> assertTrue(b.hasta() <= 101)));
        long terminadosFoto = f.procesos().stream().filter(p -> p.estado() == Estado.TERMINADO).count();
        assertEquals(terminadosFoto, s.registro().metricas(f).terminados());
        for (Instantanea.Proceso p : f.procesos()) {
            if (p.llegada() > 100) continue;
            List<Tramo> tr = s.registro().tramos(p.pid(), 100);
            assertEquals(p.estado(), tr.get(tr.size() - 1).estado(), "P" + p.pid() + " termina en su estado de t = 100");
        }
    }

    @Test @DisplayName("[P16] Al re-simular un tick antiguo el Gantt sale idéntico")
    void reSimular() {
        SesionSimulacion s = lote();
        s.avanzar(300);
        s.irA(120);
        DiagramaGantt antes = DiagramaGantt.de(s.registro(), s.vista(), 0, Filtro.TODOS);
        s.irA(300);
        s.limitarHistorial(5);
        s.irA(120);                                                       // fuera del historial
        assertEquals(antes, DiagramaGantt.de(s.registro(), s.vista(), 0, Filtro.TODOS));
    }

    @Test @DisplayName("[P16] Ventana y filtros del Gantt: últimos N ticks; «En curso» oculta los que no llegaron o terminaron")
    void ventanaYFiltros() {
        SesionSimulacion s = lote();
        s.avanzar(200);
        Instantanea f = s.vista();
        DiagramaGantt g = DiagramaGantt.de(s.registro(), f, 50, Filtro.EN_CURSO);
        assertEquals(151, g.desde());
        assertEquals(50, g.ancho());
        long enCurso = f.procesos().stream().filter(p -> p.llegada() <= 200 && p.estado() != Estado.TERMINADO).count();
        assertEquals(enCurso, g.filas().size());
        assertEquals(20 - enCurso, g.ocultos());
        g.filas().forEach(fila -> fila.barras().forEach(b -> assertTrue(b.desde() >= 151 && b.hasta() <= 201)));
        DiagramaGantt t = DiagramaGantt.de(s.registro(), f, 0, Filtro.TERMINADOS);
        assertTrue(t.filas().stream().allMatch(x -> x.estado() == Estado.TERMINADO));
    }

    @Test @DisplayName("[P1][P6] Bitácora: del más reciente al más antiguo, con filtros por proceso y por momento a–e")
    void bitacora() {
        SesionSimulacion s = lote();
        s.hastaElFinal();
        int t = s.vista().t();
        List<Instantanea.Suceso> todos = s.registro().bitacora(t, Instantanea.NINGUNO, Set.of(), Integer.MAX_VALUE);
        for (int i = 1; i < todos.size(); i++) assertTrue(todos.get(i - 1).t() >= todos.get(i).t());
        Map<Momento, Integer> conteo = s.registro().conteoMomentos(t);
        assertEquals(todos.size(), conteo.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(20, conteo.get(Momento.E));                         // cada proceso termina una vez
        assertEquals(20, conteo.get(Momento.D));                         // y se admite una vez
        List<Instantanea.Suceso> p3 = s.registro().bitacora(t, 3, EnumSet.of(Momento.A, Momento.C), Integer.MAX_VALUE);
        assertTrue(p3.stream().allMatch(x -> x.pid() == 3 && (x.momento() == Momento.A || x.momento() == Momento.C)));
        assertEquals(10, s.registro().bitacora(t, Instantanea.NINGUNO, Set.of(), 10).size());
        assertTrue(s.registro().bitacora(40, Instantanea.NINGUNO, Set.of(), 1000).stream().allMatch(x -> x.t() <= 40));
    }

    @Test @DisplayName("[P18][P16] Un proceso ingresado en plena corrida aparece en el Gantt desde su llegada")
    void ingresoEnGantt() {
        SesionSimulacion s = lote();
        s.avanzar(30);
        int pid = s.ingresarManual(31, 20, 300 * 1024L, 1);
        DiagramaGantt g = DiagramaGantt.de(s.registro(), s.vista(), 0, Filtro.TODOS);
        assertTrue(g.filas().stream().noneMatch(x -> x.pid() == pid));   // aún no llega
        s.avanzar(5);
        g = DiagramaGantt.de(s.registro(), s.vista(), 0, Filtro.TODOS);
        DiagramaGantt.Fila fila = g.filas().stream().filter(x -> x.pid() == pid).findFirst().orElseThrow();
        assertTrue(fila.usuario());
        assertEquals(31, fila.barras().get(0).desde());
        s.hastaElFinal();
        assertTrue(s.registro().metricas(s.vista()).filas().stream().filter(x -> x.pid() == pid).findFirst().orElseThrow().terminado());
    }

    @Test @DisplayName("[P17][P16] Respuesta = primera instrucción en CPU − llegada; uso de CPU = ticks con ejecución / ticks transcurridos")
    void respuestaYUsoReales() {
        SesionSimulacion s = lote();
        s.hastaElFinal();
        int fin = s.vista().t();
        RegistroProcesos.Metricas m = s.registro().metricas(s.vista());
        java.util.Map<Integer, Integer> primera = new java.util.HashMap<>();
        int ocupados = 0;
        for (int t = 1; t <= fin; t++) {
            s.irA(t);
            int ejecuto = s.vista().cpu().ejecuto();
            if (ejecuto != Instantanea.NINGUNO) { ocupados++; primera.putIfAbsent(ejecuto, t - 1); }
        }
        for (FilaMetricas x : m.filas()) assertEquals(primera.get(x.pid()) - x.llegada(), x.respuesta(), "P" + x.pid());
        assertEquals(100.0 * ocupados / fin, m.usoCpu(), 1e-9);
        s.irA(1);
        RegistroProcesos.Metricas m1 = s.registro().metricas(s.vista());
        assertEquals(s.vista().cpu().ejecuto() == Instantanea.NINGUNO ? 0.0 : 100.0, m1.usoCpu(), 1e-9);
        Instantanea vieja = s.vista();
        assertThrows(EventoInvalidoException.class, () -> s.registro().registrar(vieja));   // anterior a la última registrada
    }
}
