package pe.uni.so.memoria;

import java.util.EnumMap;
import java.util.Map;
import pe.uni.so.excepciones.ParametroInvalidoException;

/**
 * [I1][I3][I5][M3] Espacio lógico de un proceso (Sesión 4, «Memoria de los procesos»):
 * texto (ejecutable), datos (20–40 % del texto), heap (10–30 % del texto, tamaño máximo) y
 * pila (tamaño fijo configurable). Cada segmento se divide en páginas del mismo tamaño B que
 * los marcos (I4) y empieza en un límite de página.
 *
 * <p>Disposición lógica en un espacio de 32 bits (como x86): texto desde 0x0000_0000, luego datos
 * y heap, que crece hacia arriba; la pila ocupa el tope y crece hacia abajo. Entre el heap y la
 * pila queda un hueco que no pertenece al proceso: acceder ahí es un acceso ilegal (E01).
 */
public final class EspacioProceso {

    public enum Segmento { TEXTO, DATOS, HEAP, PILA }

    /** Tope del espacio lógico de 32 bits. */
    public static final long LIMITE_VIRTUAL = 1L << 32;
    public static final long PILA_POR_DEFECTO = 64 * 1024;

    private final long bloque;
    private final Map<Segmento, Long> tamano = new EnumMap<>(Segmento.class);

    /**
     * @param texto       tamaño del ejecutable en bytes
     * @param fraccionDatos datos = fraccionDatos × texto, entre 0,20 y 0,40
     * @param fraccionHeap  heap máximo = fraccionHeap × texto, entre 0,10 y 0,30
     * @param pila        tamaño fijo de la pila en bytes
     * @param bloque      tamaño de página y de marco: potencia de 2 entre 32 KiB y 2 MiB
     */
    public EspacioProceso(long texto, double fraccionDatos, double fraccionHeap, long pila, long bloque) {
        MapaBits.validarBloque(bloque);
        if (texto <= 0) throw new ParametroInvalidoException("El ejecutable debe medir más de 0 bytes (" + texto + ")");
        if (pila <= 0) throw new ParametroInvalidoException("La pila debe medir más de 0 bytes (" + pila + ")");
        if (fraccionDatos < 0.20 || fraccionDatos > 0.40)
            throw new ParametroInvalidoException("Datos debe ser 20–40 % del texto (" + fraccionDatos + ")");
        if (fraccionHeap < 0.10 || fraccionHeap > 0.30)
            throw new ParametroInvalidoException("Heap debe ser 10–30 % del texto (" + fraccionHeap + ")");
        this.bloque = bloque;
        tamano.put(Segmento.TEXTO, texto);
        tamano.put(Segmento.DATOS, Math.round(fraccionDatos * texto));
        tamano.put(Segmento.HEAP, Math.round(fraccionHeap * texto));
        tamano.put(Segmento.PILA, pila);
        if (base(Segmento.HEAP) + paginas(Segmento.HEAP) * bloque > base(Segmento.PILA))
            throw new ParametroInvalidoException("El proceso no cabe en el espacio lógico de 32 bits");
    }

    public EspacioProceso(long texto, double fraccionDatos, double fraccionHeap, long bloque) {
        this(texto, fraccionDatos, fraccionHeap, PILA_POR_DEFECTO, bloque);
    }

    public long bloque() { return bloque; }
    public long tamano(Segmento s) { return tamano.get(s); }
    public long total() { return tamano.values().stream().mapToLong(Long::longValue).sum(); }

    /** [I3] Páginas del segmento: ⌈tamaño / B⌉. */
    public int paginas(Segmento s) { return (int) MapaBits.bloques(tamano(s), bloque); }
    public int paginasTotales() { int n = 0; for (Segmento s : Segmento.values()) n += paginas(s); return n; }

    /** [I5] Fragmentación interna del segmento: lo que sobra en su última página. */
    public long desperdicio(Segmento s) { return MapaBits.desperdicio(tamano(s), bloque); }
    public long desperdicioTotal() { long d = 0; for (Segmento s : Segmento.values()) d += desperdicio(s); return d; }

    /** [I5] Desperdicio como porcentaje de los bytes reservados en páginas. */
    public double porcentajeDesperdicio() { return 100.0 * desperdicioTotal() / ((long) paginasTotales() * bloque); }

    /** [M3] Dirección lógica donde empieza el segmento. */
    public long base(Segmento s) {
        return switch (s) {
            case TEXTO -> 0;
            case DATOS -> (long) paginas(Segmento.TEXTO) * bloque;
            case HEAP -> base(Segmento.DATOS) + (long) paginas(Segmento.DATOS) * bloque;
            case PILA -> LIMITE_VIRTUAL - (long) paginas(Segmento.PILA) * bloque;
        };
    }

    /** [M3] Primera dirección lógica fuera del segmento (para la pila, el tope del espacio). */
    public long limite(Segmento s) { return base(s) + (long) paginas(s) * bloque; }

    /** [M3] Segmento al que pertenece una dirección lógica, o null si cae en el hueco o fuera del espacio. */
    public Segmento segmentoDe(long direccion) {
        for (Segmento s : Segmento.values())
            if (direccion >= base(s) && direccion < limite(s)) return s;
        return null;
    }
}
