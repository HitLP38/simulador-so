package pe.uni.so.excepciones;

/** [P9] Acceso fuera de los límites del proceso: termina con código E01. */
public class AccesoIlegalException extends SimuladorException {
    public AccesoIlegalException(int pid, long direccion) {
        super("P" + pid + ": acceso ilegal a 0x" + Long.toHexString(direccion).toUpperCase());
    }
}
