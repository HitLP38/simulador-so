package pe.uni.so.excepciones;

/** Raíz de los errores del simulador (LP2 S03: excepciones propias, no genéricas). */
public class SimuladorException extends RuntimeException {
    public SimuladorException(String mensaje) { super(mensaje); }
}
