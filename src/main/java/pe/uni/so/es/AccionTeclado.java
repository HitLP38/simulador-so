package pe.uni.so.es;

import pe.uni.so.excepciones.ParametroInvalidoException;

/**
 * [E9] Señal que el usuario envía con el teclado al proceso que espera una lectura de teclado:
 * C cancela el proceso (terminará con E04) y Enter lo deja continuar. Si no hay respuesta antes
 * de que venza la duración del pedido (el plazo), el pedido termina solo y el proceso continúa.
 */
public enum AccionTeclado {
    CANCELAR, CONTINUAR;

    public static AccionTeclado de(char tecla) {
        if (Character.toUpperCase(tecla) == 'C') return CANCELAR;
        if (tecla == '\n' || tecla == '\r') return CONTINUAR;
        throw new ParametroInvalidoException("Tecla no reconocida: use C para cancelar o Enter para continuar");
    }
}
