package pe.uni.so.procesos;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import pe.uni.so.aleatorio.Ecuaciones;
import pe.uni.so.aleatorio.Lcg;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.SolicitudES.Operacion;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.EspacioProceso;

/**
 * [P14][P17][P18] Fábrica de procesos (patrón Factory). Los procesos aleatorios, los manuales y
 * los del lote oficial pasan por el mismo {@link #crear}: PID consecutivo, espacio con la página de
 * la máquina y plan de E/S según las ecuaciones 2 a 6. Todo sale de un solo LCG con semilla, así
 * la misma semilla da la misma carga.
 */
public class CreadorProcesos {

    /** Ecuación 8: rangos de la carga aleatoria. El burst mínimo 11 garantiza b ≥ N + 1 (ecuación 5). */
    public static final int BURST_MINIMO = 11, BURST_MAXIMO_ALEATORIO = 50, BURST_MAXIMO = 0xFFFF;
    public static final long TEXTO_MINIMO = 128 * 1024L, TEXTO_MAXIMO = 2047 * 1024L;
    public static final int PRIORIDAD_MINIMA = 1, PRIORIDAD_MAXIMA = 5;
    public static final double LLEGADA_MEDIA = 3;                       // μ de las llegadas de Poisson
    /** Tamaño máximo de un proceso aleatorio (texto + 40 % de datos + 30 % de heap): normaliza el peso. */
    public static final long TAMANO_MAXIMO = Math.round(TEXTO_MAXIMO * 1.7);
    /** Valores de un proceso manual cuando solo se indican ráfaga y tamaño (P18). */
    public static final double DATOS_MANUAL = 0.30, HEAP_MANUAL = 0.20;
    public static final int PRIORIDAD_MANUAL = 3;
    /** Semilla del lote oficial de 20 procesos (P17). */
    public static final long SEMILLA_LOTE_OFICIAL = 2026;

    /** Ecuación 6: ruleta de dispositivos (teclado, disco, impresora, pantalla, red). */
    private static final Dispositivo[] RULETA = {Dispositivo.TECLADO, Dispositivo.DISCO, Dispositivo.IMPRESORA, Dispositivo.PANTALLA, Dispositivo.RED};
    private static final double[] ACUMULADA = {0.20, 0.50, 0.65, 0.85, 1.00};

    private final Lcg rng;
    private final long bloque;
    private int siguientePid;

    public CreadorProcesos(long semilla, long bloque) { this(semilla, bloque, 1); }

    public CreadorProcesos(long semilla, long bloque, int primerPid) {
        if (semilla < 0) throw new ParametroInvalidoException("La semilla debe ser ≥ 0 (" + semilla + ")");
        if (primerPid < 1) throw new ParametroInvalidoException("El primer PID debe ser ≥ 1 (" + primerPid + ")");
        this.rng = new Lcg(semilla);
        this.bloque = bloque;
        this.siguientePid = primerPid;
    }

    /**
     * [P14] El único punto de creación: valida, asigna PID, espacio y plan de E/S. Lo usan la
     * carga aleatoria, el ingreso manual y el lote oficial.
     */
    public Pcb crear(int llegada, int burst, long texto, double fraccionDatos, double fraccionHeap, int prioridad) {
        if (llegada < 0) throw new ParametroInvalidoException("La llegada debe ser ≥ 0 (" + llegada + ")");
        if (burst < BURST_MINIMO || burst > BURST_MAXIMO)
            throw new ParametroInvalidoException("La ráfaga debe estar entre " + BURST_MINIMO + " y " + BURST_MAXIMO + " instrucciones (" + burst + ")");
        if (prioridad < PRIORIDAD_MINIMA || prioridad > PRIORIDAD_MAXIMA)
            throw new ParametroInvalidoException("La prioridad debe estar entre " + PRIORIDAD_MINIMA + " y " + PRIORIDAD_MAXIMA + " (" + prioridad + ")");
        EspacioProceso e = new EspacioProceso(texto, fraccionDatos, fraccionHeap, bloque);   // valida tamaños y porcentajes
        Pcb p = new Pcb(siguientePid, llegada, burst, prioridad);
        p.asignarEspacio(e);
        long tamano = texto + e.tamano(EspacioProceso.Segmento.DATOS) + e.tamano(EspacioProceso.Segmento.HEAP);
        p.asignarPlanES(planES(burst, tamano));
        siguientePid++;
        return p;
    }

    /** [P18] Ingreso manual: el usuario indica llegada, ráfaga y tamaño del ejecutable. */
    public Pcb manual(int llegada, int burst, long texto) {
        return crear(llegada, burst, texto, DATOS_MANUAL, HEAP_MANUAL, PRIORIDAD_MANUAL);
    }

