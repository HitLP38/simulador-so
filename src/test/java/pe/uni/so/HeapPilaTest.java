package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.excepciones.AccesoIlegalException;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.MemoriaInsuficienteException;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.EstrategiaAsignacion;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.motor.Kernel;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.PlanificadorLargoPlazo;
import pe.uni.so.procesos.Pcb;

class HeapPilaTest {
    private static final long KIB = 1024, B = 64 * KIB;

    /** Texto 1 000 KiB, datos 30 %, heap máximo 20 % (200 KiB = 4 páginas), pila máxima 256 KiB (4 páginas). */
    private static EspacioProceso espacio() { return new EspacioProceso(1000 * KIB, 0.30, 0.20, 256 * KIB, B); }

    private static GestorMemoria conProceso(int marcos) {
        GestorMemoria g = new GestorMemoria(marcos, B, EstrategiaAsignacion.primerAjuste());
        g.asignar(1, espacio());                                    // carga 16 + 5 + 1 = 22 páginas
        return g;
    }

    @Test @DisplayName("[M2] Taller p.2 ej. 3: tres malloc de 10 enteros dejan 120 bytes en el heap (fuga si nunca hay free)")
    void ej3Malloc() {
        GestorMemoria g = conProceso(40);
        for (int i = 0; i < 3; i++) assertTrue(g.crecerHeap(1, 10 * 4));
        assertEquals(120, g.heapUsado(1));
        assertEquals(1, g.paginasHeap(1));                            // 120 B caben en una página de 64 KiB
        assertEquals(17, g.mapa().libres());                          // 40 − 22 − 1
        long heap = espacio().base(EspacioProceso.Segmento.HEAP);
        assertEquals(22 * B + 0x10, g.mmu().traducir(1, heap + 0x10).fisica());
    }

    @Test @DisplayName("[M2] El heap crece hacia arriba hasta su máximo; pasarlo devuelve NULL sin cambiar nada")
    void heapHastaSuMaximo() {
        GestorMemoria g = conProceso(40);
        long heap = espacio().base(EspacioProceso.Segmento.HEAP);
        assertThrows(AccesoIlegalException.class, () -> g.mmu().traducir(1, heap));    // aún no creció
        assertTrue(g.crecerHeap(1, 200 * KIB));
        assertEquals(4, g.paginasHeap(1));
        g.mmu().traducir(1, heap + 3 * B + 0xFFFF);                   // la última página del heap ya traduce
        assertFalse(g.crecerHeap(1, 1));                              // 200 KiB + 1 B > máximo: NULL
        assertEquals(200 * KIB, g.heapUsado(1));
        assertEquals(14, g.mapa().libres());
    }

    @Test @DisplayName("[M2] PC2 25-2 preg. 6: la pila crece hacia abajo, desde el tope hacia el hueco")
    void pilaHaciaAbajo() {
        GestorMemoria g = conProceso(40);
        assertEquals(1, g.paginasPila(1));
        assertThrows(AccesoIlegalException.class, () -> g.mmu().traducir(1, 0xFFFE_FFFFL));   // debajo de la pila actual
        g.crecerPila(1, 100 * KIB);                                   // 100 KiB → 2 páginas
        assertEquals(2, g.paginasPila(1));
        assertEquals(22 * B + 0xFFFF, g.mmu().traducir(1, 0xFFFE_FFFFL).fisica());   // nueva página, más abajo
        g.crecerPila(1, 156 * KIB);                                   // justo 256 KiB
        assertEquals(4, g.paginasPila(1));
        assertThrows(AccesoIlegalException.class, () -> g.crecerPila(1, 1));          // desbordamiento de pila (E01)
    }

    @Test @DisplayName("[M2] Sin marcos libres: malloc devuelve NULL y la pila no puede crecer")
    void sinMarcos() {
        GestorMemoria g = conProceso(22);                             // justo lo inicial
        assertFalse(g.crecerHeap(1, 1));
        assertEquals(0, g.paginasHeap(1));
        assertThrows(MemoriaInsuficienteException.class, () -> g.crecerPila(1, 65 * KIB));
        g.crecerPila(1, 64 * KIB);                                    // cabe en la página que ya tiene
    }

    @Test @DisplayName("[M2][I6] Los marcos del crecimiento también se liberan al terminar")
    void liberarTodo() {
        GestorMemoria g = conProceso(40);
        g.crecerHeap(1, 130 * KIB);
        g.crecerPila(1, 70 * KIB);
        assertEquals(40 - 22 - 3 - 1, g.mapa().libres());
        g.liberar(1);
        assertEquals(40, g.mapa().libres());
    }

    @Test @DisplayName("[M2] El kernel aplica malloc y llamadas al proceso que ejecuta")
    void desdeElKernel() {
        GestorMemoria g = new GestorMemoria(40, B, EstrategiaAsignacion.primerAjuste());
        Pcb p = new Pcb(1, 0, 3);
        p.asignarEspacio(espacio());
        Kernel k = new Kernel(new Fcfs(), List.of(p), new PlanificadorLargoPlazo(g));
        assertThrows(EventoInvalidoException.class, () -> k.malloc(8));            // CPU libre
        k.paso();
        assertTrue(k.malloc(40));
        k.apilar(64 * KIB + 1);
        assertEquals(1, g.paginasHeap(1));
        assertEquals(2, g.paginasPila(1));
        assertThrows(EventoInvalidoException.class, () -> new Kernel(new Fcfs(), List.of(new Pcb(2, 0, 1))).malloc(8));
    }
}
