package pe.uni.so.motor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.SolicitudES;
import pe.uni.so.es.VelocidadDispositivo;
import pe.uni.so.interrupciones.TipoInterrupcion;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.memoria.Reemplazo;
import pe.uni.so.procesos.CodigoError;
import pe.uni.so.procesos.Estado;

/**
 * [P16] Foto inmutable de la máquina al inicio del instante {@code t}, es decir, al cerrar el
 * tick [t − 1, t). Es lo único que leen las vistas: CPU, colas, PCB de cada proceso, PIC,
 * dispositivos y memoria. No guarda referencias a objetos vivos del simulador (ni PCB ni colas),
 * así que una foto vieja no cambia aunque la simulación siga, y se puede entregar a otro hilo
 * (por ejemplo, al de JavaFX) sin copiarla.
 *
 * <p>La foto inicial ({@code t = 0}) se toma al crear el Kernel; luego hay una por tick.
 *
 * @param t           instante de la foto
 * @param cpu         estado del procesador
 * @param nuevos      PID de los que ya intentaron entrar y siguen en Nuevo (sin memoria o grado máximo), en orden
 * @param listos      PID de la cola de listos, en orden
 * @param procesos    una ficha por proceso, en el orden de ingreso
 * @param dispositivos una ficha por dispositivo, en el orden del enum
 * @param pic         registros del controlador de interrupciones
 * @param memoria     resumen de la memoria, o null si la simulación no tiene gestor de memoria
 * @param sucesos     eventos de la bitácora ocurridos durante este tick
 */
