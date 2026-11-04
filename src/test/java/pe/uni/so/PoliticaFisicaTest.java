package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.excepciones.AccesoIlegalException;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.EstrategiaAsignacion;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.memoria.PoliticaFisica;
import pe.uni.so.memoria.Reemplazo;
import pe.uni.so.motor.Kernel;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.PlanificadorLargoPlazo;
import pe.uni.so.planificacion.PlanificadorMedianoPlazo;
import pe.uni.so.procesos.Colas;
import pe.uni.so.procesos.Pcb;

class PoliticaFisicaTest {
    private static final long KIB = 1024, B = 64 * KIB;
    private static final long[] PC2_26_1 = {7, 0, 1, 2, 0, 3, 0, 4, 2, 3, 0, 3, 2};

    /** 26 páginas como máximo (texto 16, datos 5, heap 4, pila 1). */
    private static EspacioProceso espacio() { return new EspacioProceso(1000 * KIB, 0.30, 0.20, B); }

    private static int fallos(Reemplazo r, int marcos, long... referencias) {
        PoliticaFisica pf = new PoliticaFisica(r, marcos);
        for (long p : referencias) pf.referenciar(p);
        return pf.fallos();
    }

    @Test @DisplayName("[M7] PC2 26-1 preg. 21: FIFO con 3 marcos da 10 fallos y deja 0, 3, 2 en memoria")
    void pc2Fifo() {
        PoliticaFisica pf = new PoliticaFisica(Reemplazo.FIFO, 3);
        for (long p : PC2_26_1) pf.referenciar(p);
        assertEquals(10, pf.fallos());
        assertEquals(7, pf.reemplazos());                           // los 3 primeros fallos llenan marcos libres
        assertEquals(Set.of(0L, 3L, 2L), new HashSet<>(pf.marcos()));
    }

    @Test @DisplayName("[M7] PC2 26-1 preg. 21: LRU con 3 marcos da 9 fallos")
    void pc2Lru() {
        assertEquals(9, fallos(Reemplazo.LRU, 3, PC2_26_1));
    }

    @Test @DisplayName("[M7] FIFO y LRU eligen víctimas distintas: FIFO la más antigua, LRU la menos usada")
    void victimas() {
        PoliticaFisica fifo = new PoliticaFisica(Reemplazo.FIFO, 3), lru = new PoliticaFisica(Reemplazo.LRU, 3);
        for (long p : new long[] {7, 0, 1, 2, 0}) { fifo.referenciar(p); lru.referenciar(p); }
        assertEquals(0, fifo.referenciar(3).victima());             // 0 llegó antes que 1 y 2
        assertEquals(1, lru.referenciar(3).victima());              // 0 se usó hace poco; 1 es la menos reciente
        assertFalse(lru.referenciar(0).fallo());                    // acierto
    }

    @Test @DisplayName("[M7] Anomalía de Belady: con FIFO, 4 marcos dan más fallos que 3; con LRU no")
    void belady() {
        long[] refs = {1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5};
        assertEquals(9, fallos(Reemplazo.FIFO, 3, refs));
        assertEquals(10, fallos(Reemplazo.FIFO, 4, refs));
        assertTrue(fallos(Reemplazo.LRU, 4, refs) <= fallos(Reemplazo.LRU, 3, refs));
    }

    @Test @DisplayName("[M7] Asignación proporcional fᵢ = ⌊(sᵢ / S)·m⌋: 10 y 127 páginas con 62 marcos → 4 y 57")
    void proporcional() {
        assertArrayEquals(new int[] {4, 57}, PoliticaFisica.cuotas(new int[] {10, 127}, 62));
        assertArrayEquals(new int[] {1, 9}, PoliticaFisica.cuotas(new int[] {1, 200}, 10));   // mínimo 1 marco
        assertThrows(ParametroInvalidoException.class, () -> PoliticaFisica.cuotas(new int[] {5, 5, 5}, 2));
        assertThrows(ParametroInvalidoException.class, () -> new PoliticaFisica(Reemplazo.LRU, 0));
        assertThrows(ParametroInvalidoException.class, () -> new PoliticaFisica(null, 3));
    }

    @Test @DisplayName("[M1][M7] Bajo demanda: al admitir se reserva la cuota y todas las páginas quedan con bit i")
    void cargaParcial() {
        GestorMemoria g = new GestorMemoria(40, B, EstrategiaAsignacion.primerAjuste(), Reemplazo.FIFO);
        g.repartirCuotas(Map.of(1, espacio()));                     // un solo proceso: min(40, 26) = 26 marcos
        assertEquals(26, g.cuota(1));
        g.asignar(1, espacio());
        assertEquals(14, g.mapa().libres());
        assertTrue(g.marcosDe(1).stream().allMatch(m -> m == null));  // nada cargado todavía
        assertEquals(0, g.acceder(1, 0x10, 1, false).fisica() / B);   // fallo: la página 0 entra al marco 0
        assertEquals(1, g.fallosPagina());
        g.acceder(1, 0x20, 1, false);                                 // acierto
        assertEquals(1, g.fallosPagina());
        long heap = espacio().base(EspacioProceso.Segmento.HEAP);
        assertThrows(AccesoIlegalException.class, () -> g.acceder(1, heap, 1, false));   // el heap aún no creció
    }

