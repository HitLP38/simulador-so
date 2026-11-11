package pe.uni.so.configuracion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.VelocidadDispositivo;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.EstrategiaAsignacion;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.memoria.Reemplazo;
import pe.uni.so.motor.Kernel;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.PlanificadorLargoPlazo;
import pe.uni.so.planificacion.PoliticaPlanificacion;
import pe.uni.so.planificacion.Prioridad;
import pe.uni.so.planificacion.RoundRobin;
import pe.uni.so.planificacion.Sjf;
import pe.uni.so.planificacion.Srtf;
import pe.uni.so.procesos.Pcb;

/**
 * [G5] Características de la máquina simulada. Cada parámetro solo toma opciones válidas
 * (la RAM y la página son potencias de 2; el resto tiene un rango), así no existe una máquina
 * imposible. El enunciado modela un solo procesador: lo configurable de la CPU es su velocidad.
 *
 * @param ram           memoria principal
 * @param pagina        tamaño de página = tamaño de marco (B)
 * @param cpu           velocidad del procesador
 * @param politica      política de planificación de corto plazo
 * @param quantum       quantum de Round Robin, en ticks (solo se usa con Round Robin)
 * @param costoDespacho costo de cada cambio de contexto, en ms
 * @param grado         grado de multiprogramación (procesos residentes a la vez)
 * @param estrategia    estrategia de asignación de huecos
 * @param carga         carga total o paginación bajo demanda
 * @param reemplazo     algoritmo de reemplazo (solo se usa con paginación bajo demanda)
 * @param dispositivos  velocidad de cada uno de los 5 dispositivos
 * @param semilla       semilla del generador aleatorio
 */
