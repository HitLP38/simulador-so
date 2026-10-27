package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.planificacion.Despachador;
import pe.uni.so.procesos.Pcb;

class DespachadorTest {

    private static final double EPS = 1e-9;

    @Test @DisplayName("[P8] Taller ej. 17: 3 despachos de 0,5 ms, total 7,5 ms y 80 % útil")
    void ej17() {
        Despachador d = new Despachador(0.5);
        Pcb[] p = { new Pcb(1, 0, 2), new Pcb(2, 0, 2), new Pcb(3, 0, 2) };
        double t = 0;
        double[] inicio = new double[3];
        Pcb anterior = null;
        for (int i = 0; i < 3; i++) {
            inicio[i] = d.despachar(anterior, p[i], t);
            t = inicio[i] + p[i].burst;          // sin expropiación ni E/S
            anterior = p[i];
        }
        // a) P1 de 0,5 a 2,5; P2 de 3 a 5; P3 de 5,5 a 7,5
        assertEquals(0.5, inicio[0], EPS);
        assertEquals(3.0, inicio[1], EPS);
        assertEquals(5.5, inicio[2], EPS);
        // b) total, despacho, fracción útil y respuesta promedio
        assertEquals(7.5, t, EPS);
        assertEquals(1.5, d.tiempoDespacho(), EPS);
        assertEquals(0.80, d.fraccionUtil(6), EPS);
        assertEquals(3.0, (inicio[0] + inicio[1] + inicio[2]) / 3, EPS);
    }

    @Test @DisplayName("[P8] Taller ej. 17 d: con 0,1 ms el total es 6,3 ms y 95,24 % útil")
    void ej17d() {
        Despachador d = new Despachador(0.1);
        double t = 0;
        for (int i = 1; i <= 3; i++) t = d.despachar(null, new Pcb(i, 0, 2), t) + 2;
        assertEquals(6.3, t, EPS);
        assertEquals(95.24, Math.round(d.fraccionUtil(6) * 10000) / 100.0, EPS);
    }

    @Test @DisplayName("[P7][P8] Taller ej. 11: fracción útil con q = 1 y q = 5, y quantum mínimo 3,8 ms")
    void ej11() {
        assertEquals(83.33, Math.round(Despachador.fraccionUtilCiclo(1, 0.2) * 10000) / 100.0, EPS);
        assertEquals(96.15, Math.round(Despachador.fraccionUtilCiclo(5, 0.2) * 10000) / 100.0, EPS);
        assertEquals(3.8, Despachador.quantumMinimo(0.2, 0.05), EPS);
    }

    @Test @DisplayName("[P8] Taller ej. 10 b: se guarda el saliente antes de restaurar el entrante")
    void ordenDelCambioDeContexto() {
        Despachador d = new Despachador(0);
        Pcb p1 = new Pcb(1, 0, 5), p2 = new Pcb(2, 0, 5);
        d.despachar(p1, p2, 0);
        List<String> b = d.bitacora();
        assertEquals(4, b.size());
        assertTrue(b.get(0).startsWith("1. Guardar el contexto de P1"));
        assertTrue(b.get(3).startsWith("4. Restaurar el contexto de P2"));
    }

    @Test @DisplayName("[P8] El despacho inicial no guarda contexto: no hay proceso saliente")
    void despachoInicial() {
        Despachador d = new Despachador(0.5);
        d.despachar(null, new Pcb(1, 0, 2), 0);
        assertEquals(2, d.bitacora().size());
        assertEquals(1, d.despachos());
    }

    @Test @DisplayName("[P8] Un costo de despacho negativo se rechaza")
    void costoNegativo() {
        assertThrows(IllegalArgumentException.class, () -> new Despachador(-0.1));
    }
}