    @Test @DisplayName("[M7] Reemplazo local: con la cuota llena, la víctima es una página del mismo proceso")
    void reemplazoLocal() {
        GestorMemoria g = new GestorMemoria(6, B, EstrategiaAsignacion.primerAjuste(), Reemplazo.LRU);
        Map<Integer, EspacioProceso> procesos = new LinkedHashMap<>();
        procesos.put(1, espacio());
        procesos.put(2, espacio());
        g.repartirCuotas(procesos);                                   // 26 y 26 páginas, 6 marcos → 3 y 3
        g.asignar(1, espacio());
        g.asignar(2, espacio());
        assertEquals(0, g.mapa().libres());
        for (long p : PC2_26_1) g.acceder(1, p * B, 1, false);       // P1 recorre la cadena de la PC2 con LRU
        assertEquals(9, g.fallosPagina());
        assertEquals(List.of(), g.marcosDe(2).stream().filter(m -> m != null).toList());   // P2 no perdió nada
        assertEquals(3, g.marcosDe(1).stream().filter(m -> m != null).count());
        assertTrue(Arrays.asList(0L, 3L, 2L).containsAll(g.marcosDe(1)));
        assertThrows(EventoInvalidoException.class, () -> g.cuota(9));
    }

    @Test @DisplayName("[M1][M7] El kernel reparte las cuotas al iniciar y el proceso en CPU provoca fallos de página")
    void desdeElKernel() {
        GestorMemoria g = new GestorMemoria(10, B, EstrategiaAsignacion.primerAjuste(), Reemplazo.FIFO);
        Pcb p1 = new Pcb(1, 0, 3), p2 = new Pcb(2, 0, 3);
        p1.asignarEspacio(espacio());
        p2.asignarEspacio(espacio());
        Kernel k = new Kernel(new Fcfs(), List.of(p1, p2), new PlanificadorLargoPlazo(g));
        assertEquals(5, g.cuota(1));                                  // 26/52 · 10
        k.paso();
        assertTrue(g.residente(1) && g.residente(2));                 // con carga parcial caben los dos
        k.acceder(0x0, 4, true);
        k.acceder(0x1_0000, 4, false);
        assertEquals(2, g.fallosPagina());
    }

    @Test @DisplayName("[M2][M7] Bajo demanda el heap crece sin pedir marcos: sus páginas llegan con los fallos")
    void heapBajoDemanda() {
        GestorMemoria g = new GestorMemoria(3, B, EstrategiaAsignacion.primerAjuste(), Reemplazo.FIFO);
        g.repartirCuotas(Map.of(1, espacio()));
        g.asignar(1, espacio());
        assertTrue(g.crecerHeap(1, 200 * KIB));                       // 4 páginas lógicas, 0 marcos nuevos
        assertEquals(0, g.mapa().libres());
        long heap = espacio().base(EspacioProceso.Segmento.HEAP);
        for (int i = 0; i < 4; i++) g.acceder(1, heap + i * B, 1, true);
        assertEquals(4, g.fallosPagina());
        assertEquals(List.of(heap / B + 3, heap / B + 1, heap / B + 2), g.marcosDe(1));   // FIFO expulsó la primera
    }

    @Test @DisplayName("[P5][M7] Suspender devuelve la cuota; al reanudar las páginas vuelven por fallos de página")
    void suspenderBajoDemanda() {
        GestorMemoria g = new GestorMemoria(10, B, EstrategiaAsignacion.primerAjuste(), Reemplazo.LRU);
        g.repartirCuotas(Map.of(1, espacio()));
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(g);
        PlanificadorMedianoPlazo mediano = new PlanificadorMedianoPlazo(largo);
        Pcb p = new Pcb(1, 0, 5);
        p.asignarEspacio(espacio());
        assertTrue(largo.admitir(p, new Colas()));
        assertTrue(g.crecerHeap(1, 8));
        g.acceder(1, 0x0, 1, false);
        mediano.suspender(p);
        assertEquals(10, g.mapa().libres());
        assertTrue(mediano.reanudar(p));
        assertEquals(0, g.mapa().libres());                           // vuelve con su cuota de 10 marcos
        assertEquals(8, g.heapUsado(1));                              // y conserva lo que creció el heap
        g.acceder(1, 0x0, 1, false);
        assertEquals(2, g.fallosPagina());                            // la página 0 tuvo que volver desde swap
    }

    @Test @DisplayName("[M1][M4] Un acceso que cruza a una página nunca asignada es ilegal antes de atender ningún fallo")
    void accesoIlegalSinFalloPrevio() {
        GestorMemoria g = new GestorMemoria(10, B, EstrategiaAsignacion.primerAjuste(), Reemplazo.FIFO);
        g.repartirCuotas(Map.of(1, espacio()));
        g.asignar(1, espacio());
        assertTrue(g.crecerHeap(1, 8));                               // 1 página de heap registrada (bit i)
        long heap = espacio().base(EspacioProceso.Segmento.HEAP);
        assertThrows(AccesoIlegalException.class, () -> g.acceder(1, heap + B - 2, 4, false));   // cruza a la 2.ª página, que no existe
        assertEquals(0, g.fallosPagina());                            // no cargó la primera ni expulsó a nadie
        assertTrue(g.marcosDe(1).stream().allMatch(m -> m == null));
    }
}
