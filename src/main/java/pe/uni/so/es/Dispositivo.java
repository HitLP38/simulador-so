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
}
