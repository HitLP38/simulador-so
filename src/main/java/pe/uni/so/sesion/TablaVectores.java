package pe.uni.so.sesion;

import java.util.ArrayList;
import java.util.List;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.interrupciones.ControladorInterrupciones;
import pe.uni.so.interrupciones.TipoInterrupcion;
import pe.uni.so.procesos.CodigoError;

/**
 * [I1][I2][P2] Tabla de vectores de interrupción (IVT) del simulador, lista para mostrar: vector,
 * clase, línea IRQ y prioridad en el PIC (las de hardware), dispositivo que la levanta y rutina
 * de servicio que la atiende. Es fija: la define {@link TipoInterrupcion}.
 */
public final class TablaVectores {

    /**
     * Una fila de la IVT.
     *
     * @param irq       línea del PIC 8259, o −1 si es síncrona (excepción o llamada al sistema)
     * @param prioridad 1 = la más alta entre las de hardware (menor IRQ); 0 si es síncrona (se atiende al instante)
     * @param generada  ¿la simulación la produce? Los fines de E/S pasan por el PIC; el TIMER se registra en cada
     *                  fin de quantum (el kernel lo resuelve sin IRQ); la int 0x80 en cada pedido de E/S (momento a,
     *                  la cuenta la CPU); las excepciones 0x00, 0x06 y 0x0D, en cada proceso que el módulo de errores aborta
     *                  (P9); el fallo de página solo con los accesos manuales a memoria
     */
    public record Fila(TipoInterrupcion tipo, int vector, TipoInterrupcion.Clase clase, int irq, int prioridad,
                       Dispositivo dispositivo, String rutina, boolean generada) {
        public String vectorHex() { return String.format("0x%02X", vector); }
        public boolean sincrona() { return irq < 0; }
    }

    private static final List<Fila> FILAS = armar();

    private TablaVectores() {}

    /** Las 11 filas, en orden de vector. */
    public static List<Fila> filas() { return FILAS; }

    /** Fila de un tipo de interrupción. */
    public static Fila de(TipoInterrupcion t) {
        for (Fila f : FILAS) if (f.tipo() == t) return f;
        throw new ParametroInvalidoException("No hay vector para la interrupción " + t);
    }

    private static List<Fila> armar() {
        List<TipoInterrupcion> hardware = new ArrayList<>();
        for (TipoInterrupcion t : TipoInterrupcion.values()) if (t.clase == TipoInterrupcion.Clase.HARDWARE) hardware.add(t);
        hardware.sort(java.util.Comparator.comparingInt(ControladorInterrupciones::irq));
        List<Fila> r = new ArrayList<>();
        for (TipoInterrupcion t : TipoInterrupcion.values()) {
            boolean hw = t.clase == TipoInterrupcion.Clase.HARDWARE;
            Dispositivo d = null;
            for (Dispositivo x : Dispositivo.values()) if (x.interrupcion == t) d = x;
            r.add(new Fila(t, t.vector, t.clase, hw ? ControladorInterrupciones.irq(t) : -1,
                    hw ? hardware.indexOf(t) + 1 : 0, d, rutina(t), generada(t, d)));
        }
        r.sort(java.util.Comparator.comparingInt(Fila::vector));
        return List.copyOf(r);
    }

    /**
     * Fines de E/S (dispositivo), TIMER (fin de quantum), int 0x80 (pedido de E/S) y las excepciones del módulo de
     * errores (E01–E03): las que produce la simulación.
     */
    private static boolean generada(TipoInterrupcion t, Dispositivo d) {
        if (d != null || t == TipoInterrupcion.TIMER || t == TipoInterrupcion.LLAMADA_SISTEMA) return true;
        for (CodigoError c : CodigoError.DE_EXCEPCION) if (c.excepcion == t) return true;
        return false;
    }

    /** Qué hace la rutina de servicio de cada vector en este simulador. */
    static String rutina(TipoInterrupcion t) {
        return switch (t) {
            case DIVISION_CERO -> "Aborta el proceso (error de división entre cero)";
            case INSTRUCCION_INVALIDA -> "Aborta el proceso (código de operación inválido)";
            case ACCESO_ILEGAL -> "Aborta el proceso (acceso fuera de sus segmentos)";
            case FALLO_PAGINA -> "Trae la página del swap a un marco (reemplazo local si la cuota está llena)";
            case TIMER -> "Fin de quantum (momento b): expropia al proceso en CPU y lo devuelve a Listos (el kernel lo resuelve sin pasar por el PIC)";
            case TECLADO -> "Fin de E/S del teclado: el proceso pasa de Bloqueado a Listo (momento c)";
            case DISCO -> "Fin de E/S del disco: el proceso pasa de Bloqueado a Listo (momento c)";
            case IMPRESORA -> "Fin de E/S de la impresora: el proceso pasa de Bloqueado a Listo (momento c)";
            case PANTALLA -> "Fin de E/S de la pantalla: el proceso pasa de Bloqueado a Listo (momento c)";
            case RED -> "Fin de E/S de la red: el proceso pasa de Bloqueado a Listo (momento c)";
            case LLAMADA_SISTEMA -> "Llamada al sistema (int 0x80): pedido de E/S, el proceso pasa a Bloqueado (momento a)";
        };
    }
}
