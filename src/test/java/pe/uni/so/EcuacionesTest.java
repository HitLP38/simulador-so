package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.aleatorio.Ecuaciones;
import pe.uni.so.aleatorio.Lcg;

/** Capa INVARIANTES: los rangos de las ecuaciones se cumplen para cualquier semilla. */
class EcuacionesTest {

    @Test @DisplayName("[P11][E10] Interrupciones de E/S por proceso entre 10 y 20")
    void interrupcionesEnRango() {
        for (long semilla = 1; semilla <= 500; semilla++) {
            Lcg rng = new Lcg(semilla);
            for (int i = 0; i < 20; i++) {
                int n = Ecuaciones.pedidosES(rng.uniforme(), rng);
                assertTrue(n >= 5 && n <= 10, "pedidos=" + n);
                assertTrue(2 * n >= 10 && 2 * n <= 20);
            }
        }
    }

    @Test @DisplayName("[P12][E11] Duración de cada servicio de E/S entre 5 y 20")
    void duracionEnRango() {
        Lcg rng = new Lcg(2026);
        for (int i = 0; i < 10_000; i++) {
            int d = Ecuaciones.duracionES(rng.uniforme(), rng);
            assertTrue(d >= 5 && d <= 20, "duracion=" + d);
        }
    }

    @Test @DisplayName("[P13] Misma semilla, misma secuencia (resultados reproducibles)")
    void reproducible() {
        Lcg a = new Lcg(42), b = new Lcg(42);
        for (int i = 0; i < 100; i++) assertEquals(a.uniforme(), b.uniforme());
    }
}
