package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.Metricas;
import pe.uni.so.planificacion.SimuladorCpu;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

class MetricasTest {

    private static final double EPS = 1e-9;

    private static double dosDecimales(double x) { return Math.round(x * 100) / 100.0; }

    /**
     * Reproduce una traza tick a tick: en cada tick ejecuta el PID indicado (0 = CPU ociosa)
     * y suma espera a los que ya llegaron, no han terminado y no están en la CPU.
     */
    private static void reproducir(List<Pcb> procesos, int[] traza) {
        for (int t = 0; t < traza.length; t++) {
            for (Pcb p : procesos) {
                if (p.llegada == t) p.pasarA(Estado.LISTO);
                boolean enCpu = p.pid == traza[t];
                if (enCpu) {
                    if (p.estado() == Estado.LISTO) p.pasarA(Estado.EJECUTANDO);
                    p.marcarInicio(t);
                    p.ejecutarInstruccion();
                    if (p.restante() == 0) { p.marcarFin(t + 1); p.pasarA(Estado.TERMINADO); }
                } else if (p.estado() == Estado.EJECUTANDO) {
                    p.pasarA(Estado.LISTO);
                    p.sumarEspera();
                } else if (p.estado() == Estado.LISTO) {
                    p.sumarEspera();
                }
            }
        }
    }

    @Test @DisplayName("[P17] Taller ej. 20: retorno 6,67, espera 3,33 y respuesta 2,33 de promedio")
    void ej20Promedios() {
        List<Pcb> ps = List.of(new Pcb(1, 0, 5), new Pcb(2, 1, 3), new Pcb(3, 2, 2));
        // a) P1 0-2, P2 2-5, P1 5-8, P3 8-10, CPU ociosa 10-12
        reproducir(ps, new int[] {1, 1, 2, 2, 2, 1, 1, 1, 3, 3, 0, 0});

        assertEquals(List.of(8, 4, 8), ps.stream().map(Pcb::retorno).toList());
        assertEquals(List.of(3, 1, 6), ps.stream().map(Pcb::espera).toList());
        assertEquals(List.of(0, 1, 6), ps.stream().map(Pcb::respuesta).toList());
        assertEquals(6.67, dosDecimales(Metricas.retornoMedio(ps)), EPS);
        assertEquals(3.33, dosDecimales(Metricas.esperaMedia(ps)), EPS);
        assertEquals(2.33, dosDecimales(Metricas.respuestaMedia(ps)), EPS);
    }

    @Test @DisplayName("[P17] Taller ej. 20 c: utilización útil 83,33 % y throughput 0,25 en la ventana 0-12")
    void ej20Ventana() {
        List<Pcb> ps = List.of(new Pcb(1, 0, 5), new Pcb(2, 1, 3), new Pcb(3, 2, 2));
        reproducir(ps, new int[] {1, 1, 2, 2, 2, 1, 1, 1, 3, 3, 0, 0});

        assertEquals(83.33, dosDecimales(Metricas.utilizacionUtil(ps, 12)), EPS);
        assertEquals(0.25, Metricas.throughput(ps, 12), EPS);
    }

    @Test @DisplayName("[P17] Taller ej. 20 d: respuesta 0 no implica espera 0 (P1 esperó 3)")
    void respuestaNoEsEspera() {
        List<Pcb> ps = List.of(new Pcb(1, 0, 5), new Pcb(2, 1, 3), new Pcb(3, 2, 2));
        reproducir(ps, new int[] {1, 1, 2, 2, 2, 1, 1, 1, 3, 3, 0, 0});
        assertEquals(0, ps.get(0).respuesta());
        assertEquals(3, ps.get(0).espera());
    }

    @Test @DisplayName("[P17] Sin E/S, espera = retorno − CPU para cada proceso")
    void esperaSinEs() {
        List<Pcb> ps = List.of(new Pcb(1, 0, 4), new Pcb(2, 1, 3), new Pcb(3, 2, 1));
        SimuladorCpu.Resultado r = new SimuladorCpu(new Fcfs(), ps).ejecutar();
        for (Pcb p : r.procesos()) assertEquals(p.retorno() - p.burst, p.espera());
        assertEquals(100.0, r.utilizacionUtil(), EPS);              // sin huecos: CPU siempre ocupada
        assertEquals(3.0 / 8, r.throughput(), EPS);
    }

    @Test @DisplayName("[P17] Un proceso que termina fuera de la ventana no cuenta en el throughput")
    void fueraDeVentana() {
        List<Pcb> ps = List.of(new Pcb(1, 0, 2), new Pcb(2, 0, 4));
        reproducir(ps, new int[] {1, 1, 2, 2, 2, 2});
        assertEquals(0.25, Metricas.throughput(ps, 4), EPS);       // solo P1 terminó antes de t = 4
    }

    @Test @DisplayName("[P17] Un intervalo observado de 0 o negativo se rechaza")
    void intervaloInvalido() {
        assertThrows(ParametroInvalidoException.class, () -> Metricas.utilizacionUtil(List.of(), 0));
        assertThrows(ParametroInvalidoException.class, () -> Metricas.throughput(List.of(), -1));
    }

    @Test @DisplayName("[P17] El resultado acepta una ventana observada mayor que la simulación")
    void ventanaObservada() {
        List<Pcb> ps = List.of(new Pcb(1, 0, 4), new Pcb(2, 1, 3), new Pcb(3, 2, 1));
        SimuladorCpu.Resultado r = new SimuladorCpu(new Fcfs(), ps).ejecutar();   // termina en t = 8
        assertEquals(80.0, r.utilizacionUtil(10), EPS);                            // 8 de CPU útil en 10
        assertEquals(0.3, r.throughput(10), EPS);                                  // 3 procesos en 10
        assertThrows(ParametroInvalidoException.class, () -> r.utilizacionUtil(7));
    }
}
