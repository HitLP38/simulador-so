package pe.uni.so.comparador;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Configuracion.Estrategia;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.excepciones.SimuladorException;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.motor.Kernel;
import pe.uni.so.motor.Momento;
import pe.uni.so.planificacion.Metricas;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;
import pe.uni.so.sesion.PlanCarga;
import pe.uni.so.sesion.SesionSimulacion;

/**
 * [P17][M8][G1] Comparador de configuraciones: corre la <b>misma carga</b> (lote oficial o N
 * aleatorios con semilla) con varias políticas de planificación y con varias estrategias de
 * asignación de memoria, y mide cada corrida. Es la base de las tablas del informe y de la prueba
 * de estrés de 1000 procesos.
 *
 * <p>Dos series: en la de planificación cambia solo la política (la memoria queda como en la
 * máquina); en la de memoria cambia solo la estrategia (la política queda como en la máquina).
 * Cada corrida usa procesos nuevos, sin fotos ni vista (modo rápido del Kernel). No depende de
 * JavaFX.
 */
public final class Comparador {

    /** Una configuración a correr y la serie a la que pertenece. */
    public record Escenario(Resultado.Serie serie, Configuracion configuracion) {
        public Escenario {
            if (serie == null || configuracion == null) throw new ParametroInvalidoException("Falta la serie o la configuración del escenario");
        }
    }

    /** Tope de seguridad de cada corrida (el mismo de la sesión). */
    public static final int LIMITE_TICKS = SesionSimulacion.LIMITE_TICKS;

    private Comparador() {}

    /**
     * Escenarios de la comparación sobre la máquina {@code base}: una corrida por cada política
     * marcada y una por cada estrategia marcada, en el orden de las opciones.
     */
    public static List<Escenario> escenarios(Configuracion base, Collection<Politica> politicas, Collection<Estrategia> estrategias) {
        if (base == null) throw new ParametroInvalidoException("Falta la máquina base de la comparación");
        EnumSet<Politica> ps = politicas == null || politicas.isEmpty() ? EnumSet.noneOf(Politica.class) : EnumSet.copyOf(politicas);
        EnumSet<Estrategia> es = estrategias == null || estrategias.isEmpty() ? EnumSet.noneOf(Estrategia.class) : EnumSet.copyOf(estrategias);
        if (ps.isEmpty() && es.isEmpty()) throw new ParametroInvalidoException("Marca al menos una política o una estrategia para comparar");
        List<Escenario> lista = new ArrayList<>();
        for (Politica p : ps) lista.add(new Escenario(Resultado.Serie.PLANIFICACION, conPolitica(base, p)));
        for (Estrategia e : es) lista.add(new Escenario(Resultado.Serie.MEMORIA, conEstrategia(base, e)));
        return lista;
    }

    static Configuracion conPolitica(Configuracion c, Politica p) {
        return new Configuracion(c.ram(), c.pagina(), c.cpu(), p, c.quantum(), c.costoDespacho(), c.grado(), c.estrategia(),
                c.carga(), c.reemplazo(), c.dispositivos(), c.semilla());
    }

    static Configuracion conEstrategia(Configuracion c, Estrategia e) {
        return new Configuracion(c.ram(), c.pagina(), c.cpu(), c.politica(), c.quantum(), c.costoDespacho(), c.grado(), e,
                c.carga(), c.reemplazo(), c.dispositivos(), c.semilla());
    }

    /**
     * Corre los escenarios en orden.
     *
     * @param alTerminar recibe cada resultado apenas termina su corrida (para mostrar el avance); puede ser null
     * @param cancelado  se consulta antes de cada corrida; si da true, se devuelve lo hecho hasta ahí; puede ser null
     */
    public static List<Resultado> correr(List<Escenario> escenarios, PlanCarga carga, Consumer<Resultado> alTerminar,
                                         BooleanSupplier cancelado) {
        if (escenarios == null || escenarios.isEmpty()) throw new ParametroInvalidoException("No hay configuraciones para comparar");
        if (carga == null) throw new ParametroInvalidoException("Falta la carga de la comparación");
        List<Resultado> resultados = new ArrayList<>();
        for (Escenario e : escenarios) {
            if (cancelado != null && cancelado.getAsBoolean()) break;
            Resultado r = correr(e, carga);
            resultados.add(r);
            if (alTerminar != null) alTerminar.accept(r);
        }
        return resultados;
    }

