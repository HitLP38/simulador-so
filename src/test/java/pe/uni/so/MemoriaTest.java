package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.excepciones.MemoriaInsuficienteException;
import pe.uni.so.memoria.EstrategiaAsignacion;
import pe.uni.so.memoria.MapaBits;

/** Capa TEORÍA + REQUISITOS de memoria (prácticas del profesor). */
class MemoriaTest {

    /** Aplica la estrategia a cada pedido; el sobrante queda en el mismo hueco. */
    private static int aplicar(EstrategiaAsignacion e, List<Integer> huecos, int... pedidos) {
        int comparaciones = 0;
        for (int pedido : pedidos) {
            var el = e.elegir(huecos, pedido);
            comparaciones += el.comparaciones();
            huecos.set(el.indice(), huecos.get(el.indice()) - pedido);
        }
        return comparaciones;
    }

    private static List<Integer> huecosPc2() { return new ArrayList<>(List.of(20, 10, 30, 5, 40, 15)); }

    @Test @DisplayName("[M6][M8] PC2 25-2 preg. 3: primer, mejor y peor ajuste")
    void pc2_25_2() {
        var ff = huecosPc2();
        assertEquals(7, aplicar(EstrategiaAsignacion.primerAjuste(), ff, 12, 25, 8, 10));
        assertEquals(List.of(0, 0, 5, 5, 40, 15), ff);
        var bf = huecosPc2();
        assertEquals(24, aplicar(EstrategiaAsignacion.mejorAjuste(), bf, 12, 25, 8, 10));
        assertEquals(List.of(10, 2, 5, 5, 40, 3), bf);
        var wf = huecosPc2();
        assertEquals(24, aplicar(EstrategiaAsignacion.peorAjuste(), wf, 12, 25, 8, 10));
        assertEquals(List.of(10, 10, 5, 5, 20, 15), wf);
    }

    @Test @DisplayName("[M6] PC2 26-1 preg. 22: pedido de 250 KB")
    void pc2_26_1() {
        List<Integer> h = List.of(100, 300, 200, 400);
        assertEquals(1, EstrategiaAsignacion.primerAjuste().elegir(h, 250).indice());
        assertEquals(1, EstrategiaAsignacion.mejorAjuste().elegir(h, 250).indice());
        assertEquals(3, EstrategiaAsignacion.peorAjuste().elegir(h, 250).indice());
    }

    @Test @DisplayName("[I3][I5] PC3 25-I preg. 2: bloques de 64 KB y fragmentación interna")
    void pc3_25_1() {
        long bloque = 64 * 1024;
        assertEquals(32768, MapaBits.bloques(2L * 1024 * 1024 * 1024, bloque));
        long proceso = 1_288_490_189L;                     // 1,2 GiB
        assertEquals(19661, MapaBits.bloques(proceso, bloque));
        long desperdicio = MapaBits.desperdicio(proceso, bloque);
        assertTrue(desperdicio > 13_100 && desperdicio < 13_110);   // ~12,8 KiB
    }

    @Test @DisplayName("[I2][I4] El bloque debe ser potencia de 2 entre 32 KiB y 2 MiB")
    void bloquePotenciaDeDos() {
        assertDoesNotThrow(() -> MapaBits.validarBloque(64 * 1024));
        assertThrows(IllegalArgumentException.class, () -> MapaBits.validarBloque(48 * 1024));
        assertThrows(IllegalArgumentException.class, () -> MapaBits.validarBloque(4 * 1024 * 1024));
    }

    @Test @DisplayName("[M5][I6] Mapa de bits: asignar, liberar y rechazar sin memoria")
    void mapaDeBits() {
        MapaBits m = new MapaBits(10);
        int a = m.asignar(4, EstrategiaAsignacion.primerAjuste());
        m.asignar(3, EstrategiaAsignacion.primerAjuste());
        assertEquals(3, m.libres());
        m.liberar(a, 4);
        assertEquals(2, m.huecos().size());
        assertEquals(m.total(), m.libres() + 3);
        assertThrows(MemoriaInsuficienteException.class, () -> m.asignar(5, EstrategiaAsignacion.mejorAjuste()));
    }
}
