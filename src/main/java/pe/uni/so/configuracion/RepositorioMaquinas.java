package pe.uni.so.configuracion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;

/**
 * [G5] Las máquinas guardadas, como el gestor de máquinas virtuales: un archivo por máquina en
 * una carpeta. La máquina por defecto siempre existe: se puede editar y restaurar, pero no borrar.
 */
public class RepositorioMaquinas {

    public static final String EXTENSION = ".maquina";

    /** Nombres de dispositivo que Windows reserva incluso con extensión (nul.maquina no es un archivo normal). */
    private static final Set<String> RESERVADAS_WINDOWS = reservadas();

    private final Path carpeta;

    public RepositorioMaquinas(Path carpeta) {
        if (carpeta == null) throw new EventoInvalidoException("Falta la carpeta de las máquinas");
        this.carpeta = carpeta;
    }

    /** Carpeta del usuario: ~/.simulador-so/maquinas. */
    public static RepositorioMaquinas enCarpetaDelUsuario() {
        return new RepositorioMaquinas(Path.of(System.getProperty("user.home"), ".simulador-so", "maquinas"));
    }

    public Path carpeta() { return carpeta; }

    /**
     * La máquina por defecto primero (editada o de fábrica) y luego las demás por nombre. Un
     * archivo dañado (editado a mano, truncado) se omite para que el gestor siga usable; la
     * interfaz los informa con {@link #archivosDanados()}.
     */
    public List<Maquina> listar() {
        List<Maquina> otras = new ArrayList<>();
        for (Path a : archivos()) {
            try {
                Maquina m = Maquina.leer(a);
                if (!m.esPorDefecto()) otras.add(m);
            } catch (ParametroInvalidoException | IOException ex) {
                // archivo dañado o ilegible (p. ej. sin permiso de lectura): se omite y queda en archivosDanados()
            }
        }
        otras.sort(Comparator.comparing(m -> m.nombre().toLowerCase(Locale.ROOT)));
        List<Maquina> todas = new ArrayList<>();
        todas.add(porDefecto());
        todas.addAll(otras);
        return todas;
    }

    /** La máquina por defecto editada; si nunca se editó o su archivo está dañado, la de fábrica. */
    public Maquina porDefecto() {
        try {
            return leerSiExiste(archivo(Maquina.NOMBRE_POR_DEFECTO)).orElseGet(Maquina::porDefecto);
        } catch (ParametroInvalidoException | UncheckedIOException ex) {   // dañada o ilegible: la de fábrica
            return Maquina.porDefecto();
        }
    }

    /** Archivos de máquina que no se pueden leer, con el motivo, para mostrarlos al usuario. */
    public Map<Path, String> archivosDanados() {
        Map<Path, String> danados = new LinkedHashMap<>();
        for (Path a : archivos()) {
            try {
                Maquina.leer(a);
            } catch (ParametroInvalidoException ex) {
                danados.put(a, ex.getMessage());
            } catch (IOException ex) {
                danados.put(a, "no se pudo leer: " + ex.getMessage());
            }
        }
        return danados;
    }

    /** Borra un archivo dañado de la carpeta de máquinas (no se puede elegir desde listar()). */
    public void eliminarArchivoDanado(Path archivo) {
        if (!archivosDanados().containsKey(archivo)) throw new EventoInvalidoException(archivo.getFileName() + " no es un archivo dañado de esta carpeta");
        borrar(archivo);
    }

    private List<Path> archivos() {
        if (!Files.isDirectory(carpeta)) return List.of();
        try (Stream<Path> archivos = Files.list(carpeta)) {
            return archivos.filter(a -> a.getFileName().toString().endsWith(EXTENSION)).sorted().toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer la carpeta de máquinas " + carpeta, ex);
        }
    }

    public Optional<Maquina> buscar(String nombre) {
        return listar().stream().filter(m -> clave(m.nombre()).equals(clave(nombre))).findFirst();
    }

    public boolean existe(String nombre) { return buscar(nombre).isPresent(); }

