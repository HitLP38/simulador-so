package pe.uni.so.procesos;

/**
 * [P9] Códigos con los que un proceso termina por error; se guardan en la PCB (16 bits altos
 * del campo ERROR_E_INTERRUPCIONES) y en las estadísticas. E01–E03 los detecta el módulo de
 * errores (Fase 5); E04 lo produce la señal de teclado (E9).
 */
public enum CodigoError {
    E01(1, "Acceso ilegal a memoria"),
    E02(2, "División por cero"),
    E03(3, "Instrucción inválida"),
    E04(4, "Cancelado por el usuario");

    public final int numero;
    public final String descripcion;

    CodigoError(int numero, String descripcion) {
        this.numero = numero;
        this.descripcion = descripcion;
    }
}
