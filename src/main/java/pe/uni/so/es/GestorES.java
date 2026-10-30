package pe.uni.so.es;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pe.uni.so.interrupciones.ControladorInterrupciones;
import pe.uni.so.procesos.Colas;
import pe.uni.so.procesos.Pcb;

/**
 * [E1][E5][E6] Gestor de E/S: cada dispositivo atiende su cola FIFO de a un pedido por vez.
 * El primero de la cola está en servicio; en cada tick su controlador descuenta una unidad de
 * la duración y, al llegar a 0, levanta la IRQ del dispositivo en el PIC (E/S por
 * interrupciones, Sesión 9). El Kernel atiende esa IRQ al inicio del tick siguiente.
 *
 * <p>Un pedido sin bloque de petición registrado (el atajo de las pruebas) no se atiende solo:
 * lo termina quien lo creó con {@code Kernel.finES}.
 */
public class GestorES {

    /** Pedido en servicio en un dispositivo y ticks que le faltan. */
    private static final class Servicio {
        final Pcb proceso;
        int restante;
        Servicio(Pcb proceso, int duracion) { this.proceso = proceso; this.restante = duracion; }
    }

    private final Colas colas;
    private final ControladorInterrupciones pic;
    private final Map<Pcb, SolicitudES> solicitudes = new HashMap<>();
    private final Map<Dispositivo, Servicio> enServicio = new EnumMap<>(Dispositivo.class);

    public GestorES(Colas colas, ControladorInterrupciones pic) {
        this.colas = colas;
        this.pic = pic;
    }

    /** [E4] Registra el bloque de petición de un proceso ya bloqueado en la cola del dispositivo. */
    public void registrar(Pcb p, SolicitudES s) { solicitudes.put(p, s); }

    /** Un tick de trabajo de cada controlador. Devuelve los dispositivos que terminaron (IRQ levantada). */
    public List<Dispositivo> avanzar() {
        List<Dispositivo> terminados = new java.util.ArrayList<>();
        for (Dispositivo d : Dispositivo.values()) {
            List<Pcb> cola = colas.cola(d);
            if (cola.isEmpty()) continue;
            Pcb primero = cola.get(0);
            Servicio s = enServicio.get(d);
            if (s == null || s.proceso != primero) {               // empieza el siguiente de la cola
                SolicitudES pedido = solicitudes.get(primero);
                if (pedido == null) continue;                        // pedido manual: no se atiende solo
                s = new Servicio(primero, pedido.duracion());
                enServicio.put(d, s);
            }
            if (s.restante == 0) continue;                           // ya terminó; su IRQ espera (p. ej. enmascarada)
            if (--s.restante == 0) {
                pic.levantar(d.interrupcion);
                terminados.add(d);
            }
        }
        return terminados;
    }

    /**
     * [E9] El dispositivo termina ya el pedido que está atendiendo (p. ej. el usuario pulsó una
     * tecla antes del plazo) y levanta su IRQ. Si ya había terminado, no hace nada.
     */
    public void forzarFin(Dispositivo d) {
        List<Pcb> cola = colas.cola(d);
        if (cola.isEmpty()) return;
        Servicio s = enServicio.get(d);
        if (s == null || s.proceso != cola.get(0)) {
            s = new Servicio(cola.get(0), 1);
            enServicio.put(d, s);
        }
        if (s.restante == 0) return;
        s.restante = 0;
        pic.levantar(d.interrupcion);
    }

    /** [E6] El Kernel atendió la IRQ: el pedido se cierra y el dispositivo queda libre para el siguiente. */
    public void completar(Pcb p) {
        solicitudes.remove(p);
        enServicio.values().removeIf(s -> s.proceso == p);
    }

    /**
     * ¿Puede la E/S seguir avanzando sola? Sí, si algún dispositivo tiene un pedido real por
     * empezar o en curso, o si ya terminó y su IRQ no está enmascarada (se atenderá en el próximo
     * tick). Una IRQ enmascarada no cuenta: nadie la va a entregar mientras la máscara siga puesta.
     */
    public boolean ocupado() {
        for (Dispositivo d : Dispositivo.values()) {
            List<Pcb> cola = colas.cola(d);
            if (cola.isEmpty() || !solicitudes.containsKey(cola.get(0))) continue;
            if (!servicioTerminado(d) || !pic.enmascarada(d.interrupcion)) return true;
        }
        return false;
    }

    /** [E6] ¿El pedido que está primero en la cola ya cumplió toda su duración? */
    public boolean servicioTerminado(Dispositivo d) {
        Servicio s = enServicio.get(d);
        List<Pcb> cola = colas.cola(d);
        return s != null && !cola.isEmpty() && s.proceso == cola.get(0) && s.restante == 0;
    }

    public SolicitudES solicitud(Pcb p) { return solicitudes.get(p); }

    /** Ticks que le faltan al pedido en servicio del dispositivo, o -1 si no atiende ninguno. */
    public int restante(Dispositivo d) {
        Servicio s = enServicio.get(d);
        return s == null ? -1 : s.restante;
    }
}
