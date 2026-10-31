package pe.uni.so.memoria;

import java.util.Map;
import java.util.TreeMap;
import pe.uni.so.excepciones.EventoInvalidoException;

/**
 * [M4][M1] Tabla de páginas de un proceso (Sesión 8): número de página → marco, con los bits
 * de validez, referencia y modificación. Solo guarda las páginas que el proceso tiene en su
 * espacio virtual; una página ausente o inválida produce un fallo de página.
 */
public class TablaPaginas {

    /** Entrada de la tabla: marco y bits de control. */
    public static final class Entrada {
        private int marco;
        private boolean valida, referenciada, modificada;

        public int marco() { return marco; }
        public boolean valida() { return valida; }
        public boolean referenciada() { return referenciada; }
        public boolean modificada() { return modificada; }
        void marcarUso(boolean escritura) { referenciada = true; if (escritura) modificada = true; }
    }

    private final Map<Long, Entrada> entradas = new TreeMap<>();

    /** La página queda en el marco indicado y su bit de validez pasa a v. */
    public void cargar(long pagina, int marco) {
        if (marco < 0) throw new EventoInvalidoException("Marco negativo: " + marco);
        Entrada e = entradas.computeIfAbsent(pagina, p -> new Entrada());
        e.marco = marco;
        e.valida = true;
        e.referenciada = false;
        e.modificada = false;
    }

    /** [M3] Carga páginas consecutivas en marcos consecutivos (un segmento en un hueco). */
    public void cargarContiguo(long primeraPagina, int paginas, int primerMarco) {
        for (int i = 0; i < paginas; i++) cargar(primeraPagina + i, primerMarco + i);
    }

    /** La página sale de memoria (swap-out o reemplazo): su bit de validez pasa a i. */
    public void invalidar(long pagina) {
        Entrada e = entradas.get(pagina);
        if (e != null) e.valida = false;
    }

    /** Entrada de la página, o null si nunca se cargó. */
    public Entrada entrada(long pagina) { return entradas.get(pagina); }

    public int paginasValidas() { return (int) entradas.values().stream().filter(Entrada::valida).count(); }
}
