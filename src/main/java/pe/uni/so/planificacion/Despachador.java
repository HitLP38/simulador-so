package pe.uni.so.planificacion;

import java.util.ArrayList;
import java.util.List;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.procesos.Pcb;

/**
 * [P8] Despachador: transfiere la CPU al proceso que eligió el planificador.
 * El planificador decide QUIÉN ejecuta; el despachador hace el cambio de contexto y su costo
 * no cuenta como tiempo útil (taller 2026-2, ej. 17 c).
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
     * Cambio de contexto en el orden del taller, ej. 10 b: primero se guarda el saliente y
     * solo después se restaura el entrante. Devuelve el instante en que el entrante empieza a ejecutar.
     */
    public double despachar(Pcb saliente, Pcb entrante, double t) {
        if (saliente != null) {
            bitacora.add("1. Guardar el contexto de " + saliente + " en su PCB (PC = " + saliente.pc() + ")");
            bitacora.add("2. Actualizar el estado de " + saliente + " y moverlo a su cola");
        }
        bitacora.add("3. Confirmar a " + entrante + " como elegido por el planificador");
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
