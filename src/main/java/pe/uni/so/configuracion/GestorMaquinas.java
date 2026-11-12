package pe.uni.so.configuracion;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import pe.uni.so.excepciones.EventoInvalidoException;

/**
 * [G5] Lógica del gestor de máquinas (la ventana tipo VirtualBox que se abre al iniciar la app),
 * separada de JavaFX para poder probarla. Sobre {@link RepositorioMaquinas} agrega lo que la
 * interfaz necesita: qué máquina preseleccionar, validar el nombre mientras se escribe, renombrar
 * al configurar, sugerir el nombre de una copia y no tocar una máquina que está abierta.
 */
public class GestorMaquinas {

    public static final String PREFIJO_COPIA = "Copia de ";

    private final RepositorioMaquinas repositorio;
    private final Set<String> abiertas = new HashSet<>();      // claves de las máquinas con ventana abierta

    public GestorMaquinas(RepositorioMaquinas repositorio) {
        if (repositorio == null) throw new EventoInvalidoException("Falta el repositorio de máquinas");
        this.repositorio = repositorio;
    }

    public RepositorioMaquinas repositorio() { return repositorio; }

    /** La máquina por defecto primero y luego las demás por nombre. */
    public List<Maquina> maquinas() { return repositorio.listar(); }

    /**
     * Índice a seleccionar en la lista: el de la máquina con ese nombre si aún existe (tras
     * crear, editar o clonar) y, si no, 0, la máquina por defecto (así se abre la app).
     */
    public int indiceASeleccionar(String nombre) {
        if (nombre == null) return 0;
        List<Maquina> lista = maquinas();
        for (int i = 0; i < lista.size(); i++)
            if (mismaClave(lista.get(i).nombre(), nombre)) return i;
        return 0;
    }

    /** Archivos que no se pudieron leer, con el motivo; la interfaz los muestra aparte. */
    public Map<Path, String> archivosDanados() { return repositorio.archivosDanados(); }

    public void eliminarArchivoDanado(Path archivo) { repositorio.eliminarArchivoDanado(archivo); }

    /**
     * Error del nombre que se escribe en el formulario, o vacío si se puede usar.
     *
     * @param nombre       lo escrito
     * @param nombreActual nombre de la máquina que se configura, o null si es una máquina nueva
     */
    public Optional<String> errorNombre(String nombre, String nombreActual) {
        if (nombre == null || nombre.isBlank()) return Optional.of("Escribe un nombre para la máquina");
        String n = nombre.strip();
        if (n.length() > Maquina.LARGO_MAXIMO_NOMBRE)
            return Optional.of("El nombre admite hasta " + Maquina.LARGO_MAXIMO_NOMBRE + " caracteres (" + n.length() + ")");
        boolean editaPorDefecto = nombreActual != null && mismaClave(nombreActual, Maquina.NOMBRE_POR_DEFECTO);
        if (editaPorDefecto)
            return n.equals(Maquina.NOMBRE_POR_DEFECTO) ? Optional.empty()
                    : Optional.of("La máquina por defecto no se puede renombrar; clónala para tener otra");
        if (mismaClave(n, Maquina.NOMBRE_POR_DEFECTO))
            return Optional.of("«" + n + "» está reservado para la máquina por defecto");
        if (nombreActual != null && mismaClave(n, nombreActual)) return Optional.empty();   // mismo archivo
        if (repositorio.existe(n)) return Optional.of("Ya existe una máquina llamada «" + n + "»");
        if (hayArchivoDanado(n))
            return Optional.of("Hay un archivo dañado con ese nombre (" + archivoDe(n) + "); elimínalo o usa otro nombre");
        return Optional.empty();
    }

    /** Guarda una máquina nueva; el nombre no debe estar usado. */
    public Maquina crear(Maquina m) {
        exigirNombre(m.nombre(), null);
        repositorio.guardar(m);
        return m;
    }

    /**
     * Guarda los cambios de una máquina. Si cambió el nombre, primero escribe el archivo nuevo y
     * luego borra el anterior: si la escritura falla, la máquina original sigue intacta.
     */
    public Maquina actualizar(String nombreActual, Maquina nueva) {
        Maquina actual = repositorio.buscar(nombreActual)
                .orElseThrow(() -> new EventoInvalidoException("No existe la máquina «" + nombreActual + "»"));
        exigirCerrada(actual.nombre(), "configurar");
        exigirNombre(nueva.nombre(), actual.nombre());
        repositorio.guardar(nueva);
        if (!mismaClave(actual.nombre(), nueva.nombre())) repositorio.eliminar(actual.nombre());
        return nueva;
    }

    /**
     * Nombre libre para la copia: «Copia de X», «Copia de X (2)», … recortando X si hace falta
     * para no pasar de {@link Maquina#LARGO_MAXIMO_NOMBRE} caracteres.
     */
    public String nombreParaCopia(String origen) {
        for (int n = 1; ; n++) {
            String sufijo = n == 1 ? "" : " (" + n + ")";
            int espacio = Maquina.LARGO_MAXIMO_NOMBRE - PREFIJO_COPIA.length() - sufijo.length();
            String base = origen.strip();
            if (base.length() > espacio) base = base.substring(0, espacio).strip();
            String candidato = PREFIJO_COPIA + base + sufijo;
            if (errorNombre(candidato, null).isEmpty()) return candidato;
        }
    }

    public Maquina clonar(String origen, String nuevoNombre) {
        exigirNombre(nuevoNombre, null);
        return repositorio.clonar(origen, nuevoNombre.strip());
    }

    public boolean sePuedeEliminar(String nombre) {
        return !mismaClave(nombre, Maquina.NOMBRE_POR_DEFECTO) && !estaAbierta(nombre);
    }

    public void eliminar(String nombre) {
        exigirCerrada(nombre, "eliminar");
        repositorio.eliminar(nombre);
    }

    /** Vuelve la máquina por defecto a la de fábrica (no se puede si está abierta). */
    public Maquina restaurarPorDefecto() {
        exigirCerrada(Maquina.NOMBRE_POR_DEFECTO, "restaurar");
        return repositorio.restaurarPorDefecto();
    }

    // ---------------------------------------------------------------- máquinas abiertas

    /** Registra que la máquina se abrió (botón Iniciar); devuelve false si ya estaba abierta. */
    public boolean abrir(String nombre) { return abiertas.add(RepositorioMaquinas.clave(nombre)); }

    public void cerrar(String nombre) { abiertas.remove(RepositorioMaquinas.clave(nombre)); }

    public boolean estaAbierta(String nombre) { return abiertas.contains(RepositorioMaquinas.clave(nombre)); }

    // ---------------------------------------------------------------- auxiliares

    private void exigirNombre(String nombre, String nombreActual) {
        errorNombre(nombre, nombreActual).ifPresent(e -> { throw new EventoInvalidoException(e); });
    }

    private void exigirCerrada(String nombre, String accion) {
        if (estaAbierta(nombre))
            throw new EventoInvalidoException("Cierra la ventana de «" + nombre + "» antes de " + accion + "la");
    }

    private boolean hayArchivoDanado(String nombre) {
        String archivo = archivoDe(nombre);
        return repositorio.archivosDanados().keySet().stream().anyMatch(p -> p.getFileName().toString().equals(archivo));
    }

    private static String archivoDe(String nombre) { return RepositorioMaquinas.clave(nombre) + RepositorioMaquinas.EXTENSION; }

    private static boolean mismaClave(String a, String b) {
        return RepositorioMaquinas.clave(a).equals(RepositorioMaquinas.clave(b));
    }
}