    /** Crea o reemplaza (editar) la máquina con ese nombre. */
    public void guardar(Maquina m) {
        exigirNombreUsable(m);
        try {
            m.escribir(archivo(m.nombre()));
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo guardar la máquina " + m.nombre(), ex);
        }
    }

    /**
     * Crea una máquina con un nombre nuevo de forma exclusiva (CREATE_NEW): si otro proceso
     * acaba de crear ese archivo, falla en vez de sobrescribirlo.
     */
    public void crear(Maquina m) {
        exigirNombreUsable(m);
        try {
            m.escribir(archivo(m.nombre()), true);
        } catch (FileAlreadyExistsException ex) {
            throw new EventoInvalidoException("Ya existe una máquina llamada «" + m.nombre() + "»");
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo crear la máquina " + m.nombre(), ex);
        }
    }

    /** True si el nombre daría un archivo que Windows no permite (con, prn, aux, nul, com0–9, lpt0–9). */
    public static boolean nombreReservadoPorWindows(String nombre) { return RESERVADAS_WINDOWS.contains(clave(nombre)); }

    private static void exigirNombreUsable(Maquina m) {
        if (!m.esPorDefecto() && clave(m.nombre()).equals(clave(Maquina.NOMBRE_POR_DEFECTO)))
            throw new EventoInvalidoException("El nombre «" + m.nombre() + "» está reservado para la máquina por defecto");
        if (nombreReservadoPorWindows(m.nombre()))
            throw new EventoInvalidoException("«" + m.nombre() + "» es un nombre reservado por Windows; usa otro");
    }

    private static Set<String> reservadas() {
        Set<String> r = new HashSet<>(List.of("con", "prn", "aux", "nul"));
        for (int i = 0; i <= 9; i++) {
            r.add("com" + i);
            r.add("lpt" + i);
        }
        return Set.copyOf(r);
    }

    /** Copia una máquina con otro nombre, que no debe existir. */
    public Maquina clonar(String origen, String nuevoNombre) {
        Maquina base = buscar(origen).orElseThrow(() -> new EventoInvalidoException("No existe la máquina «" + origen + "»"));
        if (existe(nuevoNombre) || Files.exists(archivo(nuevoNombre)))   // también un archivo ilegible o dañado con ese nombre
            throw new EventoInvalidoException("Ya existe una máquina llamada «" + nuevoNombre + "»");
        Maquina copia = base.conNombre(nuevoNombre);
        exigirNombreUsable(copia);
        try {
            copia.escribir(archivo(nuevoNombre), true);               // CREATE_NEW: nunca pisa un archivo creado entre medio
        } catch (FileAlreadyExistsException ex) {
            throw new EventoInvalidoException("Ya existe una máquina llamada «" + nuevoNombre + "»");
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo crear la máquina " + nuevoNombre, ex);
        }
        return copia;
    }

    public void eliminar(String nombre) {
        if (clave(nombre).equals(clave(Maquina.NOMBRE_POR_DEFECTO)))
            throw new EventoInvalidoException("La máquina por defecto no se puede eliminar; se puede restaurar");
        if (!existe(nombre)) throw new EventoInvalidoException("No existe la máquina «" + nombre + "»");
        borrar(archivo(nombre));
    }

    /** Vuelve la máquina por defecto a sus valores de fábrica. */
    public Maquina restaurarPorDefecto() {
        borrar(archivo(Maquina.NOMBRE_POR_DEFECTO));
        return Maquina.porDefecto();
    }

    private Path archivo(String nombre) { return carpeta.resolve(clave(nombre) + EXTENSION); }

    /** Nombre de archivo a partir del nombre: sin tildes, en minúsculas y con guiones. */
    static String clave(String nombre) {
        String sinTildes = Normalizer.normalize(nombre.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String c = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return c.isEmpty() ? "maquina" : c;
    }

    private static Optional<Maquina> leerSiExiste(Path a) {
        if (!Files.exists(a)) return Optional.empty();
        try {
            return Optional.of(Maquina.leer(a));
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer la máquina " + a, ex);
        }
    }

    private static void borrar(Path a) {
        try {
            Files.deleteIfExists(a);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo borrar " + a, ex);
        }
    }
}
