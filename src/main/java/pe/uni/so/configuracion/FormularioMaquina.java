package pe.uni.so.configuracion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import pe.uni.so.configuracion.Configuracion.Carga;
import pe.uni.so.configuracion.Configuracion.Cpu;
import pe.uni.so.configuracion.Configuracion.Estrategia;
import pe.uni.so.configuracion.Configuracion.Pagina;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.configuracion.Configuracion.Ram;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.VelocidadDispositivo;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.Reemplazo;
import pe.uni.so.procesos.CreadorProcesos;
import pe.uni.so.procesos.Pcb;

/**
 * [G5] Modelo del formulario Nueva / Configurar, sin JavaFX. Solo ofrece opciones válidas: listas
 * cerradas para RAM, página, CPU, política, estrategia, carga, reemplazo, velocidades y costo;
 * rangos para quantum y grado. Lo único que se escribe a mano es el nombre y la semilla, y esos
 * dos se validan antes de habilitar «Aceptar». Los avisos se calculan con el lote oficial de 20
 * procesos, para ver antes de iniciar si la máquina lo soporta.
 */
public class FormularioMaquina {

    /** Largo máximo de la semilla escrita (cabe en un long sin desbordar). */
    public static final int DIGITOS_SEMILLA = 18;

    private final Function<String, Optional<String>> validadorNombre;

    private String nombre;
    private Ram ram;
    private Pagina pagina;
    private Cpu cpu;
    private Politica politica;
    private int quantum;
    private double costoDespacho;
    private int grado;
    private Estrategia estrategia;
    private Carga carga;
    private Reemplazo reemplazo;
    private final Map<Dispositivo, VelocidadDispositivo> dispositivos = new EnumMap<>(Dispositivo.class);
    private String semilla;

    /**
     * @param base            máquina con la que se llena el formulario
     * @param validadorNombre error del nombre escrito, o vacío si se puede usar
     *                        (normalmente {@link GestorMaquinas#errorNombre})
     */
    public FormularioMaquina(Maquina base, Function<String, Optional<String>> validadorNombre) {
        if (base == null) throw new ParametroInvalidoException("Falta la máquina base del formulario");
        this.validadorNombre = validadorNombre == null ? n -> Optional.empty() : validadorNombre;
        Configuracion c = base.configuracion();
        nombre = base.nombre();
        ram = c.ram();
        pagina = c.pagina();
        cpu = c.cpu();
        politica = c.politica();
        quantum = c.quantum();
        costoDespacho = c.costoDespacho();
        grado = c.grado();
        estrategia = c.estrategia();
        carga = c.carga();
        reemplazo = c.reemplazo();
        dispositivos.putAll(c.dispositivos());
        semilla = Long.toString(c.semilla());
    }

    /** Formulario de una máquina nueva: valores de la máquina del curso y un nombre libre. */
    public static FormularioMaquina nueva(GestorMaquinas gestor) {
        String nombre = "Máquina nueva";
        for (int n = 2; gestor.errorNombre(nombre, null).isPresent(); n++) nombre = "Máquina nueva " + n;
        return new FormularioMaquina(new Maquina(nombre, Configuracion.porDefecto()), n -> gestor.errorNombre(n, null));
    }

    /** Formulario para configurar una máquina existente (puede cambiarle el nombre). */
    public static FormularioMaquina configurar(GestorMaquinas gestor, Maquina m) {
        return new FormularioMaquina(m, n -> gestor.errorNombre(n, m.nombre()));
    }

    // ---------------------------------------------------------------- opciones válidas

    /** Costos de cambio de contexto ofrecidos: 0,0 a 1,0 ms en pasos de 0,1. */
    public static List<Double> costos() {
        int pasos = (int) Math.round(Configuracion.COSTO_MAXIMO / Configuracion.PASO_COSTO);
        return IntStream.rangeClosed(0, pasos).mapToObj(i -> Math.round(i * Configuracion.PASO_COSTO * 10) / 10.0).toList();
    }

    /** «0,1 ms», como se muestra en la lista. */
    public static String etiquetaCosto(double costo) { return String.format(java.util.Locale.ROOT, "%.1f ms", costo).replace('.', ','); }

    /** El quantum solo se edita con Round Robin. */
    public boolean quantumHabilitado() { return politica.usaQuantum(); }

    /** El reemplazo solo se edita con paginación bajo demanda. */
    public boolean reemplazoHabilitado() { return carga.usaReemplazo(); }

    /** Cantidad de marcos con la RAM y la página elegidas. */
    public int marcos() { return (int) (ram.bytes / pagina.bytes); }

