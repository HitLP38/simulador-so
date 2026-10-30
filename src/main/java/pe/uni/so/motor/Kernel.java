package pe.uni.so.motor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.planificacion.Despachador;
import pe.uni.so.planificacion.PlanificadorLargoPlazo;
import pe.uni.so.planificacion.PoliticaPlanificacion;
import pe.uni.so.procesos.Colas;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

/**
 * [P6] Núcleo del simulador: avanza un tick a la vez y decide CUÁNDO se llama al planificador.
 * Cada momento a–e tiene su propio método y deja un evento en la bitácora.
 *
 * <p>Orden de un tick (el mismo de {@code SimuladorCpu}, que validan las pruebas del taller):
 * primero se registran todos los eventos del instante t (fin de E/S, llegadas y timer) y
 * recién después se toma UNA decisión de planificación; luego se ejecuta una instrucción.
 * Así un proceso que llega en t compite por la CPU en ese mismo t.
 *
 * <p>Contexto no expropiativo: en b, c y d el proceso solo se encola. Contexto expropiativo
 * (SRTF, Prioridad E): en c y d el candidato se compara contra el que ejecuta. El timer (b)
 * expropia siempre que haya otro proceso listo, porque el quantum ya se agotó.
 *
 * <p>[P7] El quantum se toma al despachar: cambiarlo en plena simulación rige desde el
 * siguiente despacho. [P8] La CPU entra en modo KERNEL con cada tick, llamada al sistema o IRQ,
 * y vuelve a USUARIO cuando el despachador entrega la CPU a un proceso.
 */
public class Kernel {

    /** Un evento de la bitácora: en qué instante, qué momento y a qué proceso le ocurrió. */
    public record Evento(int t, Momento momento, Pcb proceso, String detalle) {
        @Override public String toString() { return "t=" + t + " (" + momento + ") " + proceso + ": " + detalle; }
    }

    private final PoliticaPlanificacion politica;
    private final PlanificadorLargoPlazo largoPlazo;       // null: memoria sin límite
    private final Colas colas = new Colas();
    private final Despachador despachador = new Despachador(0);
    private final List<Pcb> procesos;
    private final List<Pcb> porLlegar;
    private final List<Evento> bitacora = new ArrayList<>();
    private final StringBuilder gantt = new StringBuilder();
    private int t, usado, desalojos;
    private int quantumDespacho;                           // [P7] el quantum vigente se fija al despachar
    private Modo modo = Modo.KERNEL;                        // [P8] bit de modo de la CPU
    private boolean revisarExpropiacion;                   // hubo un momento c o d en este instante

    public Kernel(PoliticaPlanificacion politica, List<Pcb> procesos, PlanificadorLargoPlazo largoPlazo) {
        this.politica = politica;
        this.largoPlazo = largoPlazo;
        this.procesos = List.copyOf(procesos);
        this.porLlegar = new ArrayList<>(procesos);
        porLlegar.sort(Comparator.comparingInt(p -> p.llegada));   // estable: respeta el orden de ingreso
    }

    public Kernel(PoliticaPlanificacion politica, List<Pcb> procesos) { this(politica, procesos, null); }

    /** Un tick: eventos del instante t, una decisión de planificación y una instrucción. */
    public void paso() {
        modo = Modo.KERNEL;                                // la interrupción del reloj entra al kernel
        momentoD();
        planificar();
        if (colas.enCpu() != null) modo = Modo.USUARIO;    // paso 4 del despachador: vuelve a modo usuario
        ejecutar();
        t++;
    }

    /**
     * Corre hasta que todos terminen. Falla si la CPU queda ociosa para siempre: nadie listo y
     * ninguna llegada futura (un proceso que ya llegó y no cabe tampoco cuenta, porque la memoria
     * la retienen procesos bloqueados o suspendidos que aquí nadie despierta).
     */
    public void ejecutarTodo() {
        while (!terminado()) {
            momentoD();                                    // intenta admitir antes de decidir
            boolean ociosa = colas.enCpu() == null && listosResidentes().isEmpty();
            boolean llegadaFutura = !porLlegar.isEmpty() && porLlegar.get(0).llegada > t;
            if (ociosa && !llegadaFutura)
                throw new EventoInvalidoException("t=" + t + ": nadie puede ejecutar; quedan procesos bloqueados, suspendidos o sin memoria");
            paso();
        }
    }

    // ---------------------------------------------------------------- los 5 momentos

    /** (a) El proceso en ejecución pide E/S: se bloquea en la cola del dispositivo y la CPU queda libre. */
    public void pedirES(Dispositivo d) {
        Pcb p = colas.enCpu();
        colas.bloquearEnCpu(d);                            // valida que haya alguien en la CPU
        modo = Modo.KERNEL;                                // llamada al sistema (vector 0x80)
        registrar(t, Momento.A, p, "pide E/S a " + d + " y espera en su cola");
    }

