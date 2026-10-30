package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static pe.uni.so.memoria.EspacioProceso.Segmento.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.EspacioProceso;

class EspacioProcesoTest {
    private static final long KIB = 1024, B = 64 * KIB;

    /** Ejecutable de 1 000 KiB, datos 30 %, heap 20 %, pila 64 KiB, bloques de 64 KiB. */
    private static EspacioProceso ejemplo() { return new EspacioProceso(1000 * KIB, 0.30, 0.20, B); }

    @Test @DisplayName("[I1] Cuatro segmentos: texto, datos (% del texto), heap máximo (% del texto) y pila fija")
    void segmentos() {
        EspacioProceso e = ejemplo();
        assertEquals(1000 * KIB, e.tamano(TEXTO));
        assertEquals(300 * KIB, e.tamano(DATOS));
        assertEquals(200 * KIB, e.tamano(HEAP));
        assertEquals(64 * KIB, e.tamano(PILA));
        assertEquals(1564 * KIB, e.total());
    }

    @Test @DisplayName("[I1][I2] Se rechazan porcentajes fuera de rango, tamaños no positivos y bloques que no son 2^n")
    void validaciones() {
        assertThrows(ParametroInvalidoException.class, () -> new EspacioProceso(1000 * KIB, 0.19, 0.20, B));
        assertThrows(ParametroInvalidoException.class, () -> new EspacioProceso(1000 * KIB, 0.41, 0.20, B));
        assertThrows(ParametroInvalidoException.class, () -> new EspacioProceso(1000 * KIB, 0.30, 0.09, B));
        assertThrows(ParametroInvalidoException.class, () -> new EspacioProceso(1000 * KIB, 0.30, 0.31, B));
        assertThrows(ParametroInvalidoException.class, () -> new EspacioProceso(0, 0.30, 0.20, B));
        assertThrows(ParametroInvalidoException.class, () -> new EspacioProceso(1000 * KIB, 0.30, 0.20, 0, B));
        assertThrows(IllegalArgumentException.class, () -> new EspacioProceso(1000 * KIB, 0.30, 0.20, 48 * KIB));
    }

    @Test @DisplayName("[I3][I5] Páginas por segmento y fragmentación interna con bloques de 64 KiB")
    void paginasYDesperdicio() {
        EspacioProceso e = ejemplo();
        assertEquals(16, e.paginas(TEXTO));      // 1 000 / 64 → 16 (1 024 KiB)
        assertEquals(5, e.paginas(DATOS));       //   300 / 64 → 5 (320 KiB)
        assertEquals(4, e.paginas(HEAP));        //   200 / 64 → 4 (256 KiB)
        assertEquals(1, e.paginas(PILA));
        assertEquals(26, e.paginasTotales());
        assertEquals(24 * KIB, e.desperdicio(TEXTO));
        assertEquals(20 * KIB, e.desperdicio(DATOS));
        assertEquals(56 * KIB, e.desperdicio(HEAP));
        assertEquals(0, e.desperdicio(PILA));
        assertEquals(100 * KIB, e.desperdicioTotal());
        assertEquals(6.01, e.porcentajeDesperdicio(), 0.01);   // 100 / (26 × 64)
    }

    @Test @DisplayName("[M3] Direcciones lógicas: texto desde 0, heap hacia arriba, pila en el tope y hueco entre ambos")
    void disposicionLogica() {
        EspacioProceso e = ejemplo();
        assertEquals(0x0000_0000L, e.base(TEXTO));
        assertEquals(0x0010_0000L, e.base(DATOS));        // 16 × 64 KiB
        assertEquals(0x0015_0000L, e.base(HEAP));         // + 5 × 64 KiB
        assertEquals(0x0019_0000L, e.limite(HEAP));       // + 4 × 64 KiB
        assertEquals(0xFFFF_0000L, e.base(PILA));         // tope − 1 página
        assertEquals(EspacioProceso.LIMITE_VIRTUAL, e.limite(PILA));

        assertEquals(TEXTO, e.segmentoDe(0x0000_0FFCL));
        assertEquals(HEAP, e.segmentoDe(0x0015_0000L));
        assertEquals(PILA, e.segmentoDe(0xFFFF_FFFFL));
        assertNull(e.segmentoDe(0x0019_0000L));           // hueco entre heap y pila
        assertNull(e.segmentoDe(-4));
    }
}
