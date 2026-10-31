package pe.uni.so.memoria;

import java.util.HashMap;
import java.util.Map;
import pe.uni.so.excepciones.AccesoIlegalException;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.FalloPaginaException;

/**
 * [M4] Unidad de gestión de memoria. Traduce una dirección virtual del proceso en ejecución:
 * <ol>
 *   <li>Protección: la dirección y todos los bytes del acceso deben caer dentro de un mismo
 *       segmento del proceso; si no, acceso ilegal (excepción 0x0D, el proceso termina con E01).</li>
 *   <li>Página = dirección / B y desplazamiento = dirección mod B (B = 2^n: corrimiento y máscara).</li>
 *   <li>Tabla de páginas: si la página no está en un marco (bit i), fallo de página (0x0E).</li>
 *   <li>Física = marco × B + desplazamiento; se marcan los bits de referencia y modificación.</li>
 * </ol>
 */
public class Mmu {

    /** Resultado de una traducción. */
    public record Traduccion(long pagina, long desplazamiento, int marco, long fisica) {}

    private record Contexto(EspacioProceso espacio, TablaPaginas tabla) {}

    private final long bloque;
    private final int bitsDesplazamiento;
    private final Map<Integer, Contexto> procesos = new HashMap<>();

    public Mmu(long bloque) {
        MapaBits.validarBloque(bloque);
        this.bloque = bloque;
        this.bitsDesplazamiento = Long.numberOfTrailingZeros(bloque);
    }

    /** El SO registra el espacio y la tabla del proceso (lo que apunta el PTBR en el cambio de contexto). */
    public void registrar(int pid, EspacioProceso espacio, TablaPaginas tabla) {
        if (espacio.bloque() != bloque)
            throw new EventoInvalidoException("P" + pid + ": su página (" + espacio.bloque() + " B) no coincide con el marco (" + bloque + " B)");
        procesos.put(pid, new Contexto(espacio, tabla));
    }

    public void olvidar(int pid) { procesos.remove(pid); }

    public long pagina(long direccion) { return direccion >>> bitsDesplazamiento; }
    public long desplazamiento(long direccion) { return direccion & (bloque - 1); }

    /** Traduce un acceso de lectura de 1 byte. */
    public Traduccion traducir(int pid, long direccion) { return traducir(pid, direccion, 1, false); }

    /** Traduce un acceso de {@code bytes} bytes; todos deben caer en el mismo segmento y en páginas cargadas. */
    public Traduccion traducir(int pid, long direccion, int bytes, boolean escritura) {
        Contexto c = procesos.get(pid);
        if (c == null) throw new EventoInvalidoException("P" + pid + " no tiene tabla de páginas en la MMU");
        if (bytes < 1) throw new EventoInvalidoException("Un acceso debe ser de al menos 1 byte");

        EspacioProceso.Segmento s = c.espacio().segmentoDe(direccion);
        if (s == null || bytes > c.espacio().limite(s) - direccion)      // taller p.1 ej. 13: dir + n ≤ límite
            throw new AccesoIlegalException(pid, direccion);

        long primera = pagina(direccion), ultima = pagina(direccion + bytes - 1);
        for (long p = primera; p <= ultima; p++) {
            TablaPaginas.Entrada e = c.tabla().entrada(p);
            if (e == null || !e.valida()) throw new FalloPaginaException(pid, p);
        }
        for (long p = primera; p <= ultima; p++) c.tabla().entrada(p).marcarUso(escritura);

        TablaPaginas.Entrada e = c.tabla().entrada(primera);
        return new Traduccion(primera, desplazamiento(direccion), e.marco(), (long) e.marco() * bloque + desplazamiento(direccion));
    }

    public long bloque() { return bloque; }
}
