package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.excepciones.TransicionInvalidaException;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.EstrategiaAsignacion;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.memoria.GestorMemoria.Tramo;
import pe.uni.so.memoria.MapaBits;
import pe.uni.so.motor.Kernel;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.PlanificadorLargoPlazo;
import pe.uni.so.planificacion.PlanificadorMedianoPlazo;
import pe.uni.so.procesos.Colas;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

class GestorMemoriaTest {
    private static final long KIB = 1024, B = 64 * KIB;

    /** Proceso de 26 páginas como máximo: texto 1 000 KiB (16), datos 30 % (5), heap 20 % (4), pila 64 KiB (1).
     *  Al admitirse carga 22: texto, datos y 1 de pila; el heap crece después (M2). */
    private static EspacioProceso espacio() { return new EspacioProceso(1000 * KIB, 0.30, 0.20, B); }

    private static Pcb proceso(int pid, int llegada, int burst) {
        Pcb p = new Pcb(pid, llegada, burst);
        p.asignarEspacio(espacio());
        return p;
    }

    /** Huecos de la PC2 25-2 preg. 3 (20, 10, 30, 5, 40, 15 marcos) separados por un marco ocupado. */
    private static GestorMemoria pc2(EstrategiaAsignacion e) {
        MapaBits m = new MapaBits(125);
        for (int separador : new int[] {20, 31, 62, 68, 109}) m.ocupar(separador, 1);
        GestorMemoria g = new GestorMemoria(m, B, e);
        for (int pedido : new int[] {12, 25, 8, 10}) g.asignarMarcos(pedido);
        return g;
    }

    private static List<Integer> huecos(GestorMemoria g) {
        return g.mapa().huecos().stream().map(MapaBits.Hueco::tamano).toList();
    }

    @Test @DisplayName("[M5][M6] PC2 25-2 preg. 3 sobre el mapa de bits real: primer, mejor y peor ajuste")
    void pc2_25_2() {
        GestorMemoria ff = pc2(EstrategiaAsignacion.primerAjuste());
        assertEquals(List.of(5, 5, 40, 15), huecos(ff));             // los huecos agotados desaparecen del mapa
        assertEquals(6, ff.comparaciones());                         // 1 + 3 + 1 + 1
        GestorMemoria bf = pc2(EstrategiaAsignacion.mejorAjuste());
        assertEquals(List.of(10, 2, 5, 5, 40, 3), huecos(bf));
        assertEquals(24, bf.comparaciones());
        GestorMemoria wf = pc2(EstrategiaAsignacion.peorAjuste());
        assertEquals(List.of(10, 10, 5, 5, 20, 15), huecos(wf));
        assertEquals(24, wf.comparaciones());
        assertEquals(45, wf.muestra(0).fragmentacionExterna());      // 65 marcos libres − hueco mayor de 20
    }

    @Test @DisplayName("[M6] Si ningún hueco alcanza, el segmento se reparte en varios huecos con la misma estrategia")
    void repartoEnVariosHuecos() {
        MapaBits m = new MapaBits(10);
        m.ocupar(3, 1);
        m.ocupar(7, 1);                                              // huecos: 3, 3, 2
        GestorMemoria g = new GestorMemoria(m, B, EstrategiaAsignacion.primerAjuste());
        assertEquals(List.of(new Tramo(0, 3), new Tramo(4, 2)), g.asignarMarcos(5));
        assertEquals(List.of(1, 2), huecos(g));
    }

    @Test @DisplayName("[M3][M4] Al asignar, cada segmento recibe marcos y la MMU traduce sus direcciones a físicas")
    void asignarYTraducir() {
        GestorMemoria g = new GestorMemoria(40, B, EstrategiaAsignacion.primerAjuste());
        g.asignar(1, espacio());
        assertEquals(18, g.mapa().libres());
        assertEquals(0, g.mmu().traducir(1, 0x0).fisica());                       // texto en los marcos 0–15
        assertEquals(16 * B, g.mmu().traducir(1, 0x0010_0000L).fisica());         // datos en 16–20
        assertEquals(21 * B + 0xFFFF, g.mmu().traducir(1, 0xFFFF_FFFFL).fisica()); // pila en el marco 21
        assertEquals(100 * KIB, g.desperdicioInterno());

        g.liberar(1);
        assertEquals(40, g.mapa().libres());
        assertFalse(g.residente(1));
        assertThrows(EventoInvalidoException.class, () -> g.mmu().traducir(1, 0x0));
    }