public record Instantanea(int t, Cpu cpu, List<Integer> nuevos, List<Integer> listos, List<Proceso> procesos,
                          List<FichaDispositivo> dispositivos, Pic pic, Memoria memoria, List<Suceso> sucesos) {

    /** PID que indica «ninguno» (CPU libre, dispositivo sin pedido). */
    public static final int NINGUNO = -1;

    public Instantanea {
        nuevos = List.copyOf(nuevos);
        listos = List.copyOf(listos);
        procesos = List.copyOf(procesos);
        dispositivos = List.copyOf(dispositivos);
        sucesos = List.copyOf(sucesos);
    }

    /**
     * Marcas que se dibujan junto al color del estado. No son estados: el modelo sigue siendo
     * el de 5 estados de la Sesión 4.
     */
    public enum Insignia {
        /** Vuelve a Listo en este tick porque otro le quitó la CPU (contexto expropiativo). */
        EXPROPIADO,
        /** Vuelve a Listo en este tick porque agotó su quantum (Round Robin). */
        FIN_QUANTUM,
        /** Su memoria está en el área de intercambio (planificador de mediano plazo). */
        SUSPENDIDO,
        /** Intentó entrar en el momento d y sigue en Nuevo: no cabe en memoria o se alcanzó el grado máximo. */
        ESPERA_MEMORIA,
        /** Tiene la señal CANCELAR del teclado pendiente de entrega. */
        CANCELACION_PENDIENTE,
        /** Terminó con un código de error. */
        ERROR
    }

    /**
     * Ficha de un proceso: los campos de su PCB que muestra la vista y cómo llegó a su estado.
     *
     * @param anterior   estado en la foto anterior (igual a {@code estado} si no cambió)
     * @param color      color del estado en hexadecimal, según {@link ColorEstado}
     * @param momento    momento a–e que causó el cambio en este tick, o null
     * @param motivo     explicación del último cambio de este tick, o cadena vacía
     * @param dispositivo dispositivo en cuya cola espera, o null
     * @param marcos     marcos de RAM que ocupa ahora (0 si no es residente)
     * @param cuota      marcos reservados por la asignación proporcional (0 sin paginación bajo demanda)
     * @param error      código de error, o null si no falló
     */
    public record Proceso(int pid, int llegada, int burst, int restante, int prioridad, Estado estado, Estado anterior,
                          String color, Set<Insignia> insignias, Momento momento, String motivo, int pc,
                          int quantumRestante, Dispositivo dispositivo, int espera, int cpu, int bloqueado,
                          int pedidosES, int marcos, int cuota, CodigoError error) {
        public Proceso { insignias = Set.copyOf(insignias); }

        /** ¿Cambió de estado en este tick? (lo que la vista anima) */
        public boolean cambio() { return estado != anterior; }

        public boolean tiene(Insignia i) { return insignias.contains(i); }

        /** PC en hexadecimal, como lo muestra la tabla de PCB. */
        public String pcHex() { return "0x" + String.format("%08X", pc); }
    }

    /**
     * Estado del procesador.
     *
     * @param enCpu        PID que tiene la CPU ahora, o {@link #NINGUNO}
     * @param ejecuto      PID que usó la CPU durante el tick que cerró, o {@link #NINGUNO} (celda del Gantt)
     * @param quantum      quantum vigente desde el último despacho (0: sin límite)
     * @param usado        ticks que lleva el proceso en CPU desde su despacho
     * @param velocidad    instrucciones que ejecuta la CPU por tick
     */
    public record Cpu(Modo modo, int enCpu, int ejecuto, String politica, int quantum, int usado, double velocidad,
                      int despachos, int desalojos, double tiempoDespacho) {
        public boolean libre() { return enCpu == NINGUNO; }
    }

    /**
     * Registros del PIC 8259: IRR (pendientes), IMR (enmascaradas) e ISR (en servicio, la de arriba primero).
     * La IVT es fija y está en {@link TipoInterrupcion}.
     */
    public record Pic(List<TipoInterrupcion> pendientes, List<TipoInterrupcion> enmascaradas,
                      List<TipoInterrupcion> enServicio, Map<TipoInterrupcion, Integer> atendidas) {
        public Pic {
            pendientes = List.copyOf(pendientes);
            enmascaradas = List.copyOf(enmascaradas);
            enServicio = List.copyOf(enServicio);
            atendidas = Map.copyOf(atendidas);
        }

        public int atendidas(TipoInterrupcion t) { return atendidas.getOrDefault(t, 0); }
    }

    /**
     * Un dispositivo y su cola FIFO.
     *
     * @param cola       PID en espera, el primero es el que se atiende
     * @param atendiendo PID del pedido en servicio, o {@link #NINGUNO}
     * @param restante   ticks que le faltan al pedido en servicio, o -1 si no atiende ninguno
     * @param operacion  operación del pedido en servicio, o null
     */
    public record FichaDispositivo(Dispositivo dispositivo, VelocidadDispositivo velocidad, List<Integer> cola,
                                   int atendiendo, int restante, SolicitudES.Operacion operacion) {
        public FichaDispositivo { cola = List.copyOf(cola); }

        public int irq() { return dispositivo.interrupcion.vector - 0x20; }
        public boolean libre() { return atendiendo == NINGUNO; }
    }

    /**
     * Resumen de la memoria física.
     *
     * @param muestra    marcos libres y ocupados, huecos y fragmentación de este instante
     * @param reemplazo  política de reemplazo, o null si se carga todo el proceso al admitirlo
     */
    public record Memoria(int marcos, GestorMemoria.Muestra muestra, String estrategia, Reemplazo reemplazo, int fallosPagina) {}

    /** Un evento de la bitácora, sin referencias al PCB vivo. */
    public record Suceso(int t, Momento momento, int pid, String detalle) {
        @Override public String toString() { return "t=" + t + " (" + momento + ") P" + pid + ": " + detalle; }
    }

    public Optional<Proceso> proceso(int pid) { return procesos.stream().filter(p -> p.pid() == pid).findFirst(); }

    public List<Proceso> en(Estado e) { return procesos.stream().filter(p -> p.estado() == e).toList(); }

    public FichaDispositivo dispositivo(Dispositivo d) {
        return dispositivos.stream().filter(f -> f.dispositivo() == d).findFirst().orElseThrow();
    }

    /** Procesos que cambiaron de estado en este tick. */
    public List<Proceso> cambios() { return procesos.stream().filter(Proceso::cambio).toList(); }

    /** ¿Ya terminaron todos? */
    public boolean terminada() { return procesos.stream().allMatch(p -> p.estado() == Estado.TERMINADO); }
}
