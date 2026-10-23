package pe.uni.so.interrupciones;

/** [P2] Tabla de vectores del proyecto (numeración x86): 11 vectores en 3 clases. */
public enum TipoInterrupcion {
    DIVISION_CERO(0x00, Clase.EXCEPCION),
    INSTRUCCION_INVALIDA(0x06, Clase.EXCEPCION),
    ACCESO_ILEGAL(0x0D, Clase.EXCEPCION),
    FALLO_PAGINA(0x0E, Clase.EXCEPCION),
    TIMER(0x20, Clase.HARDWARE),
    TECLADO(0x21, Clase.HARDWARE),
    DISCO(0x2E, Clase.HARDWARE),
    IMPRESORA(0x27, Clase.HARDWARE),
    PANTALLA(0x2A, Clase.HARDWARE),
    RED(0x2B, Clase.HARDWARE),
    LLAMADA_SISTEMA(0x80, Clase.SOFTWARE);

    public enum Clase { HARDWARE, SOFTWARE, EXCEPCION }

    public final int vector;
    public final Clase clase;

    TipoInterrupcion(int vector, Clase clase) { this.vector = vector; this.clase = clase; }
}