    @Test @DisplayName("[I6][P5] El largo plazo admite por marcos reales; el kernel registra la memoria en cada tick")
    void admisionPorMarcosYSerie() {
        GestorMemoria g = new GestorMemoria(40, B, EstrategiaAsignacion.primerAjuste());
        Pcb p1 = proceso(1, 0, 3), p2 = proceso(2, 0, 2);            // 22 + 22 páginas iniciales > 40 marcos
        Kernel k = new Kernel(new Fcfs(), List.of(p1, p2), new PlanificadorLargoPlazo(g));
        k.paso();
        assertEquals(Estado.NUEVO, p2.estado());                     // no hay marcos: espera sin adelantarse
        k.ejecutarTodo();

        assertEquals(3, p2.inicio());                                // entra cuando P1 libera sus marcos
        List<GestorMemoria.Muestra> serie = k.historialMemoria();
        assertEquals(k.t(), serie.size());                           // una muestra por tick
        assertTrue(serie.stream().allMatch(m -> m.libres() + m.ocupados() == 40));
        assertEquals(22, serie.get(0).ocupados());
        assertEquals(100 * KIB, serie.get(0).desperdicioInterno());
        assertEquals(0, serie.get(serie.size() - 1).ocupados());     // al final todo se liberó
    }

    @Test @DisplayName("[P5][I6] Suspender libera los marcos y reanudar asigna marcos nuevos")
    void suspenderYReanudar() {
        GestorMemoria g = new GestorMemoria(40, B, EstrategiaAsignacion.primerAjuste());
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(g);
        PlanificadorMedianoPlazo mediano = new PlanificadorMedianoPlazo(largo);
        Pcb p = proceso(1, 0, 5);
        largo.admitir(p, new Colas());
        assertEquals(22, largo.usada());
        mediano.suspender(p);
        assertEquals(40, g.mapa().libres());
        assertTrue(mediano.reanudar(p));
        assertEquals(18, largo.libre());
        assertTrue(g.residente(1));
    }

    @Test @DisplayName("[I6][I4] La admisión es atómica: si la memoria o el paso a Listo fallan, nada queda a medias")
    void admisionAtomica() {
        GestorMemoria g = new GestorMemoria(40, B, EstrategiaAsignacion.primerAjuste());
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(g);
        Colas c = new Colas();

        Pcb otroBloque = new Pcb(1, 0, 3);
        otroBloque.asignarEspacio(new EspacioProceso(1000 * KIB, 0.30, 0.20, 32 * KIB));   // página de 32 KiB ≠ marco de 64 KiB
        assertThrows(ParametroInvalidoException.class, () -> largo.admitir(otroBloque, c));
        assertEquals(Estado.NUEVO, otroBloque.estado());
        assertTrue(c.listos().isEmpty());

        Pcb yaListo = proceso(2, 0, 3);
        new Colas().admitir(yaListo);                                // ya pasó a Listo por otra vía
        assertThrows(TransicionInvalidaException.class, () -> largo.admitir(yaListo, c));
        assertEquals(40, g.mapa().libres());                         // la memoria reservada se devolvió
        assertFalse(largo.residente(yaListo));
    }

    @Test @DisplayName("[I6] Un proceso más grande que toda la memoria se rechaza; sin espacio de proceso es error")
    void casosInvalidos() {
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(new GestorMemoria(20, B, EstrategiaAsignacion.primerAjuste()));
        assertThrows(ParametroInvalidoException.class, () -> largo.cabe(proceso(1, 0, 3)));   // 26 > 20
        assertThrows(EventoInvalidoException.class, () -> largo.cabe(new Pcb(2, 0, 3)));
        assertThrows(ParametroInvalidoException.class, () -> new GestorMemoria(20, 48 * KIB, EstrategiaAsignacion.primerAjuste()));
    }
}
