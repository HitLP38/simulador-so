package pe.uni.so.excepciones;

/** Un parámetro de configuración del simulador está fuera de su rango válido. */
public class ParametroInvalidoException extends SimuladorException {
    public ParametroInvalidoException(String mensaje) { super(mensaje); }
}
