package pe.uni.so.es;

import pe.uni.so.interrupciones.TipoInterrupcion;

/** [P4][E1] Los 5 dispositivos del simulador, cada uno con su vector de interrupción. */
public enum Dispositivo {
    TECLADO(TipoInterrupcion.TECLADO),
    DISCO(TipoInterrupcion.DISCO),
    IMPRESORA(TipoInterrupcion.IMPRESORA),
    PANTALLA(TipoInterrupcion.PANTALLA),
    RED(TipoInterrupcion.RED);

    public final TipoInterrupcion interrupcion;

    Dispositivo(TipoInterrupcion interrupcion) { this.interrupcion = interrupcion; }

    /** Dispositivo que levanta esa IRQ, o null si el vector no es de un dispositivo (timer, excepciones). */
    public static Dispositivo de(TipoInterrupcion t) {
        for (Dispositivo d : values()) if (d.interrupcion == t) return d;
        return null;
    }
}