public record Configuracion(Ram ram, Pagina pagina, Cpu cpu,
                            Politica politica, int quantum, double costoDespacho, int grado,
                            Estrategia estrategia, Carga carga, Reemplazo reemplazo,
                            Map<Dispositivo, VelocidadDispositivo> dispositivos, long semilla) {

    public static final int QUANTUM_MINIMO = 1, QUANTUM_MAXIMO = 20;
    public static final int GRADO_MINIMO = 1, GRADO_MAXIMO = 50;
    public static final double COSTO_MAXIMO = 1.0, PASO_COSTO = 0.1;

    /** Memoria principal: potencias de 2 hasta 4 GiB, el tope de las direcciones de 32 bits. */
    public enum Ram {
        MIB_128("128 MiB", 128L << 20), MIB_256("256 MiB", 256L << 20), MIB_512("512 MiB", 512L << 20),
        GIB_1("1 GiB", 1L << 30), GIB_2("2 GiB", 2L << 30), GIB_4("4 GiB", 4L << 30);

        public final String etiqueta;
        public final long bytes;

        Ram(String etiqueta, long bytes) { this.etiqueta = etiqueta; this.bytes = bytes; }

        @Override public String toString() { return etiqueta; }
    }

    /** Tamaño de página y de marco: potencias de 2 entre 32 KiB y 2 MiB (requisito I2). */
    public enum Pagina {
        KIB_32("32 KiB", 32L << 10), KIB_64("64 KiB", 64L << 10), KIB_128("128 KiB", 128L << 10),
        KIB_256("256 KiB", 256L << 10), KIB_512("512 KiB", 512L << 10),
        MIB_1("1 MiB", 1L << 20), MIB_2("2 MiB", 2L << 20);

        public final String etiqueta;
        public final long bytes;

        Pagina(String etiqueta, long bytes) { this.etiqueta = etiqueta; this.bytes = bytes; }

        @Override public String toString() { return etiqueta; }
    }

    /** Velocidad de la CPU: cuántas instrucciones ejecuta en un tick (1 GHz = 1). */
    public enum Cpu {
        MHZ_500("500 MHz", 0.5), GHZ_1("1 GHz", 1), GHZ_2("2 GHz", 2), GHZ_3("3 GHz", 3);

        public final String etiqueta;
        public final double instruccionesPorTick;

        Cpu(String etiqueta, double instruccionesPorTick) { this.etiqueta = etiqueta; this.instruccionesPorTick = instruccionesPorTick; }

        @Override public String toString() { return etiqueta; }
    }

    public enum Politica {
        FCFS("FCFS"), SJF("SJF"), SRTF("SRTF"), PRIORIDAD("Prioridad (no expropiativa)"),
        PRIORIDAD_EXPROPIATIVA("Prioridad (expropiativa)"), ROUND_ROBIN("Round Robin");

        public final String etiqueta;

        Politica(String etiqueta) { this.etiqueta = etiqueta; }

        /** El quantum solo se habilita con Round Robin. */
        public boolean usaQuantum() { return this == ROUND_ROBIN; }

        @Override public String toString() { return etiqueta; }
    }

    public enum Estrategia {
        PRIMER_AJUSTE("Primer ajuste"), MEJOR_AJUSTE("Mejor ajuste"), PEOR_AJUSTE("Peor ajuste");

        public final String etiqueta;

        Estrategia(String etiqueta) { this.etiqueta = etiqueta; }

        @Override public String toString() { return etiqueta; }
    }

    public enum Carga {
        TOTAL("Carga total"), BAJO_DEMANDA("Paginación bajo demanda");

        public final String etiqueta;

        Carga(String etiqueta) { this.etiqueta = etiqueta; }

        /** El reemplazo (FIFO o LRU) solo se habilita con paginación bajo demanda. */
        public boolean usaReemplazo() { return this == BAJO_DEMANDA; }

        @Override public String toString() { return etiqueta; }
    }

    public Configuracion {
        exigir(ram, "la RAM");
        exigir(pagina, "el tamaño de página");
        exigir(cpu, "la velocidad de la CPU");
        exigir(politica, "la política de planificación");
        exigir(estrategia, "la estrategia de asignación");
        exigir(carga, "el tipo de carga");
        exigir(reemplazo, "el algoritmo de reemplazo");
        if (quantum < QUANTUM_MINIMO || quantum > QUANTUM_MAXIMO)
            throw new ParametroInvalidoException("El quantum debe estar entre " + QUANTUM_MINIMO + " y " + QUANTUM_MAXIMO + " ticks (" + quantum + ")");
        if (!(costoDespacho >= 0 && costoDespacho <= COSTO_MAXIMO) || Math.abs(costoDespacho * 10 - Math.round(costoDespacho * 10)) > 1e-9)
            throw new ParametroInvalidoException("El costo del cambio de contexto va de 0 a 1 ms en pasos de 0,1 (" + costoDespacho + ")");
        if (grado < GRADO_MINIMO || grado > GRADO_MAXIMO)
            throw new ParametroInvalidoException("El grado de multiprogramación debe estar entre " + GRADO_MINIMO + " y " + GRADO_MAXIMO + " (" + grado + ")");
        if (semilla < 0) throw new ParametroInvalidoException("La semilla debe ser ≥ 0 (" + semilla + ")");
        if (dispositivos == null) throw new ParametroInvalidoException("Falta la velocidad de los dispositivos");
        Map<Dispositivo, VelocidadDispositivo> copia = new EnumMap<>(Dispositivo.class);
        for (Dispositivo d : Dispositivo.values()) {
            VelocidadDispositivo v = dispositivos.get(d);
            if (v == null) throw new ParametroInvalidoException("Falta la velocidad del dispositivo " + d);
            copia.put(d, v);
        }
        dispositivos = Collections.unmodifiableMap(copia);
    }

    private static void exigir(Object valor, String que) {
        if (valor == null) throw new ParametroInvalidoException("Falta " + que);
    }

    /** Todos los dispositivos a la misma velocidad. */
    public static Map<Dispositivo, VelocidadDispositivo> dispositivos(VelocidadDispositivo v) {
        Map<Dispositivo, VelocidadDispositivo> m = new EnumMap<>(Dispositivo.class);
        for (Dispositivo d : Dispositivo.values()) m.put(d, v);
        return m;
    }

    /**
     * Máquina del curso: 1 GiB (taller ej. 16), páginas de 64 KiB, CPU de 1 GHz (1 instrucción por
     * tick), Round Robin con q = 4, cambio de contexto de 0,1 ms, grado 10, primer ajuste, carga
     * total (LRU queda elegido para cuando se active la paginación bajo demanda) y semilla 42.
     */
    public static Configuracion porDefecto() {
        return new Configuracion(Ram.GIB_1, Pagina.KIB_64, Cpu.GHZ_1, Politica.ROUND_ROBIN, 4, 0.1, 10,
                Estrategia.PRIMER_AJUSTE, Carga.TOTAL, Reemplazo.LRU, dispositivos(VelocidadDispositivo.NORMAL), 42);
    }

    /** Cantidad de marcos de la RAM: RAM / B (siempre exacta, ambas son potencias de 2). */
    public int marcos() { return (int) (ram.bytes / pagina.bytes); }

    /** Avisos que no impiden iniciar la máquina, pero conviene ver antes (p. ej. para la prueba de estrés). */
    public List<String> advertencias() {
        List<String> avisos = new ArrayList<>();
        if (ram == Ram.MIB_128) avisos.add("Máquina modesta: con 128 MiB la memoria se agota con cargas grandes");
        if (cpu == Cpu.MHZ_500) avisos.add("CPU lenta: cada instrucción tarda 2 ticks");
        if (dispositivos.containsValue(VelocidadDispositivo.LENTO)) avisos.add("Hay dispositivos lentos: cada E/S en ellos tarda el doble");
        return avisos;
    }

    /**
     * Problemas que impiden simular esta carga en esta máquina: procesos sin espacio, con otro
     * tamaño de página, más grandes que toda la RAM o, bajo demanda, menos de 1 marco por proceso.
     */
    public List<String> problemas(List<Pcb> procesos) {
        List<String> errores = new ArrayList<>();
        if (procesos == null || procesos.isEmpty()) {
            errores.add("No hay procesos para simular");
            return errores;
        }
        for (Pcb p : procesos) {
            EspacioProceso e = p.espacio();
            if (e == null) errores.add(p + " no tiene espacio de proceso");
            else if (e.bloque() != pagina.bytes) errores.add(p + " usa páginas de " + e.bloque() / 1024 + " KiB y la máquina de " + pagina);
            else if (e.paginasTotales() > marcos()) errores.add(p + " necesita " + e.paginasTotales() + " páginas y la RAM tiene " + marcos() + " marcos");
        }
        if (carga.usaReemplazo() && procesos.size() > marcos())
            errores.add("Con paginación bajo demanda hace falta al menos 1 marco por proceso (" + procesos.size() + " procesos, " + marcos() + " marcos)");
        return errores;
    }

    /**
     * [G5][I6] Cuántos procesos de la carga pueden estar residentes a la vez en esta máquina.
     *
     * @param procesos    procesos de la carga
     * @param porGrado    límite del grado de multiprogramación
     * @param porRam      cuántos caben en la RAM a la vez (en orden de llegada; bajo demanda, 1 marco cada uno)
     * @param simultaneos el menor de los límites: los que pueden estar dentro a la vez
     * @param enEspera    los que esperarían en Nuevo si llegaran todos juntos
     */
    public record Capacidad(int procesos, int porGrado, int porRam, int simultaneos, int enEspera) {
        public String resumen() {
            String limite = simultaneos == procesos ? "todos caben" : porGrado <= porRam ? "limita el grado" : "limita la RAM";
            return "Caben " + simultaneos + " de " + procesos + " a la vez (grado " + porGrado + ", RAM " + porRam + "; " + limite + ")"
                    + (enEspera > 0 ? ". Si llegan todos juntos, " + enEspera + " esperan en Nuevo" : "");
        }
    }

    /** Resumen de capacidad antes de iniciar: lo que se puede predecir y luego comprobar en la simulación. */
    public Capacidad capacidad(List<Pcb> procesos) {
        List<Pcb> validos = procesos == null ? List.of() : procesos.stream()
                .filter(p -> p.espacio() != null && p.espacio().bloque() == pagina.bytes)
                .sorted(Comparator.comparingInt(p -> p.llegada)).toList();
        int porRam;
        if (carga.usaReemplazo()) porRam = Math.min(marcos(), validos.size());
        else {
            long usados = 0;
            porRam = 0;
            for (Pcb p : validos) {
                int k = GestorMemoria.paginasIniciales(p.espacio());
                if (usados + k > marcos()) break;
                usados += k;
                porRam++;
            }
        }
        int simultaneos = Math.min(Math.min(grado, porRam), validos.size());
        return new Capacidad(validos.size(), grado, porRam, simultaneos, validos.size() - simultaneos);
    }

    public PoliticaPlanificacion crearPolitica() {
        return switch (politica) {
            case FCFS -> new Fcfs();
            case SJF -> new Sjf();
            case SRTF -> new Srtf();
            case PRIORIDAD -> new Prioridad(false);
            case PRIORIDAD_EXPROPIATIVA -> new Prioridad(true);
            case ROUND_ROBIN -> new RoundRobin(quantum);
        };
    }

    public EstrategiaAsignacion crearEstrategia() {
        return switch (estrategia) {
            case PRIMER_AJUSTE -> EstrategiaAsignacion.primerAjuste();
            case MEJOR_AJUSTE -> EstrategiaAsignacion.mejorAjuste();
            case PEOR_AJUSTE -> EstrategiaAsignacion.peorAjuste();
        };
    }

    public GestorMemoria crearGestorMemoria() {
        return new GestorMemoria(marcos(), pagina.bytes, crearEstrategia(), carga.usaReemplazo() ? reemplazo : null);
    }

    /** Arma el Kernel de esta máquina con la carga dada; antes revisa que la carga quepa en la máquina. */
    public Kernel crearKernel(List<Pcb> procesos) {
        List<String> errores = problemas(procesos);
        if (!errores.isEmpty()) throw new ParametroInvalidoException("No se puede iniciar la máquina: " + String.join("; ", errores));
        PlanificadorLargoPlazo largoPlazo = new PlanificadorLargoPlazo(crearGestorMemoria(), grado);
        Kernel k = new Kernel(crearPolitica(), procesos, largoPlazo, costoDespacho, cpu.instruccionesPorTick);
        dispositivos.forEach(k.gestorES()::fijarVelocidad);
        return k;
    }
}