    /** (b) Ejecutando → Listo, por fin de quantum o porque otro lo expropia. */
    private void momentoB(Pcb saliente, String motivo) {
        registrar(t, Momento.B, saliente, motivo + "; vuelve al final de listos");
    }

    /** (c) Fin de E/S: el primero del dispositivo pasa a Listo (nunca directo a la CPU). */
    public void finES(Dispositivo d) {
        Pcb p = colas.terminarES(d);
        modo = Modo.KERNEL;                                // IRQ del dispositivo
        registrar(t, Momento.C, p, "terminó su E/S en " + d + " y pasa a Listo");
        revisarExpropiacion = true;
    }

    /** (d) Llegadas del instante t. Si hay largo plazo y no cabe, espera en Nuevo sin adelantarse. */
    private void momentoD() {
        while (!porLlegar.isEmpty() && porLlegar.get(0).llegada <= t) {
            Pcb p = porLlegar.get(0);
            if (largoPlazo == null) colas.admitir(p);
            else if (!largoPlazo.admitir(p, colas)) break;    // orden de llegada: evita la inanición
            porLlegar.remove(0);
            registrar(t, Momento.D, p, "admitido, pasa a Listo");
            revisarExpropiacion = true;
        }
    }

    /** (e) El proceso en ejecución terminó al final del tick: libera la CPU y su memoria. */
    private void momentoE(Pcb p) {
        p.marcarFin(t + 1);
        colas.terminarEnCpu();
        modo = Modo.KERNEL;                                // el fin del proceso entra al kernel (exit)
        if (largoPlazo != null && largoPlazo.residente(p)) largoPlazo.liberar(p);
        registrar(t + 1, Momento.E, p, "terminó y libera la CPU");
    }

    // ---------------------------------------------------------------- planificación

    /** Decide si hay cambio de contexto en este instante y, si lo hay, llama al despachador. */
    private void planificar() {
        Pcb actual = colas.enCpu();
        List<Pcb> candidatos = listosResidentes();
        boolean cambiar = actual == null;                  // momentos a y e: CPU libre

        if (actual != null && usado >= quantumDespacho) {              // (b) timer
            if (candidatos.isEmpty()) usado = 0;                        // solo: sigue con el mismo quantum
            else { cambiar = true; momentoB(actual, "agotó su quantum de " + quantumDespacho); }
        } else if (actual != null && revisarExpropiacion && !candidatos.isEmpty()) {   // (c)(d)
            Pcb candidato = politica.elegir(candidatos);
            if (politica.expropia(candidato, actual)) {
                cambiar = true;
                desalojos++;
                momentoB(actual, "expropiado por " + candidato + " (" + politica.nombre() + ")");
            }
        }
        revisarExpropiacion = false;

        if (!cambiar) return;
        despachador.despachar(colas, politica, t);         // guarda el saliente y carga el entrante
        Pcb entrante = colas.enCpu();
        if (entrante != null) { entrante.marcarInicio(t); usado = 0; quantumDespacho = politica.quantum(); }
    }

    private void ejecutar() {
        colas.listos().forEach(Pcb::sumarEspera);
        procesos.stream().filter(p -> p.estado() == Estado.BLOQUEADO).forEach(Pcb::sumarBloqueado);
        Pcb actual = colas.enCpu();
        gantt.append(actual == null ? "-" : actual.pid).append(' ');
        if (actual == null) return;
        actual.ejecutarInstruccion();
        usado++;
        actual.fijarQuantumRestante(quantumDespacho == Integer.MAX_VALUE ? 0 : quantumDespacho - usado);
        if (actual.restante() == 0) momentoE(actual);
    }

    private List<Pcb> listosResidentes() { return colas.listos().stream().filter(p -> !p.suspendido()).toList(); }

    private void registrar(int instante, Momento m, Pcb p, String detalle) {
        bitacora.add(new Evento(instante, m, p, detalle));
    }

    // ---------------------------------------------------------------- lectura (para la interfaz)

    public boolean terminado() { return procesos.stream().allMatch(p -> p.estado() == Estado.TERMINADO); }
    public int t() { return t; }
    public Colas colas() { return colas; }
    public List<Pcb> procesos() { return procesos; }
    public List<Evento> bitacora() { return List.copyOf(bitacora); }
    public List<Evento> eventos(Momento m) { return bitacora.stream().filter(e -> e.momento() == m).toList(); }
    public String gantt() { return gantt.toString().trim(); }
    public int desalojos() { return desalojos; }
    public Modo modo() { return modo; }
    public Despachador despachador() { return despachador; }
}
