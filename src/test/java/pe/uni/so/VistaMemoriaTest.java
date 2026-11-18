package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.excepciones.AccesoIlegalException;
import pe.uni.so.excepciones.FalloPaginaException;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.EstrategiaAsignacion;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.memoria.Reemplazo;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.sesion.MapaMarcos;
import pe.uni.so.sesion.SesionSimulacion;
import pe.uni.so.sesion.TraductorDirecciones;
import pe.uni.so.sesion.TraductorDirecciones.Tipo;

class VistaMemoriaTest {

    private static final long KIB = 1024;
    private static final long B = Configuracion.porDefecto().pagina().bytes;

    /** La misma foto de memoria que arma el kernel, a partir de un gestor. */
    private static Instantanea.Memoria foto(GestorMemoria g, int... pids) {
        Map<Integer, Instantanea.MemoriaProceso> m = new HashMap<>();
        for (int pid : pids) {
            boolean cuota = g.bajoDemanda();
            m.put(pid, new Instantanea.MemoriaProceso(pid, g.tablaComprimida(pid), g.segmentos(pid),
                    cuota ? g.cuota(pid) : 0, cuota ? g.marcosDe(pid) : List.of(), g.proximaVictima(pid)));
        }
        return new Instantanea.Memoria(g.mapa().total(), g.muestra(0), "primer ajuste", g.reemplazo(), g.fallosPagina(),
                g.mmu().bloque(), g.ocupacion(), m);
    }

    /** Direcciones de prueba: inicio, medio y final de cada segmento, el hueco y más allá del límite. */
    private static List<Long> direcciones(EspacioProceso e) {
        List<Long> r = new java.util.ArrayList<>();
        for (EspacioProceso.Segmento s : EspacioProceso.Segmento.values()) {
            long base = e.base(s), lim = e.limite(s);
            if (lim > base) { r.add(base); r.add(base + (lim - base) / 2); r.add(lim - 1); }
            r.add(lim);
        }
        r.add(e.limite(EspacioProceso.Segmento.HEAP) + 5 * B);
        r.add(EspacioProceso.LIMITE_VIRTUAL - 1);
        return r;
    }

    private static void comparaConMmu(GestorMemoria g, Instantanea.Memoria f, int pid, EspacioProceso e) {
        for (long d : direcciones(e)) {
            TraductorDirecciones.Resultado r = TraductorDirecciones.traducir(f, pid, d);
            try {
                var t = g.mmu().traducir(pid, d);
                assertEquals(Tipo.TRADUCIDA, r.tipo(), "dir " + d);
                assertEquals(t.marco(), r.marco(), "dir " + d);
                assertEquals(t.fisica(), r.fisica(), "dir " + d);
                assertEquals(t.pagina(), r.pagina(), "dir " + d);
                assertEquals(t.desplazamiento(), r.desplazamiento(), "dir " + d);
            } catch (FalloPaginaException ex) {
                assertEquals(Tipo.FALLO_PAGINA, r.tipo(), "dir " + d);
            } catch (AccesoIlegalException ex) {
                assertEquals(Tipo.ACCESO_ILEGAL, r.tipo(), "dir " + d);
            }
        }
    }

    @Test @DisplayName("[M3][M4] Traductor (carga total): da el mismo marco y dirección física que la MMU, y los mismos accesos ilegales")
    void traductorCargaTotal() {
        GestorMemoria g = new GestorMemoria(64, B, EstrategiaAsignacion.primerAjuste());
        EspacioProceso e1 = new EspacioProceso(300 * KIB, 0.25, 0.25, B), e2 = new EspacioProceso(700 * KIB, 0.4, 0.25, B);
        g.asignar(1, e1);
        g.asignar(2, e2);
        g.crecerHeap(2, 100 * KIB);
        Instantanea.Memoria f = foto(g, 1, 2);
        comparaConMmu(g, f, 1, e1);
        comparaConMmu(g, f, 2, e2);
        assertEquals(Tipo.NO_RESIDENTE, TraductorDirecciones.traducir(f, 3, 0).tipo());
        for (GestorMemoria.TramoPaginas t : f.procesos().get(2).tabla())             // cada tramo queda dentro de un solo segmento
            assertEquals(e2.segmentoDe(t.paginaInicial() * B), e2.segmentoDe(t.paginaFinal() * B));
        assertTrue(f.procesos().get(2).tabla().size() >= 4);                      // texto, datos, heap y pila por separado
        TraductorDirecciones.Resultado r = TraductorDirecciones.traducir(f, 1, e1.base(EspacioProceso.Segmento.TEXTO) + 10);
        assertEquals(EspacioProceso.Segmento.TEXTO, r.segmento());
        assertEquals(r.marco() * B + r.desplazamiento(), r.fisica());
    }

    @Test @DisplayName("[M1][M7] Traductor (bajo demanda): las páginas con bit i dan fallo de página, igual que la MMU")
    void traductorBajoDemanda() {
        GestorMemoria g = new GestorMemoria(64, B, EstrategiaAsignacion.primerAjuste(), Reemplazo.FIFO);
        EspacioProceso e1 = new EspacioProceso(900 * KIB, 0.25, 0.25, B), e2 = new EspacioProceso(300 * KIB, 0.25, 0.25, B);
        g.repartirCuotas(Map.of(1, e1, 2, e2));
        g.asignar(1, e1);
        long texto = e1.base(EspacioProceso.Segmento.TEXTO);
        for (int i = 0; i < 6; i++) g.acceder(1, texto + i * B, 1, false);     // carga y reemplaza dentro de la cuota
        Instantanea.Memoria f = foto(g, 1);
        comparaConMmu(g, f, 1, e1);
        Instantanea.MemoriaProceso mp = f.procesos().get(1);
        assertEquals(g.cuota(1), mp.cuota());
        assertEquals(mp.cuota(), mp.marcosLocales().size());
        assertTrue(mp.paginasValidas() <= mp.cuota());
        assertTrue(mp.paginasRegistradas() > mp.paginasValidas());            // hay páginas con bit i
        if (mp.marcosLocales().stream().noneMatch(java.util.Objects::isNull)) assertTrue(mp.proximaVictima() >= 0);
    }

