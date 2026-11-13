package pe.uni.so.sesion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.motor.Kernel;
import pe.uni.so.procesos.CreadorProcesos;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

/**
 * [G5][P16] Una simulación abierta en el espacio de trabajo de una máquina. Separa dos
 * instantes: el del Kernel (hasta dónde se simuló) y el de la foto que se está mirando. Así la
 * línea de tiempo puede retroceder sobre el historial de {@link Instantanea} sin deshacer la
 * simulación, y al avanzar desde el pasado primero se recorren las fotos ya tomadas.
 *
 * <p>Si se pide un instante anterior a la foto más antigua del historial, la sesión vuelve a
 * simular desde t = 0 con la misma máquina y la misma carga: la simulación es determinista y en
 * esta sesión no hay acciones manuales, así que se obtiene exactamente la misma foto.
 *
 * <p>No depende de JavaFX: la interfaz solo llama a estos métodos y se suscribe con
 * {@link #alCambiar(Consumer)}.
 */
public class SesionSimulacion {

    /** Tope de seguridad: ninguna corrida avanza más allá de este instante. */
    public static final int LIMITE_TICKS = 200_000;
    /** Ticks seguidos sin progreso posible para declarar el atasco (deja reintentar la admisión). */
    static final int TICKS_PARA_ATASCO = 2;

    private final Maquina maquina;
    private final Supplier<List<Pcb>> carga;
    private final List<Consumer<Instantanea>> oyentes = new ArrayList<>();
    private Kernel kernel;
    private Instantanea vista;
    private boolean atascada;
    private int ticksSinProgreso;                               // ticks seguidos sin progreso posible
    private int maximoAlcanzado;                                // el mayor instante simulado (sobrevive a re-simular)
    /** Fines de quantum (interrupción del TIMER) acumulados hasta cada instante: índice = t. */
    private final List<Integer> finesQuantum = new ArrayList<>(List.of(0));
    private int capacidadHistorial = Kernel.CAPACIDAD_HISTORIAL;

    /** Sesión con el lote oficial de 20 procesos, en páginas del tamaño de la máquina. */
    public SesionSimulacion(Maquina maquina) {
        this(maquina, () -> CreadorProcesos.loteOficial(maquina.configuracion().pagina().bytes));
    }

    /**
     * @param carga crea una carga nueva cada vez que se llama (los PCB cambian al simular, así que
     *              reiniciar necesita procesos nuevos)
     */
    public SesionSimulacion(Maquina maquina, Supplier<List<Pcb>> carga) {
        if (maquina == null) throw new ParametroInvalidoException("Falta la máquina de la sesión");
        if (carga == null) throw new ParametroInvalidoException("Falta la carga de la sesión");
        this.maquina = maquina;
        this.carga = carga;
        kernel = crearKernel();
        vista = kernel.instantanea();
    }

    private Kernel crearKernel() {
        finesQuantum.clear();                                       // el acumulado se rehace al simular de nuevo
        finesQuantum.add(0);
        Kernel k = maquina.configuracion().crearKernel(carga.get());
        k.limitarHistorial(capacidadHistorial);
        return k;
    }

    /**
     * Cuántas fotos conserva la línea de tiempo (por defecto {@link Kernel#CAPACIDAD_HISTORIAL}).
     * Se mantiene también al reiniciar o volver a simular.
     */
    public void limitarHistorial(int capacidad) {
        kernel.limitarHistorial(capacidad);                 // valida la capacidad
        capacidadHistorial = capacidad;
        if (kernel.instantanea(vista.t()).isEmpty()) {             // la foto mirada salió del historial:
            vista = kernel.instantanea();                           // se muestra la última y se avisa
            avisar();
        }
    }

    // ---------------------------------------------------------------- consultas

    public Maquina maquina() { return maquina; }

    /** Foto que se está mirando. */
    public Instantanea vista() { return vista; }

    /** Hasta qué instante se simuló. */
    public int tActual() { return kernel.t(); }

    /** ¿La foto mirada es la última simulada? */
    public boolean enVivo() { return vista.t() == kernel.t(); }

    /** ¿Terminaron todos los procesos? */
    public boolean terminada() { return kernel.terminado(); }

    /**
     * ¿La simulación ya no puede progresar? CPU libre, nadie listo, dispositivos e interrupciones
     * sin trabajo y ninguna llegada futura, pero quedan procesos sin terminar (por ejemplo, en
     * Nuevo sin memoria). También se marca al llegar a {@link #LIMITE_TICKS}.
     */
    public boolean atascada() { return atascada; }

    /**
     * El mayor instante simulado en esta sesión. Volver a simular un tick antiguo no lo reduce: la
     * línea de tiempo sigue ofreciendo hasta aquí. Reiniciar lo vuelve a 0.
     */
    public int maximoAlcanzado() { return maximoAlcanzado; }

    /**
     * Fines de quantum acumulados hasta el instante t: cada uno es una interrupción del TIMER que
     * devuelve el proceso a listos (momento b). El motor marca cada uno con la insignia
     * {@code FIN_QUANTUM} solo en su tick; la sesión los suma al simular.
     */
    public int finesDeQuantum(int t) {
        if (t < 0) throw new ParametroInvalidoException("El instante no puede ser negativo (" + t + ")");
        return finesQuantum.get(Math.min(t, finesQuantum.size() - 1));
    }

