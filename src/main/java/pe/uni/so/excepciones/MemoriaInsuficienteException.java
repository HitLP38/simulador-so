package pe.uni.so.excepciones;

/** [I6] No hay marcos libres suficientes: el planificador de largo plazo no admite el proceso. */
public class MemoriaInsuficienteException extends SimuladorException {
    public MemoriaInsuficienteException(int pedidos, int libres) {
        super("Se piden " + pedidos + " marcos y solo hay " + libres + " libres");
    }
}