    @Test @DisplayName("[M3][P16] Rejilla: una celda por marco con poca RAM; con mucha, celdas agrupadas que toman el dueño mayoritario")
    void rejilla() {
        Instantanea.Memoria m = new Instantanea.Memoria(10, null, "x", null, 0, B,
                List.of(new GestorMemoria.BloqueMarcos(0, 3, 1), new GestorMemoria.BloqueMarcos(3, 1, 2),
                        new GestorMemoria.BloqueMarcos(6, 2, 3)), Map.of());
        MapaMarcos uno = MapaMarcos.de(m, 100);
        assertEquals(1, uno.porCelda());
        assertEquals(10, uno.celdas());
        assertEquals(List.of(1, 1, 1, 2, -1, -1, 3, 3, -1, -1), java.util.stream.IntStream.range(0, 10).map(uno::dueno).boxed().toList());
        assertFalse(uno.mixta(0));
        MapaMarcos agrupado = MapaMarcos.de(m, 3);                             // 4 marcos por celda: [0-3] [4-7] [8-9]
        assertEquals(4, agrupado.porCelda());
        assertEquals(3, agrupado.celdas());
        assertEquals(1, agrupado.dueno(0));
        assertTrue(agrupado.mixta(0));
        assertEquals(3, agrupado.dueno(1));                                    // 2 de P3 contra 2 libres: gana el proceso
        assertEquals(MapaMarcos.LIBRE, agrupado.dueno(2));
        assertEquals(1, agrupado.celdaDe(6));
        assertEquals(4, agrupado.primerMarco(1));
    }

    @Test @DisplayName("[M3][M4][P16] La foto del kernel trae la ocupación de cada marco y la tabla de cada proceso residente")
    void fotoDelKernel() {
        SesionSimulacion s = new SesionSimulacion(Maquina.porDefecto());
        s.avanzar(60);
        Instantanea.Memoria m = s.vista().memoria();
        int ocupados = 0;
        Set<Integer> duenos = new HashSet<>();
        Map<Integer, Integer> marcosDe = new HashMap<>();
        int fin = -1;
        for (GestorMemoria.BloqueMarcos b : m.ocupacion()) {
            assertTrue(b.marcoInicial() > fin, "bloques ordenados y sin solaparse");
            fin = b.marcoInicial() + b.marcos() - 1;
            ocupados += b.marcos();
            duenos.add(b.pid());
            marcosDe.merge(b.pid(), b.marcos(), Integer::sum);
        }
        assertEquals(m.muestra().ocupados(), ocupados);
        assertEquals(duenos, m.procesos().keySet());
        assertFalse(duenos.isEmpty());
        for (Instantanea.MemoriaProceso mp : m.procesos().values()) {
            assertEquals(marcosDe.get(mp.pid()), mp.paginasValidas(), "P" + mp.pid() + ": carga total, una página por marco");
            assertEquals(4, mp.segmentos().size());
        }
    }

    @Test @DisplayName("[M3][P16] Al re-simular un tick antiguo la foto de memoria sale idéntica")
    void reSimular() {
        SesionSimulacion s = new SesionSimulacion(Maquina.porDefecto());
        s.avanzar(120);
        s.irA(40);
        Instantanea.Memoria antes = s.vista().memoria();
        s.irA(120);
        s.limitarHistorial(5);
        s.irA(40);
        assertEquals(antes, s.vista().memoria());
    }

    @Test @DisplayName("[M7] Próxima víctima con cuota 2 y referencias A, B, A: FIFO elige A, LRU elige B; consultarla no cambia nada")
    void proximaVictimaFifoYLru() {
        for (Reemplazo politica : Reemplazo.values()) {
            GestorMemoria g = new GestorMemoria(2, B, EstrategiaAsignacion.primerAjuste(), politica);
            EspacioProceso e = new EspacioProceso(300 * KIB, 0.25, 0.25, B);
            g.repartirCuotas(Map.of(1, e));
            g.asignar(1, e);
            assertEquals(2, g.cuota(1));
            long texto = e.base(EspacioProceso.Segmento.TEXTO);
            long a = texto / B, b = a + 1, c = a + 2;
            g.acceder(1, a * B, 1, false);
            g.acceder(1, b * B, 1, false);
            g.acceder(1, a * B, 1, false);                                     // A vuelve a usarse
            long esperada = politica == Reemplazo.FIFO ? a : b;
            assertEquals(esperada, g.proximaVictima(1), politica.toString());
            assertEquals(esperada, g.proximaVictima(1), politica + ": consultar dos veces da lo mismo");
            g.acceder(1, c * B, 1, false);                                     // C entra y expulsa a la víctima
            assertFalse(g.marcosDe(1).contains(esperada), politica + ": salió la víctima anunciada");
            assertTrue(g.marcosDe(1).contains(c));
            assertTrue(g.marcosDe(1).contains(politica == Reemplazo.FIFO ? b : a));
        }
    }
}