    /** ¿Se puede avanzar desde la foto mirada? */
    public boolean puedeAvanzar() { return !enVivo() || (!terminada() && !atascada); }

    /** Instante de la foto más antigua del historial (antes de ese instante hay que volver a simular). */
    public int primerTickEnHistorial() { return kernel.historial().get(0).t(); }

    // ---------------------------------------------------------------- navegación

    /**
     * Avanza {@code n} instantes desde la foto mirada: primero recorre las fotos ya tomadas y,
     * al llegar a la última, simula ticks nuevos. Se detiene si la simulación terminó o se atascó.
     *
     * @return cuántos instantes avanzó realmente
     */
    public int avanzar(int n) {
        if (n < 1) throw new ParametroInvalidoException("Se avanza al menos 1 tick (" + n + ")");
        int hechos = 0;
        while (hechos < n) {
            if (!enVivo()) {
                vista = kernel.instantanea(vista.t() + 1).orElseGet(kernel::instantanea);
            } else {
                if (terminada() || atascada) break;
                simularUnTick();
            }
            hechos++;
        }
        if (hechos > 0) avisar();
        return hechos;
    }

    /** Retrocede {@code n} instantes (sin pasar de t = 0). */
    public void retroceder(int n) {
        if (n < 1) throw new ParametroInvalidoException("Se retrocede al menos 1 tick (" + n + ")");
        irA(Math.max(0, vista.t() - n));
    }

    /**
     * Muestra la foto del instante {@code t}: la toma del historial, simula hasta ahí si es
     * futuro, o vuelve a simular desde 0 si ya salió del historial.
     */
    public void irA(int t) {
        if (t < 0) throw new ParametroInvalidoException("El instante no puede ser negativo (" + t + ")");
        if (t > kernel.t()) {
            vista = kernel.instantanea();
            while (kernel.t() < t && !terminada() && !atascada) simularUnTick();
        } else {
            var guardada = kernel.instantanea(t);
            if (guardada.isPresent()) vista = guardada.get();
            else {                                           // más antigua que el historial: re-simular
                kernel = crearKernel();
                atascada = false;
                ticksSinProgreso = 0;
                while (kernel.t() < t && !atascada) simularUnTick();
                vista = kernel.instantanea();
            }
        }
        avisar();
    }

    /** Simula hasta que todos terminen (o la simulación se atasque) y muestra la última foto. */
    public void hastaElFinal() {
        vista = kernel.instantanea();
        while (!terminada() && !atascada) simularUnTick();
        avisar();
    }

    /** Vuelve a t = 0 con la misma máquina y una carga nueva igual a la original. */
    public void reiniciar() {
        kernel = crearKernel();
        atascada = false;
        ticksSinProgreso = 0;
        maximoAlcanzado = 0;
        vista = kernel.instantanea();
        avisar();
    }

    private void simularUnTick() {
        if (kernel.t() >= LIMITE_TICKS) {
            atascada = true;
            return;
        }
        kernel.paso();
        vista = kernel.instantanea();
        maximoAlcanzado = Math.max(maximoAlcanzado, kernel.t());
        long fines = vista.procesos().stream().filter(p -> p.tiene(Instantanea.Insignia.FIN_QUANTUM)).count();
        finesQuantum.add(finesQuantum.get(finesQuantum.size() - 1) + (int) fines);
        // Un tick sin progreso no basta: al terminar un proceso se libera memoria o grado después
        // del intento de admisión, y el que espera en Nuevo entra recién en el tick siguiente.
        ticksSinProgreso = sinProgresoPosible(vista) ? ticksSinProgreso + 1 : 0;
        atascada = ticksSinProgreso >= TICKS_PARA_ATASCO;
    }

    /** CPU libre, nadie listo, sin E/S ni interrupciones pendientes y sin llegadas futuras. */
    static boolean sinProgresoPosible(Instantanea f) {
        if (f.terminada()) return false;
        if (!f.cpu().libre() || !f.listos().isEmpty() || !f.pic().pendientes().isEmpty()) return false;
        boolean dispositivosOcupados = f.dispositivos().stream().anyMatch(d -> !d.libre() || !d.cola().isEmpty());
        boolean llegadasFuturas = f.procesos().stream().anyMatch(p -> p.estado() == Estado.NUEVO && p.llegada() >= f.t());
        boolean bloqueados = f.procesos().stream().anyMatch(p -> p.estado() == Estado.BLOQUEADO);
        return !dispositivosOcupados && !llegadasFuturas && !bloqueados && f.procesos().stream()
                .noneMatch(p -> p.estado() == Estado.LISTO);
    }

    // ---------------------------------------------------------------- oyentes

    /** La interfaz se suscribe aquí; recibe la foto mirada cada vez que cambia. */
    public void alCambiar(Consumer<Instantanea> oyente) {
        if (oyente == null) throw new ParametroInvalidoException("Falta el oyente");
        oyentes.add(oyente);
    }

    private void avisar() { for (Consumer<Instantanea> o : List.copyOf(oyentes)) o.accept(vista); }
}
