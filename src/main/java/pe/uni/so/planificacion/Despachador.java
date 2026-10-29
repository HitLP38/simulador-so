package pe.uni.so.planificacion;

import java.util.ArrayList;
import java.util.List;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.procesos.Colas;
import pe.uni.so.procesos.Pcb;

/**
 * [P8] Despachador: transfiere la CPU al proceso que elige el planificador.
 * El planificador (la política) decide QUIÉN ejecuta; el despachador hace el cambio de contexto
 * sobre las colas reales y su costo no cuenta como tiempo útil (taller 2026-2, ej. 17 c).
 */
public class Despachador {
    private final double costo;          // ms por despacho, incluido el inicial
    private int despachos;
    private final List<String> bitacora = new ArrayList<>();

    public Despachador(double costo) {
        if (!Double.isFinite(costo) || costo < 0)
            throw new ParametroInvalidoException("Costo de despacho inválido: " + costo + " ms (debe ser finito y >= 0)");
        this.costo = costo;
    }

    /**
     * Cambio de contexto en el orden del taller, ej. 10 b. Si la CPU está ocupada, el proceso
     * saliente se guarda y vuelve a listos (expropiación o fin de quantum); si quedó libre por
     * bloqueo o finalización, solo se carga el entrante. Devuelve el instante en que el entrante
     * empieza a ejecutar, o {@code t} sin costo si no hay a quién despachar.
     */
    public double despachar(Colas colas, PoliticaPlanificacion politica, double t) {
        Pcb saliente = colas.enCpu();
        if (saliente == null && colas.listos().isEmpty()) return t;
        if (saliente != null) {
            bitacora.add("1. Guardar el contexto de " + saliente + " en su PCB (PC = " + saliente.pc() + ")");
            colas.expropiarEnCpu();
            bitacora.add("2. Pasar " + saliente + " a Listo, al final de la cola de listos");
        }
        Pcb entrante = colas.despachar(politica);
        if (entrante == null) return t;                  // solo quedan procesos suspendidos
        bitacora.add("3. " + politica.nombre() + " eligió a " + entrante + "; pasa a Ejecutando");
        bitacora.add("4. Restaurar el contexto de " + entrante + " y reanudar en PC = " + entrante.pc());
        despachos++;
        return t + costo;
    }

    public int despachos() { return despachos; }
    public double tiempoDespacho() { return despachos * costo; }
    public List<String> bitacora() { return List.copyOf(bitacora); }

    /** Fracción útil = tiempo útil / (tiempo útil + tiempo de despacho). */
    public double fraccionUtil(double tiempoUtil) {
        return tiempoUtil / (tiempoUtil + tiempoDespacho());
    }

    /** Régimen sostenido de ciclos q + c (taller ej. 11 a): útil = q / (q + c). */
    public static double fraccionUtilCiclo(double quantum, double costo) {
        return quantum / (quantum + costo);
    }

    /** Menor quantum con sobrecosto c / (q + c) ≤ máximo (taller ej. 11 c). */
    public static double quantumMinimo(double costo, double sobrecostoMaximo) {
        return costo * (1 - sobrecostoMaximo) / sobrecostoMaximo;
    }
}
