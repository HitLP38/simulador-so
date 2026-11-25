package pe.uni.so.procesos;

import pe.uni.so.interrupciones.TipoInterrupcion;

/**
 * [P9] Códigos con los que un proceso termina por error; se guardan en la PCB (16 bits altos
 * del campo ERROR_E_INTERRUPCIONES) y en las estadísticas. E01–E03 los detecta el módulo de
 * errores (excepciones síncronas de la CPU); E04 lo produce la señal de teclado (E9).
 */
public enum CodigoError {
    E01(1, "Acceso ilegal a memoria", TipoInterrupcion.ACCESO_ILEGAL),
    E02(2, "División por cero", TipoInterrupcion.DIVISION_CERO),
    E03(3, "Instrucción inválida", TipoInterrupcion.INSTRUCCION_INVALIDA),
    E04(4, "Cancelado por el usuario", null);

    public final int numero;
    public final String descripcion;
    /** [P9][I1] Excepción de la CPU que lo produce; null si no viene de una excepción (E04: señal). */
    public final TipoInterrupcion excepcion;

    CodigoError(int numero, String descripcion, TipoInterrupcion excepcion) {
        this.numero = numero;
        this.descripcion = descripcion;
        this.excepcion = excepcion;
    }

    /** [P9] Los códigos que detecta el módulo de errores (E01–E03). */
    public static final java.util.List<CodigoError> DE_EXCEPCION = java.util.List.of(E01, E02, E03);

    /** [P10] Ec. 7: código del proceso errado elegido con U (un tercio cada uno). */
    public static CodigoError conU(double u) {
        return DE_EXCEPCION.get(Math.min(DE_EXCEPCION.size() - 1, (int) Math.floor(u * DE_EXCEPCION.size())));
    }

    /** «E01 · Acceso ilegal a memoria (0x0D)». */
    public String etiqueta() {
        return name() + " · " + descripcion + (excepcion == null ? "" : String.format(" (0x%02X)", excepcion.vector));
    }
}
