package pe.uni.so.memoria;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import pe.uni.so.excepciones.ParametroInvalidoException;

/**
 * [M7] Política de asignación de memoria física (Sesión 8).
 * <ul>
 *   <li>Asignación proporcional: el proceso i recibe fᵢ = ⌊(sᵢ / S)·m⌋ marcos (mínimo 1), donde sᵢ son
 *       sus páginas, S la suma de páginas de todos y m los marcos de la RAM.</li>
 *   <li>Reemplazo local: cuando un proceso usó toda su cuota, un fallo de página expulsa una página
 *       suya (FIFO o LRU), nunca la de otro proceso.</li>
 * </ul>
 * Cada objeto lleva los marcos de un proceso, numerados de 0 a cuota − 1 (posiciones locales).
 */
public class PoliticaFisica {

    /** Resultado de una referencia: si hubo fallo, en qué marco local quedó la página y a quién expulsó (−1: a nadie). */
    public record Referencia(boolean fallo, int marco, long victima) {}

    private final Reemplazo reemplazo;
    private final long[] paginas;          // página que ocupa cada marco local (−1: libre)
    private final long[] ultimoUso;        // LRU: instante de la última referencia
    private final long[] llegada;          // FIFO: instante en que se cargó
    private long reloj;
    private int fallos, reemplazos;

    public PoliticaFisica(Reemplazo reemplazo, int marcos) {
        if (reemplazo == null) throw new ParametroInvalidoException("Falta el algoritmo de reemplazo (FIFO o LRU)");
        if (marcos < 1) throw new ParametroInvalidoException("La cuota de marcos debe ser ≥ 1 (" + marcos + ")");
        this.reemplazo = reemplazo;
        this.paginas = new long[marcos];
        this.ultimoUso = new long[marcos];
        this.llegada = new long[marcos];
        Arrays.fill(paginas, -1);
    }

    /**
     * [M7] Cuotas proporcionales fᵢ = ⌊(sᵢ / S)·m⌋, con un mínimo de 1 marco por proceso.
     * Los marcos que sobran por el redondeo quedan libres; si el mínimo de 1 hace que la suma
     * pase de m, se descuenta de las cuotas más grandes hasta que la suma sea m.
     */
    public static int[] cuotas(int[] paginas, int m) {
        if (paginas == null || paginas.length == 0) throw new ParametroInvalidoException("No hay procesos para repartir marcos");
        if (m < paginas.length) throw new ParametroInvalidoException("Con " + m + " marcos no alcanza 1 por proceso (" + paginas.length + ")");
        long total = 0;
        for (int s : paginas) {
            if (s < 1) throw new ParametroInvalidoException("Cada proceso tiene al menos 1 página (" + s + ")");
            total += s;
        }
        int[] f = new int[paginas.length];
        long suma = 0;
        for (int i = 0; i < paginas.length; i++) suma += f[i] = (int) Math.max(1, paginas[i] * (long) m / total);
        while (suma > m) {                                 // el mínimo de 1 puede pasarse de m: se quita al de mayor cuota
            int mayor = 0;
            for (int i = 1; i < f.length; i++) if (f[i] > f[mayor]) mayor = i;
            f[mayor]--;
            suma--;
        }
        return f;
    }

    /**
     * El proceso referencia una página. Si está en un marco es un acierto (LRU actualiza su último
     * uso); si no, es un fallo: ocupa un marco libre de la cuota o reemplaza a la víctima del algoritmo.
     */
    public Referencia referenciar(long pagina) {
        if (pagina < 0) throw new ParametroInvalidoException("Página negativa: " + pagina);
        reloj++;
        int marco = buscar(pagina);
        if (marco >= 0) {
            ultimoUso[marco] = reloj;
            return new Referencia(false, marco, -1);
        }
        fallos++;
        marco = buscar(-1);
        long victima = -1;
        if (marco < 0) {
            marco = victima();
            victima = paginas[marco];
            reemplazos++;
        }
        paginas[marco] = pagina;
        llegada[marco] = reloj;
        ultimoUso[marco] = reloj;
        return new Referencia(true, marco, victima);
    }

    /** Marco local de la víctima: la que llegó primero (FIFO) o la usada hace más tiempo (LRU). */
    private int victima() {
        long[] criterio = reemplazo == Reemplazo.FIFO ? llegada : ultimoUso;
        int v = 0;
        for (int i = 1; i < criterio.length; i++) if (criterio[i] < criterio[v]) v = i;
        return v;
    }

    private int buscar(long pagina) {
        for (int i = 0; i < paginas.length; i++) if (paginas[i] == pagina) return i;
        return -1;
    }

    /** Contenido de cada marco local (null: libre), como las columnas de la tabla de la PC2. */
    public List<Long> marcos() {
        List<Long> foto = new ArrayList<>();
        for (long p : paginas) foto.add(p < 0 ? null : p);
        return Collections.unmodifiableList(foto);
    }

    public Reemplazo reemplazo() { return reemplazo; }
    public int cuota() { return paginas.length; }
    public int fallos() { return fallos; }
    public int reemplazos() { return reemplazos; }
}
