package pe.uni.so.procesos;

import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.excepciones.TransicionInvalidaException;

/**
 * [P3] Bloque de control de proceso. Solo los campos que usa el motor hasta ahora;
 * la tabla completa de 22 campos está en docs/arquitectura.
 */
public class Pcb {
    public final int pid;
    public final int llegada;
    public final int burst;
    public final int prioridad;      // menor número = mayor prioridad (convención del profesor)
    public final int memoria;        // [P5] memoria que ocupa residente (unidades del simulador)

    private Estado estado = Estado.NUEVO;
    private int restante;
    private int pc;                  // [P3] contador de programa: avanza 4 bytes por instrucción
    private int inicio = -1, fin = -1, espera = 0, cpu = 0;
    private boolean suspendido;      // [P5] en swap: atributo de Listo o Bloqueado, no un sexto estado

    public Pcb(int pid, int llegada, int burst, int prioridad, int memoria) {
        if (memoria < 0) throw new ParametroInvalidoException("P" + pid + ": memoria negativa (" + memoria + ")");
        this.pid = pid; this.llegada = llegada; this.burst = burst; this.prioridad = prioridad;
        this.memoria = memoria;
        this.restante = burst;
    }

    public Pcb(int pid, int llegada, int burst, int prioridad) { this(pid, llegada, burst, prioridad, 0); }

    public Pcb(int pid, int llegada, int burst) { this(pid, llegada, burst, 0); }

    /** Única puerta para cambiar de estado: rechaza lo que el modelo no permite. */
    public void pasarA(Estado destino) {
        if (!estado.puedePasarA(destino)) throw new TransicionInvalidaException(pid, estado, destino);
        estado = destino;
    }

    /** Ejecuta una instrucción (una unidad de tiempo). */
    public void ejecutarInstruccion() { restante--; cpu++; pc += 4; }

    /** [P5] El planificador de mediano plazo saca al proceso de memoria (swap-out). */
    public void suspender() {
        if (suspendido || (estado != Estado.LISTO && estado != Estado.BLOQUEADO))
            throw new EventoInvalidoException("No se puede suspender " + this + " en estado " + estado);
        suspendido = true;
    }

    /** [P5] El proceso vuelve a memoria (swap-in) y puede volver a competir por la CPU. */
    public void reanudar() {
        if (!suspendido) throw new EventoInvalidoException(this + " no está suspendido");
        suspendido = false;
    }

    public void marcarInicio(int t) { if (inicio < 0) inicio = t; }
    public void marcarFin(int t) { fin = t; }
    public void sumarEspera() { espera++; }

    public Estado estado() { return estado; }
    public boolean suspendido() { return suspendido; }
    public int restante() { return restante; }
    public int pc() { return pc; }
    public int inicio() { return inicio; }
    public int fin() { return fin; }
    public int espera() { return espera; }
    public int cpu() { return cpu; }

    // Indicadores con las definiciones del taller 2026-2
    public int retorno() { return fin - llegada; }
    public int respuesta() { return inicio - llegada; }

    @Override public String toString() { return "P" + pid; }
}
