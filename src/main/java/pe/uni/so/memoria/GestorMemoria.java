package pe.uni.so.memoria;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.uni.so.excepciones.AccesoIlegalException;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.MemoriaInsuficienteException;
import pe.uni.so.excepciones.ParametroInvalidoException;

/**
 * [I6][M5][M6][M3] Gestor de memoria: segmentación paginada (supuesto 2 de las ambigüedades).
 * Al admitir un proceso, cada segmento pide ⌈tamaño/B⌉ marcos; la estrategia (primer, mejor o
 * peor ajuste) elige el hueco del mapa de bits. Si ningún hueco alcanza, el segmento se reparte
 * en varios huecos elegidos por la misma estrategia. Con los marcos se arma la tabla de páginas
 * y se registra en la MMU, que desde ese momento traduce sus direcciones lógicas a físicas.
 *
 * <p>[M2] Asignación dinámica: al admitir se cargan texto y datos completos, el heap vacío y una
 * página de pila. Durante la ejecución, {@code malloc} hace crecer el heap hacia arriba y las
 * llamadas hacen crecer la pila hacia abajo; cada crecimiento pide marcos nuevos.
 */
public class GestorMemoria {

    /** Marcos contiguos entregados a un segmento. */
    public record Tramo(int marcoInicial, int marcos) {}

    /** [I6] Estado de la memoria en un tick: lo que se registra constantemente durante la simulación. */
    public record Muestra(int t, int libres, int ocupados, int huecos, int mayorHueco, long desperdicioInterno, int residentes) {
        /** Fragmentación externa: marcos libres que no forman parte del hueco más grande. */
        public int fragmentacionExterna() { return libres - mayorHueco; }
    }

    private static final class Residente {
        final EspacioProceso espacio;
        final TablaPaginas tabla;
        final List<Tramo> tramos = new ArrayList<>();
        int paginasHeap, paginasPila;
        long heapUsado, pilaUsada;

        Residente(EspacioProceso espacio, TablaPaginas tabla) { this.espacio = espacio; this.tabla = tabla; }
    }

    private final MapaBits mapa;
    private final EstrategiaAsignacion estrategia;
    private final Mmu mmu;
    private final Map<Integer, Residente> residentes = new LinkedHashMap<>();
    private long comparaciones;

    public GestorMemoria(MapaBits mapa, long bloque, EstrategiaAsignacion estrategia) {
        if (mapa == null || estrategia == null) throw new EventoInvalidoException("El gestor necesita mapa de bits y estrategia");
        this.mapa = mapa;
        this.estrategia = estrategia;
        this.mmu = new Mmu(bloque);                       // valida que B sea 2^n entre 32 KiB y 2 MiB
    }

    public GestorMemoria(int marcos, long bloque, EstrategiaAsignacion estrategia) {
        this(new MapaBits(marcos), bloque, estrategia);
    }

    /** ¿Hay marcos libres suficientes ahora? (el reparto en varios huecos permite usar todos). */
    public boolean cabe(EspacioProceso e) { return paginasIniciales(e) <= mapa.libres(); }

    /** [M2] Páginas que se cargan al admitir: texto y datos completos, heap vacío y 1 página de pila. */
    public static int paginasIniciales(EspacioProceso e) {
        return e.paginas(EspacioProceso.Segmento.TEXTO) + e.paginas(EspacioProceso.Segmento.DATOS) + 1;
    }

    /** ¿Cabría con la memoria vacía? Si no, el proceso nunca podrá admitirse. */
    public boolean podriaCaber(EspacioProceso e) { return e.paginasTotales() <= mapa.total(); }

    /**
     * [M6] Entrega k marcos. Si un hueco alcanza, la estrategia elige cuál; si ninguno alcanza, la
     * misma estrategia elige un hueco tras otro hasta completar. Cuenta los huecos revisados (M8).
     */
    public List<Tramo> asignarMarcos(int k) {
        if (k < 0) throw new EventoInvalidoException("No se pueden pedir " + k + " marcos");
        if (k > mapa.libres()) throw new MemoriaInsuficienteException(k, mapa.libres());
        List<Tramo> tramos = new ArrayList<>();
        int falta = k;
        while (falta > 0) {
            List<MapaBits.Hueco> huecos = mapa.huecos();
            List<Integer> tamanos = huecos.stream().map(MapaBits.Hueco::tamano).toList();
            EstrategiaAsignacion.Eleccion eleccion = estrategia.elegir(tamanos, falta);
            comparaciones += eleccion.comparaciones();
            if (eleccion.indice() < 0) {                     // ninguno alcanza: se toma un hueco parcial
                eleccion = estrategia.elegir(tamanos, 1);
                comparaciones += eleccion.comparaciones();
            }
            MapaBits.Hueco h = huecos.get(eleccion.indice());
            int n = Math.min(falta, h.tamano());
            mapa.ocupar(h.inicio(), n);
            tramos.add(new Tramo(h.inicio(), n));
            falta -= n;
        }
        return tramos;
    }

