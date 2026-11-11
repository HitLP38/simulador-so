package pe.uni.so.configuracion;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.VelocidadDispositivo;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.Reemplazo;

/**
 * [G5] Máquina simulada con nombre, como una máquina virtual. Se guarda en un archivo de texto
 * clave=valor (sin base de datos) y se vuelve a leer validando cada valor.
 */
public record Maquina(String nombre, Configuracion configuracion) {

    public static final String NOMBRE_POR_DEFECTO = "Máquina del curso";
    public static final int LARGO_MAXIMO_NOMBRE = 40;

    public Maquina {
        if (nombre == null || nombre.isBlank()) throw new ParametroInvalidoException("La máquina necesita un nombre");
        nombre = nombre.strip();
        if (nombre.length() > LARGO_MAXIMO_NOMBRE)
            throw new ParametroInvalidoException("El nombre de la máquina admite hasta " + LARGO_MAXIMO_NOMBRE + " caracteres");
        if (configuracion == null) throw new ParametroInvalidoException("Falta la configuración de la máquina " + nombre);
    }

    public static Maquina porDefecto() { return new Maquina(NOMBRE_POR_DEFECTO, Configuracion.porDefecto()); }

    public boolean esPorDefecto() { return nombre.equals(NOMBRE_POR_DEFECTO); }

    /** La misma máquina con otro nombre (para clonar). */
    public Maquina conNombre(String otro) { return new Maquina(otro, configuracion); }

    /** Crea o reemplaza el archivo de la máquina (editar). */
    public void escribir(Path archivo) throws IOException { escribir(archivo, false); }

    /**
     * Escribe la máquina. Con {@code soloSiNoExiste} el archivo se crea de forma exclusiva
     * (CREATE_NEW): si otro ya lo creó, lanza {@link java.nio.file.FileAlreadyExistsException}
     * en vez de sobrescribirlo.
     */
    public void escribir(Path archivo, boolean soloSiNoExiste) throws IOException {
        Configuracion c = configuracion;
        Properties p = new Properties();
        p.setProperty("nombre", nombre);
        p.setProperty("ram", c.ram().name());
        p.setProperty("pagina", c.pagina().name());
        p.setProperty("cpu", c.cpu().name());
        p.setProperty("politica", c.politica().name());
        p.setProperty("quantum", Integer.toString(c.quantum()));
        p.setProperty("costoDespacho", Double.toString(c.costoDespacho()));
        p.setProperty("grado", Integer.toString(c.grado()));
        p.setProperty("estrategia", c.estrategia().name());
        p.setProperty("carga", c.carga().name());
        p.setProperty("reemplazo", c.reemplazo().name());
        c.dispositivos().forEach((d, v) -> p.setProperty("dispositivo." + d.name(), v.name()));
        p.setProperty("semilla", Long.toString(c.semilla()));
        Path carpeta = archivo.toAbsolutePath().getParent();
        if (carpeta != null) Files.createDirectories(carpeta);
        OpenOption[] opciones = soloSiNoExiste
                ? new OpenOption[] {StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE}
                : new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
        try (Writer w = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8, opciones)) {
            p.store(w, "Maquina del simulador de SO (SW407)");
        }
    }

    public static Maquina leer(Path archivo) throws IOException {
        Properties p = new Properties();
        String origen = archivo.getFileName().toString();
        try (Reader r = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (CharacterCodingException ex) {                       // bytes que no son UTF-8 válido
            throw new ParametroInvalidoException(origen + ": el archivo no está en UTF-8 válido");
        } catch (IllegalArgumentException ex) {                       // escape \\uXXXX mal formado
            throw new ParametroInvalidoException(origen + ": formato inválido (" + ex.getMessage() + ")");
        }
        Map<Dispositivo, VelocidadDispositivo> dispositivos = new EnumMap<>(Dispositivo.class);
        for (Dispositivo d : Dispositivo.values())
            dispositivos.put(d, opcion(p, origen, "dispositivo." + d.name(), VelocidadDispositivo.class));
        Configuracion c = new Configuracion(
                opcion(p, origen, "ram", Configuracion.Ram.class),
                opcion(p, origen, "pagina", Configuracion.Pagina.class),
                opcion(p, origen, "cpu", Configuracion.Cpu.class),
                opcion(p, origen, "politica", Configuracion.Politica.class),
                entero(p, origen, "quantum"),
                decimal(p, origen, "costoDespacho"),
                entero(p, origen, "grado"),
                opcion(p, origen, "estrategia", Configuracion.Estrategia.class),
                opcion(p, origen, "carga", Configuracion.Carga.class),
                opcion(p, origen, "reemplazo", Reemplazo.class),
                dispositivos,
                largo(p, origen, "semilla"));
        return new Maquina(texto(p, origen, "nombre"), c);
    }

    private static String texto(Properties p, String origen, String clave) {
        String v = p.getProperty(clave);
        if (v == null || v.isBlank()) throw new ParametroInvalidoException(origen + ": falta «" + clave + "»");
        return v.strip();
    }

    private static <E extends Enum<E>> E opcion(Properties p, String origen, String clave, Class<E> tipo) {
        String v = texto(p, origen, clave);
        try {
            return Enum.valueOf(tipo, v);
        } catch (IllegalArgumentException ex) {
            throw new ParametroInvalidoException(origen + ": «" + v + "» no es un valor válido para «" + clave + "»");
        }
    }

    private static int entero(Properties p, String origen, String clave) {
        String v = texto(p, origen, clave);
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ex) {
            throw new ParametroInvalidoException(origen + ": «" + v + "» no es un número entero para «" + clave + "»");
        }
    }

    private static long largo(Properties p, String origen, String clave) {
        String v = texto(p, origen, clave);
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException ex) {
            throw new ParametroInvalidoException(origen + ": «" + v + "» no es un número entero para «" + clave + "»");
        }
    }

    private static double decimal(Properties p, String origen, String clave) {
        String v = texto(p, origen, clave);
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ex) {
            throw new ParametroInvalidoException(origen + ": «" + v + "» no es un número para «" + clave + "»");
        }
    }
}
