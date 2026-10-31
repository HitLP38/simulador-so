package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.uni.so.memoria.EspacioProceso.Segmento.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.excepciones.AccesoIlegalException;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.FalloPaginaException;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.Mmu;
import pe.uni.so.memoria.TablaPaginas;

class MmuTest {
    private static final long KIB = 1024, B = 64 * KIB;

    /** Proceso de ejemplo (texto 1 000 KiB, datos 30 %, heap 20 %, pila 64 KiB) cargado segmento a segmento. */
    private static Mmu mmuConProceso(TablaPaginas t) {
        EspacioProceso e = new EspacioProceso(1000 * KIB, 0.30, 0.20, B);
        for (EspacioProceso.Segmento s : EspacioProceso.Segmento.values()) {
            int marco = switch (s) { case TEXTO -> 100; case DATOS -> 40; case HEAP -> 7; case PILA -> 3; };
            t.cargarContiguo(e.base(s) / B, e.paginas(s), marco);
        }
        Mmu mmu = new Mmu(B);
        mmu.registrar(1, e, t);
        return mmu;
    }

    @Test @DisplayName("[M4] Sesión 8: dirección virtual = (página, desplazamiento) → marco × B + desplazamiento")
    void traduccion() {
        Mmu mmu = mmuConProceso(new TablaPaginas());
        var r = mmu.traducir(1, 0x0016_1234L);              // heap: página 0x16 (22), desplazamiento 0x1234
        assertEquals(0x16, r.pagina());
        assertEquals(0x1234, r.desplazamiento());
        assertEquals(8, r.marco());                         // el heap empieza en la página 21 → marco 7
        assertEquals(8 * B + 0x1234, r.fisica());
        assertEquals(0x0000_0000L + 100 * B, mmu.traducir(1, 0x0).fisica());   // primer byte del texto
    }

    @Test @DisplayName("[M3] Al cargar, cada segmento queda en marcos contiguos: direcciones lógicas y físicas")
    void direccionesFisicasPorSegmento() {
        Mmu mmu = mmuConProceso(new TablaPaginas());
        assertEquals(40 * B, mmu.traducir(1, 0x0010_0000L).fisica());          // base de datos
        assertEquals(3 * B + 0xFFFF, mmu.traducir(1, 0xFFFF_FFFFL).fisica());  // último byte de la pila
    }

    @Test @DisplayName("[M4][P9] Taller p.1 ej. 13: todos los bytes del acceso deben quedar dentro del segmento")
    void proteccionPorRango() {
        Mmu mmu = mmuConProceso(new TablaPaginas());
        long fin = 0x0019_0000L;                                   // límite del heap; después viene el hueco
        assertEquals(7 * B + 3 * B + 0xFFFC, mmu.traducir(1, fin - 4, 4, false).fisica());   // 0x...FFFC con 4 bytes: válido
        assertThrows(AccesoIlegalException.class, () -> mmu.traducir(1, fin - 2, 4, false)); // se sale 2 bytes (caso C)
        assertThrows(AccesoIlegalException.class, () -> mmu.traducir(1, fin));               // el límite está excluido (caso D)
        assertThrows(AccesoIlegalException.class, () -> mmu.traducir(1, -1));
        assertThrows(AccesoIlegalException.class, () -> mmu.traducir(1, EspacioProceso.LIMITE_VIRTUAL));
    }

    @Test @DisplayName("[M1][M4] Página sin marco (bit i) → fallo de página; al cargarla se traduce y marca R y M")
    void bitDeValidez() {
        TablaPaginas t = new TablaPaginas();
        Mmu mmu = mmuConProceso(t);
        t.invalidar(0x16);
        FalloPaginaException f = assertThrows(FalloPaginaException.class, () -> mmu.traducir(1, 0x0016_0010L));
        assertEquals(0x16, f.pagina);

        t.cargar(0x16, 55);
        assertFalse(t.entrada(0x16).referenciada());
        assertEquals(55 * B + 0x10, mmu.traducir(1, 0x0016_0010L, 4, true).fisica());
        assertTrue(t.entrada(0x16).referenciada());
        assertTrue(t.entrada(0x16).modificada());
        assertFalse(t.entrada(0x15).modificada());
    }

    @Test @DisplayName("[M4] Un acceso que cruza a una página no cargada también falla; sin tabla registrada es error")
    void casosInvalidos() {
        TablaPaginas t = new TablaPaginas();
        Mmu mmu = mmuConProceso(t);
        t.invalidar(0x17);
        assertThrows(FalloPaginaException.class, () -> mmu.traducir(1, 0x0016_FFFEL, 4, false));   // cruza de 0x16 a 0x17
        assertThrows(EventoInvalidoException.class, () -> mmu.traducir(9, 0x0));
        assertThrows(EventoInvalidoException.class, () -> mmu.traducir(1, 0x0, 0, false));
        assertThrows(EventoInvalidoException.class, () -> new Mmu(32 * KIB).registrar(1, new EspacioProceso(1000 * KIB, 0.30, 0.20, B), t));
        assertThrows(EventoInvalidoException.class, () -> new Mmu(B).registrar(2, null, t));
        assertThrows(EventoInvalidoException.class, () -> new Mmu(B).registrar(2, new EspacioProceso(1000 * KIB, 0.30, 0.20, B), null));
    }

    @Test @DisplayName("[M3][M4] Un rango de carga inválido se rechaza sin modificar la tabla")
    void cargaContiguaInvalida() {
        TablaPaginas t = new TablaPaginas();
        assertThrows(EventoInvalidoException.class, () -> t.cargarContiguo(0, 2, Integer.MAX_VALUE));
        assertThrows(EventoInvalidoException.class, () -> t.cargarContiguo(0, -1, 0));
        assertThrows(EventoInvalidoException.class, () -> t.cargarContiguo(-1, 1, 0));
        assertEquals(0, t.paginasValidas());
        t.cargarContiguo(5, 0, 0);                                    // cero páginas: válido, no carga nada
        assertEquals(0, t.paginasValidas());
    }
}