    // ---------------------------------------------------------------- validación

    /** Errores que impiden aceptar el formulario (vacío = se puede guardar). */
    public List<String> errores() {
        List<String> errores = new ArrayList<>();
        validadorNombre.apply(nombre).ifPresent(errores::add);
        errorSemilla().ifPresent(errores::add);
        return errores;
    }

    public boolean valido() { return errores().isEmpty(); }

    private Optional<String> errorSemilla() {
        String s = semilla == null ? "" : semilla.strip();
        if (s.isEmpty()) return Optional.of("Escribe la semilla (un entero ≥ 0)");
        if (!s.chars().allMatch(Character::isDigit)) return Optional.of("La semilla solo admite dígitos (" + s + ")");
        if (s.length() > DIGITOS_SEMILLA) return Optional.of("La semilla admite hasta " + DIGITOS_SEMILLA + " dígitos");
        return Optional.empty();
    }

    /** Arma la máquina; falla si el formulario tiene errores. */
    public Maquina construir() {
        List<String> errores = errores();
        if (!errores.isEmpty()) throw new ParametroInvalidoException(String.join("; ", errores));
        return new Maquina(nombre.strip(), configuracion());
    }

    private Configuracion configuracion() {
        return new Configuracion(ram, pagina, cpu, politica, quantum, costoDespacho, grado, estrategia, carga, reemplazo,
                dispositivos, Long.parseLong(semilla.strip()));
    }

    /**
     * Avisos que no impiden guardar: los de {@link Configuracion#advertencias()}, los problemas
     * de la máquina con el lote oficial y su capacidad (cuántos procesos caben a la vez).
     */
    public List<String> avisos() {
        if (errorSemilla().isPresent()) return List.of();
        Configuracion c = configuracion();
        List<String> avisos = new ArrayList<>(c.advertencias());
        List<Pcb> lote = CreadorProcesos.loteOficial(pagina.bytes);
        for (String p : c.problemas(lote)) avisos.add("Lote oficial: " + p);
        avisos.add("Lote oficial: " + c.capacidad(lote).resumen());
        return avisos;
    }

    // ---------------------------------------------------------------- campos

    public String nombre() { return nombre; }
    public void nombre(String v) { nombre = v; }
    public Ram ram() { return ram; }
    public void ram(Ram v) { ram = exigir(v, "la RAM"); }
    public Pagina pagina() { return pagina; }
    public void pagina(Pagina v) { pagina = exigir(v, "el tamaño de página"); }
    public Cpu cpu() { return cpu; }
    public void cpu(Cpu v) { cpu = exigir(v, "la velocidad de la CPU"); }
    public Politica politica() { return politica; }
    public void politica(Politica v) { politica = exigir(v, "la política"); }
    public int quantum() { return quantum; }

    public void quantum(int v) {
        if (v < Configuracion.QUANTUM_MINIMO || v > Configuracion.QUANTUM_MAXIMO)
            throw new ParametroInvalidoException("El quantum va de " + Configuracion.QUANTUM_MINIMO + " a " + Configuracion.QUANTUM_MAXIMO);
        quantum = v;
    }

    public double costoDespacho() { return costoDespacho; }

    public void costoDespacho(double v) {
        if (!costos().contains(v)) throw new ParametroInvalidoException("Costo no ofrecido: " + v);
        costoDespacho = v;
    }

    public int grado() { return grado; }

    public void grado(int v) {
        if (v < Configuracion.GRADO_MINIMO || v > Configuracion.GRADO_MAXIMO)
            throw new ParametroInvalidoException("El grado va de " + Configuracion.GRADO_MINIMO + " a " + Configuracion.GRADO_MAXIMO);
        grado = v;
    }

    public Estrategia estrategia() { return estrategia; }
    public void estrategia(Estrategia v) { estrategia = exigir(v, "la estrategia"); }
    public Carga carga() { return carga; }
    public void carga(Carga v) { carga = exigir(v, "el tipo de carga"); }
    public Reemplazo reemplazo() { return reemplazo; }
    public void reemplazo(Reemplazo v) { reemplazo = exigir(v, "el reemplazo"); }
    public VelocidadDispositivo dispositivo(Dispositivo d) { return dispositivos.get(d); }
    public void dispositivo(Dispositivo d, VelocidadDispositivo v) { dispositivos.put(exigir(d, "el dispositivo"), exigir(v, "la velocidad")); }
    public String semilla() { return semilla; }
    public void semilla(String v) { semilla = v; }

    private static <T> T exigir(T valor, String que) {
        if (valor == null) throw new ParametroInvalidoException("Falta " + que);
        return valor;
    }
}
