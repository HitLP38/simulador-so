package pe.uni.so.excepciones;

/** Un evento del simulador no tiene a qué proceso aplicarse (CPU libre o cola de dispositivo vacía). */
public class EventoInvalidoException extends SimuladorException {
    public EventoInvalidoException(String mensaje) { super(mensaje); }
}
