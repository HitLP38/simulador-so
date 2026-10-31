package pe.uni.so.excepciones;

/**
 * [M1] La página existe en el espacio virtual del proceso pero no está en un marco (bit de
 * validez en i). Es la excepción 0x0E: el SO la carga desde swap y el proceso reintenta.
 */
public class FalloPaginaException extends SimuladorException {
    public final int pid;
    public final long pagina;

    public FalloPaginaException(int pid, long pagina) {
        super("P" + pid + ": fallo de página en la página " + pagina + " (no está en memoria)");
        this.pid = pid;
        this.pagina = pagina;
    }
}