    /** Una corrida completa del escenario con procesos nuevos de la carga. */
    public static Resultado correr(Escenario e, PlanCarga carga) {
        Configuracion c = e.configuracion();
        String politica = c.politica() == Politica.ROUND_ROBIN ? c.politica().etiqueta + " (q = " + c.quantum() + ")" : c.politica().etiqueta;
        String estrategia = c.estrategia().etiqueta;
        long inicio = System.nanoTime();
        List<Pcb> procesos = carga.procesos(c.pagina().bytes);
        Kernel k;
        try {
            k = c.crearKernel(procesos);                      // valida que la carga quepa en la máquina
        } catch (SimuladorException ex) {
            return vacio(e.serie(), politica, estrategia, procesos.size(), ms(inicio), ex.getMessage());
        }
        k.limitarHistorial(1);
        k.apagarFotos();
        String nota = "";
        try {
            if (!k.ejecutarHasta(LIMITE_TICKS)) nota = "Se detuvo en el tope de " + LIMITE_TICKS + " ticks";
        } catch (SimuladorException ex) {
            nota = "Atascada: " + ex.getMessage();
        }
        return medir(e.serie(), politica, estrategia, k, ms(inicio), nota);
    }

    private static long ms(long inicio) { return (System.nanoTime() - inicio) / 1_000_000; }

    private static Resultado vacio(Resultado.Serie serie, String politica, String estrategia, int procesos, long ms, String nota) {
        return new Resultado(serie, politica, estrategia, procesos, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ms, nota);
    }

    private static Resultado medir(Resultado.Serie serie, String politica, String estrategia, Kernel k, long ms, String nota) {
        List<Pcb> todos = k.procesos();
        List<Pcb> terminados = todos.stream().filter(p -> p.estado() == Estado.TERMINADO).toList();
        int t = k.t();
        long ocupados = k.gantt().isEmpty() ? 0 : java.util.Arrays.stream(k.gantt().split(" ")).filter(x -> !x.equals("-")).count();
        double uso = t == 0 ? 0 : 100.0 * ocupados / t;
        double throughput = t == 0 ? 0 : 100.0 * Metricas.throughput(terminados, t);

        // [M8] Memoria: comparaciones de búsqueda, fragmentación por tick y espera en Nuevo. Los fallos de página no
        // se comparan: en la corrida automática nadie accede a memoria (solo los accesos manuales los provocan).
        GestorMemoria gm = k.memoria().orElse(null);
        long comparaciones = gm == null ? 0 : gm.comparaciones();
        List<GestorMemoria.Muestra> muestras = k.historialMemoria();
        double externa = muestras.stream().mapToInt(GestorMemoria.Muestra::fragmentacionExterna).average().orElse(0);
        int externaMax = muestras.stream().mapToInt(GestorMemoria.Muestra::fragmentacionExterna).max().orElse(0);
        double interna = muestras.stream().mapToLong(GestorMemoria.Muestra::desperdicioInterno).average().orElse(0) / 1024.0;
        // Solo las esperas por falta de memoria: las del grado de multiprogramación y las de quien espera detrás
        // de otro (orden de llegada) no dependen de la estrategia y quedan fuera.
        Map<Integer, Integer> porMemoria = k.esperasPorMemoria();
        int esperaron = porMemoria.size();
        long ticksEnNuevo = porMemoria.values().stream().mapToLong(Integer::longValue).sum();
        double esperaMemoria = esperaron == 0 ? 0 : (double) ticksEnNuevo / esperaron;

        return new Resultado(serie, politica, estrategia, todos.size(), terminados.size(), k.abortados(), t,
                Metricas.esperaMedia(terminados), Metricas.respuestaMedia(terminados), Metricas.retornoMedio(terminados),
                uso, throughput, k.despachador().despachos(), k.desalojos(), k.finesDeQuantum(), comparaciones, externa, externaMax,
                interna, esperaron, esperaMemoria, ms, nota);
    }
}