    /** [I6][M3] Admisión: marcos para cada segmento, tabla de páginas y registro en la MMU. */
    public TablaPaginas asignar(int pid, EspacioProceso e) {
        if (e == null) throw new EventoInvalidoException("P" + pid + " no tiene espacio de proceso");
        if (residentes.containsKey(pid)) throw new EventoInvalidoException("P" + pid + " ya tiene memoria asignada");
        if (e.bloque() != mmu.bloque())
            throw new EventoInvalidoException("P" + pid + ": su página (" + e.bloque() + " B) no coincide con el marco (" + mmu.bloque() + " B)");
        if (!cabe(e)) throw new MemoriaInsuficienteException(paginasIniciales(e), mapa.libres());

        Residente r = new Residente(e, new TablaPaginas());
        cargar(r, e.base(EspacioProceso.Segmento.TEXTO) / e.bloque(), e.paginas(EspacioProceso.Segmento.TEXTO));
        cargar(r, e.base(EspacioProceso.Segmento.DATOS) / e.bloque(), e.paginas(EspacioProceso.Segmento.DATOS));
        cargar(r, paginaTopePila(e) - 1, 1);                          // la pila empieza con su página más alta
        r.paginasPila = 1;
        mmu.registrar(pid, e, r.tabla);
        residentes.put(pid, r);
        return r.tabla;
    }

    /**
     * [M2] {@code malloc}: el heap crece hacia arriba. Si se pasa de su tamaño máximo o no hay
     * marcos libres, devuelve false (malloc devuelve NULL) y no cambia nada.
     */
    public boolean crecerHeap(int pid, long bytes) {
        if (bytes <= 0) throw new ParametroInvalidoException("malloc necesita más de 0 bytes (" + bytes + ")");
        Residente r = exigir(pid);
        long nuevo = r.heapUsado + bytes;
        if (nuevo > r.espacio.tamano(EspacioProceso.Segmento.HEAP)) return false;
        int necesarias = Math.toIntExact(MapaBits.bloques(nuevo, r.espacio.bloque())) - r.paginasHeap;
        if (necesarias > mapa.libres()) return false;
        if (necesarias > 0) {
            cargar(r, r.espacio.base(EspacioProceso.Segmento.HEAP) / r.espacio.bloque() + r.paginasHeap, necesarias);
            r.paginasHeap += necesarias;
        }
        r.heapUsado = nuevo;
        return true;
    }

    /**
     * [M2] Una llamada apila {@code bytes}: la pila crece hacia abajo, hacia el hueco. Pasar su
     * tamaño máximo es un desbordamiento de pila (acceso ilegal, E01); sin marcos libres no puede crecer.
     */
    public void crecerPila(int pid, long bytes) {
        if (bytes <= 0) throw new ParametroInvalidoException("Una llamada apila más de 0 bytes (" + bytes + ")");
        Residente r = exigir(pid);
        long nuevo = r.pilaUsada + bytes;
        EspacioProceso e = r.espacio;
        if (nuevo > e.tamano(EspacioProceso.Segmento.PILA)) throw new AccesoIlegalException(pid, e.base(EspacioProceso.Segmento.PILA) - 1);
        int necesarias = Math.toIntExact(MapaBits.bloques(nuevo, e.bloque())) - r.paginasPila;
        if (necesarias > mapa.libres()) throw new MemoriaInsuficienteException(necesarias, mapa.libres());
        if (necesarias > 0) {
            cargar(r, paginaTopePila(e) - r.paginasPila - necesarias, necesarias);
            r.paginasPila += necesarias;
        }
        r.pilaUsada = nuevo;
    }

    /** Pide marcos y los registra en la tabla del proceso a partir de la página indicada. */
    private void cargar(Residente r, long pagina, int paginas) {
        for (Tramo tr : asignarMarcos(paginas)) {
            r.tabla.cargarContiguo(pagina, tr.marcos(), tr.marcoInicial());
            pagina += tr.marcos();
            r.tramos.add(tr);
        }
    }

    private static long paginaTopePila(EspacioProceso e) { return EspacioProceso.LIMITE_VIRTUAL / e.bloque(); }

    private Residente exigir(int pid) {
        Residente r = residentes.get(pid);
        if (r == null) throw new EventoInvalidoException("P" + pid + " no tiene memoria asignada");
        return r;
    }

    /** El proceso terminó o se suspendió: sus marcos vuelven al mapa de bits. */
    public void liberar(int pid) {
        Residente r = residentes.remove(pid);
        if (r == null) throw new EventoInvalidoException("P" + pid + " no tiene memoria asignada");
        for (Tramo tr : r.tramos) mapa.liberar(tr.marcoInicial(), tr.marcos());
        mmu.olvidar(pid);
    }

    public boolean residente(int pid) { return residentes.containsKey(pid); }
    public List<Tramo> tramos(int pid) { Residente r = residentes.get(pid); return r == null ? List.of() : List.copyOf(r.tramos); }
    public long heapUsado(int pid) { return exigir(pid).heapUsado; }
    public long pilaUsada(int pid) { return exigir(pid).pilaUsada; }
    public int paginasHeap(int pid) { return exigir(pid).paginasHeap; }
    public int paginasPila(int pid) { return exigir(pid).paginasPila; }

    /** [I5] Fragmentación interna de todos los procesos residentes. */
    public long desperdicioInterno() {
        return residentes.values().stream().mapToLong(r -> r.espacio.desperdicioTotal()).sum();
    }

    /** [I6] Foto del estado de la memoria en el instante t. */
    public Muestra muestra(int t) {
        return new Muestra(t, mapa.libres(), mapa.total() - mapa.libres(), mapa.huecos().size(),
                mapa.mayorHueco(), desperdicioInterno(), residentes.size());
    }

    public MapaBits mapa() { return mapa; }
    public Mmu mmu() { return mmu; }
    public EstrategiaAsignacion estrategia() { return estrategia; }
    public long comparaciones() { return comparaciones; }
}
