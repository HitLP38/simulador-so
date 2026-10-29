package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.planificacion.Despachador;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.PlanificadorLargoPlazo;
import pe.uni.so.planificacion.PlanificadorMedianoPlazo;
import pe.uni.so.procesos.Colas;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

class DespachadorTest {

    private static final double EPS = 1e-9;

    /** Ejecuta P1, P2 y P3 (2 ms cada uno) en orden FCFS y devuelve el instante final. */
    private static double tresProcesos(Despachador d, double[] inicio) {
        Colas c = new Colas();
        for (int pid = 1; pid <= 3; pid++) c.admitir(new Pcb(pid, 0, 2));
        double t = 0;
        for (int i = 0; i < 3; i++) {
            inicio[i] = d.despachar(c, new Fcfs(), t);
            t = inicio[i] + c.enCpu().burst;     // sin expropiación ni E/S
            c.terminarEnCpu();
        }
        return t;
    }

    @Test @DisplayName("[P8] Taller ej. 17: 3 despachos de 0,5 ms, total 7,5 ms y 80 % útil")
    void ej17() {
        Despachador d = new Despachador(0.5);
        double[] inicio = new double[3];
        double t = tresProcesos(d, inicio);
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
        double t = tresProcesos(d, new double[3]);
        assertEquals(6.3, t, EPS);
        assertEquals(95.24, Math.round(d.fraccionUtil(6) * 10000) / 100.0, EPS);
    }

    @Test @DisplayName("[P7][P8] Taller ej. 11: fracción útil con q = 1 y q = 5, y quantum mínimo 3,8 ms")
    void ej11() {
        assertEquals(83.33, Math.round(Despachador.fraccionUtilCiclo(1, 0.2) * 10000) / 100.0, EPS);
        assertEquals(96.15, Math.round(Despachador.fraccionUtilCiclo(5, 0.2) * 10000) / 100.0, EPS);
        assertEquals(3.8, Despachador.quantumMinimo(0.2, 0.05), EPS);
    }

    @Test @DisplayName("[P4][P8] Taller ej. 10 b: guarda el saliente, lo pasa a Listo y luego carga el entrante")
    void cambioDeContexto() {
        Colas c = new Colas();
        Pcb p1 = new Pcb(1, 0, 5), p2 = new Pcb(2, 0, 5);
        c.admitir(p1);
        c.admitir(p2);
        Despachador d = new Despachador(0);
        d.despachar(c, new Fcfs(), 0);                  // despacho inicial: P1
        d.despachar(c, new Fcfs(), 1);                  // cambio de contexto P1 -> P2

        assertEquals(p2, c.enCpu());
        assertEquals(Estado.EJECUTANDO, p2.estado());
        assertEquals(Estado.LISTO, p1.estado());
        assertEquals(List.of(p1), c.listos());

        List<String> b = d.bitacora().subList(2, 6);    // los 4 pasos del segundo despacho
        assertTrue(b.get(0).startsWith("1. Guardar el contexto de P1"));
        assertTrue(b.get(1).startsWith("2. Pasar P1 a Listo"));
        assertTrue(b.get(2).startsWith("3. FCFS eligió a P2"));
        assertTrue(b.get(3).startsWith("4. Restaurar el contexto de P2"));
    }

    @Test @DisplayName("[P8] El despacho inicial no guarda contexto: no hay proceso saliente")
    void despachoInicial() {
        Colas c = new Colas();
        c.admitir(new Pcb(1, 0, 2));
        Despachador d = new Despachador(0.5);
        assertEquals(0.5, d.despachar(c, new Fcfs(), 0), EPS);
        assertEquals(2, d.bitacora().size());
        assertEquals(1, d.despachos());
    }

    @Test @DisplayName("[P8] Sin procesos listos no hay despacho ni costo")
    void sinListos() {
        Colas c = new Colas();
        Despachador d = new Despachador(0.5);
        assertEquals(4.0, d.despachar(c, new Fcfs(), 4), EPS);
        assertNull(c.enCpu());
        assertEquals(0, d.despachos());
    }

    @Test @DisplayName("[P5][P8] Si solo hay procesos suspendidos no hay despacho ni costo")
    void soloSuspendidos() {
        Colas c = new Colas();
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(1024);
        Pcb p = new Pcb(1, 0, 2, 0, 100);
        largo.admitir(p, c);
        new PlanificadorMedianoPlazo(largo).suspender(p);
        Despachador d = new Despachador(0.5);
        assertEquals(3.0, d.despachar(c, new Fcfs(), 3), EPS);
        assertNull(c.enCpu());
        assertEquals(0, d.despachos());
        assertTrue(d.bitacora().isEmpty());
    }

    @Test @DisplayName("[P8] Un costo de despacho negativo, NaN o infinito se rechaza")
    void costoInvalido() {
        assertThrows(ParametroInvalidoException.class, () -> new Despachador(-0.1));
        assertThrows(ParametroInvalidoException.class, () -> new Despachador(Double.NaN));
        assertThrows(ParametroInvalidoException.class, () -> new Despachador(Double.POSITIVE_INFINITY));
    }
}
