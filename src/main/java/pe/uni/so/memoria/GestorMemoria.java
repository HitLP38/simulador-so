package pe.uni.so.memoria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.uni.so.excepciones.AccesoIlegalException;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.FalloPaginaException;
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
 *
 * <p>[M1][M7] Paginación bajo demanda (si se configura un algoritmo de reemplazo): cada proceso
 * recibe su cuota proporcional de marcos y todas sus páginas quedan en el espacio virtual con
 * bit i; la primera referencia a cada una produce un fallo de página que la carga en un marco de
 * su cuota y, si la cuota está llena, reemplaza una página del mismo proceso (reemplazo local).
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
        PoliticaFisica politica;                          // [M7] solo bajo demanda
        int[] propios;                                    // [M7] marco real de cada marco local de la cuota

        Residente(EspacioProceso espacio, TablaPaginas tabla) { this.espacio = espacio; this.tabla = tabla; }
    }

    /** [M2][P5] Crecimiento del heap y la pila que se conserva mientras el proceso está suspendido. */
    private record Crecimiento(EspacioProceso espacio, int paginasHeap, int paginasPila, long heapUsado, long pilaUsada) {}

    private final MapaBits mapa;
    private final EstrategiaAsignacion estrategia;
    private final Mmu mmu;
    private final Map<Integer, Residente> residentes = new LinkedHashMap<>();
    private final Map<Integer, Crecimiento> suspendidos = new LinkedHashMap<>();
    private final Reemplazo reemplazo;                    // null: carga total al admitir
    private final Map<Integer, Integer> cuotas = new HashMap<>();
    private long comparaciones;
    private int fallosPagina;

    /** [M1][M7] Con {@code reemplazo} distinto de null, la memoria trabaja bajo demanda con reemplazo local. */
    public GestorMemoria(MapaBits mapa, long bloque, EstrategiaAsignacion estrategia, Reemplazo reemplazo) {
        if (mapa == null || estrategia == null) throw new EventoInvalidoException("El gestor necesita mapa de bits y estrategia");
        this.mapa = mapa;
        this.estrategia = estrategia;
        this.mmu = new Mmu(bloque);                       // valida que B sea 2^n entre 32 KiB y 2 MiB
        this.reemplazo = reemplazo;
    }

    public GestorMemoria(MapaBits mapa, long bloque, EstrategiaAsignacion estrategia) { this(mapa, bloque, estrategia, null); }

    public GestorMemoria(int marcos, long bloque, EstrategiaAsignacion estrategia) {
        this(new MapaBits(marcos), bloque, estrategia);
    }

    public GestorMemoria(int marcos, long bloque, EstrategiaAsignacion estrategia, Reemplazo reemplazo) {
        this(new MapaBits(marcos), bloque, estrategia, reemplazo);
    }

    /** ¿Trabaja bajo demanda (carga parcial) en vez de cargar todo al admitir? */
    public boolean bajoDemanda() { return reemplazo != null; }

    /**
     * [M7] Reparte los marcos de la RAM entre los procesos de la simulación: fᵢ = ⌊(sᵢ / S)·m⌋,
     * sin pasar de las páginas que el proceso puede llegar a usar.
     */
    public void repartirCuotas(Map<Integer, EspacioProceso> procesos) {
        if (!bajoDemanda()) throw new EventoInvalidoException("Las cuotas solo se usan con paginación bajo demanda");
        if (procesos == null) throw new ParametroInvalidoException("Faltan los procesos para repartir marcos");
        List<Integer> pids = new ArrayList<>(procesos.keySet());
        int[] paginas = new int[pids.size()];
        for (int i = 0; i < paginas.length; i++) {
            EspacioProceso e = procesos.get(pids.get(i));
            if (e == null) throw new EventoInvalidoException("P" + pids.get(i) + " no tiene espacio de proceso");
            paginas[i] = e.paginasTotales();
        }
        int[] f = PoliticaFisica.cuotas(paginas, mapa.total());
        cuotas.clear();
        for (int i = 0; i < f.length; i++) cuotas.put(pids.get(i), Math.min(f[i], paginas[i]));
    }

    /** [M7] Marcos asignados al proceso por la política proporcional. */
    public int cuota(int pid) {
        Integer f = cuotas.get(pid);
        if (f == null) throw new EventoInvalidoException("P" + pid + " no tiene cuota de marcos: falta repartirCuotas");
        return f;
    }

    /** ¿Hay marcos libres suficientes ahora? (el reparto en varios huecos permite usar todos). */
    public boolean cabe(EspacioProceso e) { return paginasIniciales(e) <= mapa.libres(); }

    /**
     * [P5] Igual que {@link #cabe(EspacioProceso)}, pero un proceso suspendido necesita también lo que
     * había crecido. [M7] Bajo demanda, lo que necesita es su cuota.
     */
    public boolean cabe(int pid, EspacioProceso e) { return marcosNecesarios(pid, e) <= mapa.libres(); }

    /** Marcos que el proceso ocupa al (re)admitirse: su cuota bajo demanda o sus páginas cargadas con carga total. */
    public int marcosNecesarios(int pid, EspacioProceso e) { return bajoDemanda() ? cuota(pid) : paginasAlCargar(pid, e); }

    /** [M2] Páginas que se cargan al admitir: texto y datos completos, heap vacío y 1 página de pila. */
    public static int paginasIniciales(EspacioProceso e) {
        return e.paginas(EspacioProceso.Segmento.TEXTO) + e.paginas(EspacioProceso.Segmento.DATOS) + 1;
    }

    /**
     * ¿Cabría con la memoria vacía? Si no, el proceso nunca podrá admitirse. Se compara lo que se
     * carga al admitir: si después no hay marcos para crecer, malloc devuelve NULL (M2).
     */
    public boolean podriaCaber(EspacioProceso e) { return paginasIniciales(e) <= mapa.total(); }

    /** Igual que {@link #podriaCaber(EspacioProceso)}, considerando la cuota bajo demanda. */
    public boolean podriaCaber(int pid, EspacioProceso e) { return marcosNecesarios(pid, e) <= mapa.total(); }

    /** Páginas que se cargan al (re)admitir: las iniciales o, si estaba suspendido, las que tenía. */
    private int paginasAlCargar(int pid, EspacioProceso e) {
        Crecimiento c = guardado(pid, e);
        if (c == null) return paginasIniciales(e);
        return e.paginas(EspacioProceso.Segmento.TEXTO) + e.paginas(EspacioProceso.Segmento.DATOS) + c.paginasHeap() + c.paginasPila();
    }

    /** El crecimiento guardado al suspender, solo si corresponde al mismo espacio de proceso. */
    private Crecimiento guardado(int pid, EspacioProceso e) {
        Crecimiento c = suspendidos.get(pid);
        return c != null && c.espacio() == e ? c : null;
    }

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
        if (!cabe(pid, e)) throw new MemoriaInsuficienteException(marcosNecesarios(pid, e), mapa.libres());

        Crecimiento c = guardado(pid, e);                             // [P5] al reanudar recupera lo que había crecido
        Residente r = new Residente(e, new TablaPaginas());
        if (bajoDemanda()) {                                          // [M7] se reserva la cuota; las páginas llegan con los fallos
            int cuota = cuota(pid);
            r.politica = new PoliticaFisica(reemplazo, cuota);
            r.propios = new int[cuota];
            int i = 0;
            for (Tramo tr : asignarMarcos(cuota)) {
                r.tramos.add(tr);
                for (int k = 0; k < tr.marcos(); k++) r.propios[i++] = tr.marcoInicial() + k;
            }
        }
        ubicar(r, e.base(EspacioProceso.Segmento.TEXTO) / e.bloque(), e.paginas(EspacioProceso.Segmento.TEXTO));
        ubicar(r, e.base(EspacioProceso.Segmento.DATOS) / e.bloque(), e.paginas(EspacioProceso.Segmento.DATOS));
        if (c == null) {
            ubicar(r, paginaTopePila(e) - 1, 1);                      // la pila empieza con su página más alta
            r.paginasPila = 1;
        } else {
            if (c.paginasHeap() > 0) ubicar(r, e.base(EspacioProceso.Segmento.HEAP) / e.bloque(), c.paginasHeap());
            ubicar(r, paginaTopePila(e) - c.paginasPila(), c.paginasPila());
            r.paginasHeap = c.paginasHeap();
            r.paginasPila = c.paginasPila();
            r.heapUsado = c.heapUsado();
            r.pilaUsada = c.pilaUsada();
        }
        mmu.registrar(pid, e, r.tabla);
        residentes.put(pid, r);
        suspendidos.remove(pid);
        return r.tabla;
    }

    /**
     * [M2] {@code malloc}: el heap crece hacia arriba. Si se pasa de su tamaño máximo o no hay
     * marcos libres, devuelve false (malloc devuelve NULL) y no cambia nada.
     */
    public boolean crecerHeap(int pid, long bytes) {
        if (bytes <= 0) throw new ParametroInvalidoException("malloc necesita más de 0 bytes (" + bytes + ")");
        Residente r = exigir(pid);
        if (bytes > r.espacio.tamano(EspacioProceso.Segmento.HEAP) - r.heapUsado) return false;   // sin sumar: no desborda
        long nuevo = r.heapUsado + bytes;
        int necesarias = Math.toIntExact(MapaBits.bloques(nuevo, r.espacio.bloque())) - r.paginasHeap;
        if (!bajoDemanda() && necesarias > mapa.libres()) return false;
        if (necesarias > 0) {
            ubicar(r, r.espacio.base(EspacioProceso.Segmento.HEAP) / r.espacio.bloque() + r.paginasHeap, necesarias);
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
        EspacioProceso e = r.espacio;
        if (bytes > e.tamano(EspacioProceso.Segmento.PILA) - r.pilaUsada)                       // sin sumar: no desborda
            throw new AccesoIlegalException(pid, e.base(EspacioProceso.Segmento.PILA) - 1);
        long nuevo = r.pilaUsada + bytes;
        int necesarias = Math.toIntExact(MapaBits.bloques(nuevo, e.bloque())) - r.paginasPila;
        if (!bajoDemanda() && necesarias > mapa.libres()) throw new MemoriaInsuficienteException(necesarias, mapa.libres());
        if (necesarias > 0) {
            ubicar(r, paginaTopePila(e) - r.paginasPila - necesarias, necesarias);
            r.paginasPila += necesarias;
        }
        r.pilaUsada = nuevo;
    }

    /**
     * [M1][M4] Acceso del proceso a memoria. Bajo demanda, cada fallo de página (0x0E) se atiende
     * cargando la página en un marco de su cuota y el acceso se reintenta; el reemplazo registra
     * cada referencia (LRU la necesita). Con carga total, un fallo de página no se atiende aquí.
     */
    public Mmu.Traduccion acceder(int pid, long direccion, int bytes, boolean escritura) {
        Residente r = exigir(pid);
        if (r.politica != null && bytes > 0 && direccion >= 0 && direccion <= Long.MAX_VALUE - bytes) {
            long paginas = mmu.pagina(direccion + bytes - 1) - mmu.pagina(direccion) + 1;
            if (paginas > r.politica.cuota()) throw new MemoriaInsuficienteException((int) Math.min(paginas, Integer.MAX_VALUE), r.politica.cuota());
        }
        while (true) {
            try {
                Mmu.Traduccion t = mmu.traducir(pid, direccion, bytes, escritura);
                if (r.politica != null)
                    for (long p = t.pagina(); p <= mmu.pagina(direccion + bytes - 1); p++) r.politica.referenciar(p);
                return t;
            } catch (FalloPaginaException f) {
                if (r.politica == null) throw f;
                atenderFallo(r, f.pagina);
            }
        }
    }

    /** [M1][M7] La página entra en un marco de la cuota; si estaba llena, la víctima local pasa a bit i. */
    private void atenderFallo(Residente r, long pagina) {
        PoliticaFisica.Referencia ref = r.politica.referenciar(pagina);
        if (ref.victima() >= 0) r.tabla.invalidar(ref.victima());
        r.tabla.cargar(pagina, r.propios[ref.marco()]);
        fallosPagina++;
    }

    /** Carga total: pide marcos para las páginas. Bajo demanda: solo las registra en el espacio virtual (bit i). */
    private void ubicar(Residente r, long pagina, int paginas) {
        if (r.politica == null) { cargar(r, pagina, paginas); return; }
        for (int i = 0; i < paginas; i++) r.tabla.registrar(pagina + i);
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

    /** El proceso terminó (o su admisión se deshizo): sus marcos vuelven al mapa de bits y se olvida su crecimiento. */
    public void liberar(int pid) {
        Residente r = residentes.remove(pid);
        if (r == null) throw new EventoInvalidoException("P" + pid + " no tiene memoria asignada");
        for (Tramo tr : r.tramos) mapa.liberar(tr.marcoInicial(), tr.marcos());
        mmu.olvidar(pid);
        suspendidos.remove(pid);
    }

    /**
     * [P5][M2] Swap-out: libera los marcos como {@link #liberar(int)}, pero guarda cuánto habían
     * crecido el heap y la pila para restaurarlos al reanudar con {@link #asignar(int, EspacioProceso)}.
     */
    public void suspender(int pid) {
        Residente r = exigir(pid);
        liberar(pid);
        suspendidos.put(pid, new Crecimiento(r.espacio, r.paginasHeap, r.paginasPila, r.heapUsado, r.pilaUsada));
    }

    public boolean residente(int pid) { return residentes.containsKey(pid); }
    public List<Tramo> tramos(int pid) { Residente r = residentes.get(pid); return r == null ? List.of() : List.copyOf(r.tramos); }
    public long heapUsado(int pid) { return exigir(pid).heapUsado; }
    public long pilaUsada(int pid) { return exigir(pid).pilaUsada; }
    public int paginasHeap(int pid) { return exigir(pid).paginasHeap; }
    public int paginasPila(int pid) { return exigir(pid).paginasPila; }

    /**
     * [I5] Fragmentación interna de los procesos residentes, según lo que ocupa cada uno ahora:
     * texto y datos por su tamaño; heap y pila por sus páginas cargadas menos los bytes usados.
     */
    public long desperdicioInterno() {
        long total = 0;
        for (Residente r : residentes.values()) {
            EspacioProceso e = r.espacio;
            total += e.desperdicio(EspacioProceso.Segmento.TEXTO) + e.desperdicio(EspacioProceso.Segmento.DATOS);
            total += (long) r.paginasHeap * e.bloque() - r.heapUsado;
            total += (long) r.paginasPila * e.bloque() - r.pilaUsada;
        }
        return total;
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
    public Reemplazo reemplazo() { return reemplazo; }
    /** [M1] Fallos de página atendidos en toda la simulación. */
    public int fallosPagina() { return fallosPagina; }
    /** [M7] Marcos de la cuota del proceso con la página que tiene cada uno (null: libre). */
    public List<Long> marcosDe(int pid) {
        Residente r = exigir(pid);
        if (r.politica == null) throw new EventoInvalidoException("Sin paginación bajo demanda no hay cuota por proceso");
        return r.politica.marcos();
    }
}
