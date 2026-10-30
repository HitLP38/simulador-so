package pe.uni.so.interrupciones;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import pe.uni.so.excepciones.EventoInvalidoException;

/**
 * [P2] Controlador programable de interrupciones (PIC, modelo 8259 de x86), reutilizado del
 * simulador de la expo 2. Solo atiende interrupciones de HARDWARE, que son asíncronas:
 * <ul>
 *   <li>IRR: pedidos pendientes (un bit por línea IRQ).</li>
 *   <li>IMR: líneas enmascaradas; una IRQ enmascarada queda pendiente pero no se entrega.</li>
 *   <li>ISR: interrupciones en servicio; es una pila porque una de mayor prioridad puede
 *       interrumpir a la rutina de una de menor prioridad (anidamiento).</li>
 * </ul>
 * Prioridad fija: menor número de IRQ = mayor prioridad (IRQ0 timer, IRQ1 teclado…).
 * Las excepciones y la llamada al sistema 0x80 son síncronas: las provoca la instrucción que
 * se está ejecutando y la CPU las atiende de inmediato, sin pasar por el PIC.
 */
public class ControladorInterrupciones {
    private static final Comparator<TipoInterrupcion> POR_PRIORIDAD = Comparator.comparingInt(ControladorInterrupciones::irq);

    private final Set<TipoInterrupcion> irr = EnumSet.noneOf(TipoInterrupcion.class);
    private final Set<TipoInterrupcion> imr = EnumSet.noneOf(TipoInterrupcion.class);
    private final Deque<TipoInterrupcion> isr = new ArrayDeque<>();
    private final Map<TipoInterrupcion, Integer> atendidas = new EnumMap<>(TipoInterrupcion.class);

    /** Línea IRQ de una interrupción de hardware: vector − 0x20 (convención x86). */
    public static int irq(TipoInterrupcion t) {
        exigirHardware(t, "no tiene línea IRQ");
        return t.vector - 0x20;
    }

    /** Excepciones y llamadas al sistema: las provoca la propia instrucción. */
    public static boolean esSincrona(TipoInterrupcion t) { return t.clase != TipoInterrupcion.Clase.HARDWARE; }

    /** Un dispositivo (o el reloj) levanta su línea. Si ya estaba pendiente, el bit sigue en 1. */
    public void levantar(TipoInterrupcion t) {
        exigirHardware(t, "es síncrona y la atiende la CPU directamente, no el PIC");
        irr.add(t);
    }

    public void enmascarar(TipoInterrupcion t) { exigirHardware(t, "no se puede enmascarar"); imr.add(t); }
    public void desenmascarar(TipoInterrupcion t) { imr.remove(t); }

    /**
     * La que se entregaría ahora a la CPU: la pendiente no enmascarada de mayor prioridad,
     * siempre que supere a la que está en servicio. Si no supera, espera al EOI.
     */
    public Optional<TipoInterrupcion> siguiente() {
        Optional<TipoInterrupcion> candidata = irr.stream().filter(t -> !imr.contains(t)).min(POR_PRIORIDAD);
        TipoInterrupcion enServicio = isr.peek();
        if (enServicio != null && candidata.isPresent() && irq(candidata.get()) >= irq(enServicio)) return Optional.empty();
        return candidata;
    }

    /** Reconocimiento (INTA): la interrupción pasa de IRR a ISR y se devuelve su vector para buscar en la IVT. */
    public Optional<TipoInterrupcion> reconocer() {
        Optional<TipoInterrupcion> t = siguiente();
        t.ifPresent(i -> {
            irr.remove(i);
            isr.push(i);
            atendidas.merge(i, 1, Integer::sum);
        });
        return t;
    }

    /** Fin de interrupción (EOI): la rutina terminó y se libera la de mayor prioridad en servicio. */
    public TipoInterrupcion finDeInterrupcion() {
        if (isr.isEmpty()) throw new EventoInvalidoException("EOI sin ninguna interrupción en servicio");
        return isr.pop();
    }

    public boolean pendiente(TipoInterrupcion t) { return irr.contains(t); }
    public boolean enmascarada(TipoInterrupcion t) { return imr.contains(t); }
    public Optional<TipoInterrupcion> enServicio() { return Optional.ofNullable(isr.peek()); }
    public int anidamiento() { return isr.size(); }
    public int atendidas(TipoInterrupcion t) { return atendidas.getOrDefault(t, 0); }

    private static void exigirHardware(TipoInterrupcion t, String motivo) {
        if (esSincrona(t)) throw new EventoInvalidoException(t + " (0x" + Integer.toHexString(t.vector) + ") " + motivo);
    }
}
