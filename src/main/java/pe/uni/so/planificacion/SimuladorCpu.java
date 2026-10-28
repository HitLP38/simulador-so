package pe.uni.so.planificacion;

import java.util.ArrayList;
import java.util.List;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

/**
 * Planificación de corto plazo sin E/S, un tick por unidad de tiempo.
 * Es el núcleo que validan las pruebas del taller; el Kernel completo lo reutiliza.
 * Orden dentro de un tick: llegadas -> fin de quantum -> expropiación -> despacho -> ejecución.
 */
public class SimuladorCpu {
    private final PoliticaPlanificacion politica;
    private final List<Pcb> procesos;
    private final List<Pcb> listos = new ArrayList<>();
    private final StringBuilder gantt = new StringBuilder();
    private int transiciones, desalojos;

    public record Resultado(List<Pcb> procesos, int tiempoTotal, int transiciones, int desalojos, String gantt) {
        public double retornoMedio() { return Metricas.retornoMedio(procesos); }
        public double esperaMedia() { return Metricas.esperaMedia(procesos); }
        public double respuestaMedia() { return Metricas.respuestaMedia(procesos); }
        /** Ventana por defecto: de 0 hasta que termina el último proceso. */
        public double utilizacionUtil() { return utilizacionUtil(tiempoTotal); }
        public double throughput() { return throughput(tiempoTotal); }

        /**
         * Ventana observada indicada (ej. 20: de 0 a 12 aunque el último termine en 10).
         * La utilización suma la CPU de toda la simulación, así que la ventana debe cubrirla.
         */
        public double utilizacionUtil(int ventana) { return Metricas.utilizacionUtil(procesos, validar(ventana)); }

        /** Admite ventanas parciales: solo cuenta los procesos que terminaron dentro de ella. */
        public double throughput(int ventana) { return Metricas.throughput(procesos, ventana); }

        private int validar(int ventana) {
            if (ventana < tiempoTotal)
                throw new ParametroInvalidoException("La ventana (" + ventana + ") no cubre la simulación (" + tiempoTotal + ")");
            return ventana;
        }
    }

    public SimuladorCpu(PoliticaPlanificacion politica, List<Pcb> procesos) {
        this.politica = politica;
        this.procesos = procesos;
    }

    public Resultado ejecutar() {
        int t = 0, terminados = 0, usado = 0;
        Pcb actual = null, ultimo = null;
        while (terminados < procesos.size()) {
            for (Pcb p : procesos)                                   // (d) creación: entra a listos
                if (p.llegada == t) { p.pasarA(Estado.LISTO); listos.add(p); }

            if (actual != null && usado >= politica.quantum()) {     // (b) timer: fin de quantum
                if (listos.isEmpty()) usado = 0;                      // único listo: sigue sin cambio
                else { aListos(actual); actual = null; }
            }
            if (actual != null && !listos.isEmpty()                  // (b)(c)(d) expropiación
                    && politica.expropia(politica.elegir(listos), actual)) {
                aListos(actual); actual = null; desalojos++;
            }
            if (actual == null && !listos.isEmpty()) {               // despacho
                actual = politica.elegir(listos);
                listos.remove(actual);
                actual.pasarA(Estado.EJECUTANDO);
                actual.marcarInicio(t);
                if (ultimo != null && ultimo != actual) transiciones++;
                usado = 0;
            }

            listos.forEach(Pcb::sumarEspera);
            gantt.append(actual == null ? "-" : actual.pid).append(' ');
            if (actual != null) {
                actual.ejecutarInstruccion();
                usado++;
                ultimo = actual;
                if (actual.restante() == 0) {                         // (e) finalización
                    actual.marcarFin(t + 1);
                    actual.pasarA(Estado.TERMINADO);
                    terminados++;
                    actual = null;
                }
            }
            t++;
        }
        return new Resultado(procesos, t, transiciones, desalojos, gantt.toString().trim());
    }

    private void aListos(Pcb p) { p.pasarA(Estado.LISTO); listos.add(p); }
}