    /**
     * [P14] «Generar N»: procesos con la ecuación 8, llegando como un proceso de Poisson de media 3
     * desde {@code desde}. Los intervalos se acumulan en tiempo continuo y solo la llegada absoluta
     * se redondea hacia abajo, así redondear no alarga la media.
     */
    public List<Pcb> aleatorios(int n, int desde) {
        if (n < 1) throw new ParametroInvalidoException("Hay que generar al menos 1 proceso (" + n + ")");
        if (desde < 0) throw new ParametroInvalidoException("La primera llegada debe ser ≥ 0 (" + desde + ")");
        List<Pcb> lote = new ArrayList<>(n);
        double tiempo = desde;                         // tiempo continuo: se acumula sin redondear
        for (int i = 0; i < n; i++) {
            if (i > 0) tiempo += -LLEGADA_MEDIA * Math.log(1 - rng.uniforme());   // intervalo exponencial de media 3
            int llegada = (int) Math.floor(tiempo);   // solo la llegada absoluta se lleva a ticks: la media se mantiene en 3
            int burst = BURST_MINIMO + (int) Math.floor((BURST_MAXIMO_ALEATORIO - BURST_MINIMO + 1) * rng.uniforme());
            long texto = TEXTO_MINIMO + (long) Math.floor((TEXTO_MAXIMO - TEXTO_MINIMO + 1024) / 1024.0 * rng.uniforme()) * 1024;
            double datos = 0.20 + 0.20 * rng.uniforme();
            double heap = 0.10 + 0.20 * rng.uniforme();
            int prioridad = PRIORIDAD_MINIMA + (int) Math.floor(PRIORIDAD_MAXIMA * rng.uniforme());
            lote.add(crear(llegada, burst, texto, datos, heap, prioridad));
        }
        return lote;
    }

    /**
     * [P17] Lote oficial: 20 procesos fijos que entran en secuencia (la tabla del informe). Sus
     * pedidos de E/S salen de la semilla 2026, así el lote es idéntico en cada corrida.
     * Columnas: llegada, ráfaga, ejecutable (KiB), % datos, % heap, prioridad.
     */
    public static List<Pcb> loteOficial(long bloque) {
        CreadorProcesos c = new CreadorProcesos(SEMILLA_LOTE_OFICIAL, bloque);
        List<Pcb> lote = new ArrayList<>(LOTE_OFICIAL.length);
        for (double[] f : LOTE_OFICIAL)
            lote.add(c.crear((int) f[0], (int) f[1], (long) f[2] * 1024, f[3], f[4], (int) f[5]));
        return lote;
    }

    private static final double[][] LOTE_OFICIAL = {
        {0, 11, 213, 0.21, 0.23, 4}, {2, 35, 1598, 0.23, 0.29, 1}, {3, 40, 641, 0.32, 0.26, 5},
        {4, 23, 631, 0.33, 0.27, 2}, {7, 45, 847, 0.24, 0.24, 2}, {12, 44, 703, 0.21, 0.29, 1},
        {13, 12, 1178, 0.25, 0.18, 2}, {15, 23, 942, 0.33, 0.17, 2}, {18, 41, 1853, 0.36, 0.13, 1},
        {19, 16, 1271, 0.37, 0.15, 5}, {22, 50, 1593, 0.25, 0.14, 1}, {29, 33, 914, 0.26, 0.14, 4},
        {31, 16, 600, 0.33, 0.26, 3}, {40, 49, 920, 0.37, 0.21, 2}, {44, 38, 1133, 0.39, 0.12, 2},
        {45, 23, 375, 0.30, 0.15, 4}, {48, 20, 1319, 0.32, 0.20, 3}, {52, 39, 1376, 0.36, 0.19, 5},
        {55, 35, 1391, 0.26, 0.23, 3}, {56, 49, 1872, 0.38, 0.26, 2},
    };

    /**
     * [P11][P12][E10][E11] Plan de E/S de un proceso: N pedidos (ecuación 3) en instantes distintos
     * y ordenados dentro de la ráfaga (ecuación 5), cada uno a un dispositivo de la ruleta
     * (ecuación 6) y con su duración (ecuación 4).
     */
    private List<PedidoES> planES(int burst, long tamano) {
        double w = Math.min(1.0, Ecuaciones.peso(Math.min(burst, BURST_MAXIMO_ALEATORIO), BURST_MAXIMO_ALEATORIO,
                Math.min(tamano, TAMANO_MAXIMO), TAMANO_MAXIMO));
        int n = Ecuaciones.pedidosES(w, rng);                                  // 5 a 10 (b ≥ 11 ≥ N + 1)
        TreeSet<Integer> instantes = new TreeSet<>();
        while (instantes.size() < n) instantes.add(1 + (int) Math.floor(rng.uniforme() * (burst - 1)));
        List<PedidoES> plan = new ArrayList<>(n);
        for (int instante : instantes) {
            Dispositivo d = ruleta(rng.uniforme());
            plan.add(new PedidoES(instante, d, operacion(d), Ecuaciones.duracionES(w, rng)));
        }
        return plan;
    }

    /** Ecuación 6: el primer dispositivo cuya probabilidad acumulada supera U. */
    static Dispositivo ruleta(double u) {
        for (int i = 0; i < RULETA.length; i++) if (u < ACUMULADA[i]) return RULETA[i];
        return RULETA[RULETA.length - 1];
    }

    /** El teclado se lee; la pantalla y la impresora se escriben; disco y red, al azar. */
    private Operacion operacion(Dispositivo d) {
        return switch (d) {
            case TECLADO -> Operacion.LEER;
            case PANTALLA, IMPRESORA -> Operacion.ESCRIBIR;
            case DISCO, RED -> rng.uniforme() < 0.5 ? Operacion.LEER : Operacion.ESCRIBIR;
        };
    }
}
