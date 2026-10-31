package pe.uni.so.procesos;

import pe.uni.so.es.Dispositivo;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.excepciones.TransicionInvalidaException;
import pe.uni.so.memoria.EspacioProceso;

/**
 * [P3] Bloque de control de proceso. Su disposición en memoria (22 campos con desplazamiento)
 * está en {@link CampoPcb}; {@link #leer(CampoPcb)} devuelve el valor de cada campo.
 */
public class Pcb {
    public enum Tipo { USUARIO, SISTEMA }

    /** [P3] El campo BURST guarda total y restante en 16 bits cada uno. */
    public static final int BURST_MAXIMO = 0xFFFF;

    public final int pid;
    public final int llegada;
    public final int burst;
    public final int prioridad;      // menor número = mayor prioridad (convención del profesor)
    public final int memoria;        // [P5] memoria que ocupa residente (unidades del simulador)
    private final int ppid = 1;      // todo proceso de usuario cuelga de init (PID 1)
    private final Tipo tipo = Tipo.USUARIO;

    private Estado estado = Estado.NUEVO;
    private int restante;
    private int pc;                  // [P3] contador de programa: avanza 4 bytes por instrucción
    private int inicio = -1, fin = -1, espera = 0, cpu = 0, bloqueado = 0;
    private int pedidosES, interrupciones;   // [E3] pedidos de E/S pendientes y llamadas/IRQ propias
    private CodigoError error;              // [P9] null si terminó normalmente
    private EspacioProceso espacio;         // [I1] segmentos del proceso; null en las pruebas sin memoria real
    private boolean cancelacionPendiente;   // [E9] señal CANCELAR aún no entregada
    private int quantumRestante;
    private Dispositivo dispositivo;  // [P3] dispositivo por el que espera; null si no espera E/S
    private boolean suspendido;      // [P5] en swap: atributo de Listo o Bloqueado, no un sexto estado

    public Pcb(int pid, int llegada, int burst, int prioridad, int memoria) {
        if (burst <= 0 || burst > BURST_MAXIMO)
            throw new ParametroInvalidoException("P" + pid + ": el burst debe estar entre 1 y " + BURST_MAXIMO + " (" + burst + ")");
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
    public void sumarBloqueado() { bloqueado++; }
    /** [E2] El proceso hizo una llamada al sistema para pedir E/S. */
    public void registrarPedidoES() { pedidosES++; interrupciones++; }
    /** [E9] El usuario pidió cancelarlo; la señal se entrega cuando vuelva a la CPU. */
    public void cancelar() {
        if (estado == Estado.TERMINADO) throw new EventoInvalidoException(this + " ya terminó");
        cancelacionPendiente = true;
    }
    public boolean cancelacionPendiente() { return cancelacionPendiente; }

    /** [P9] Registra el código con el que el proceso termina por error. */
    public void registrarError(CodigoError c) { error = c; cancelacionPendiente = false; }
    public CodigoError error() { return error; }

    /** [I1] Asigna los segmentos del proceso antes de su admisión. */
    public void asignarEspacio(EspacioProceso e) {
        if (estado != Estado.NUEVO) throw new EventoInvalidoException(this + " ya fue admitido: su espacio no puede cambiar");
        espacio = e;
    }
    public EspacioProceso espacio() { return espacio; }

    /** [E6] Terminó uno de sus pedidos de E/S (la IRQ de fin también cuenta como interrupción suya). */
    public void completarPedidoES() {
        if (pedidosES == 0) throw new EventoInvalidoException(this + " no tiene pedidos de E/S pendientes");
        pedidosES--;
        interrupciones++;
    }
    public void fijarQuantumRestante(int q) { quantumRestante = q; }
    void esperar(Dispositivo d) { dispositivo = d; }       // lo usan las colas al bloquear y al terminar la E/S

    /** [P3] Valor del campo tal como quedaría guardado en la PCB (enteros de 32 bits). */
    public int leer(CampoPcb campo) {
        return switch (campo) {
            case PID -> pid;
            case PPID -> ppid;
            case TIPO -> tipo.ordinal();
            case ESTADO -> estado.ordinal();
            case PC -> pc;
            case BURST -> burst << 16 | restante;          // 16 bits altos: total; bajos: restante
            case PRIORIDAD -> prioridad;
            case QUANTUM_RESTANTE -> quantumRestante;
            case LLEGADA -> llegada;
            case PRIMERA_CPU -> inicio;
            case FIN -> fin;
            case CPU -> cpu;
            case ESPERA -> espera;
            case BLOQUEADO -> bloqueado;
            case DISPOSITIVO -> dispositivo == null ? -1 : dispositivo.ordinal();
            // Reservados: registros y SP con la CPU (Fase 5); páginas, segmentos y marcos con la memoria (Fase 4)
            case PEDIDOS_ES -> pedidosES;
            case ERROR_E_INTERRUPCIONES -> (error == null ? 0 : error.numero) << 16 | (interrupciones & 0xFFFF);
            case REGISTROS, SP, TABLA_PAGINAS, SEGMENTOS, MARCOS -> 0;
        };
    }

    public Estado estado() { return estado; }
    public boolean suspendido() { return suspendido; }
    public int restante() { return restante; }
    public int pc() { return pc; }
    public int inicio() { return inicio; }
    public int fin() { return fin; }
    public int espera() { return espera; }
    public int cpu() { return cpu; }
    public int bloqueado() { return bloqueado; }
    public int pedidosES() { return pedidosES; }
    public int interrupciones() { return interrupciones; }
    public int quantumRestante() { return quantumRestante; }
    public Dispositivo dispositivo() { return dispositivo; }
    public int ppid() { return ppid; }
    public Tipo tipo() { return tipo; }

    // Indicadores con las definiciones del taller 2026-2
    public int retorno() { return fin - llegada; }
    public int respuesta() { return inicio - llegada; }

    @Override public String toString() { return "P" + pid; }
}
