package pe.uni.so.sesion;

import java.util.HashMap;
import java.util.Map;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.motor.Instantanea;

/**
 * [M3][P16] Rejilla de la vista Memoria: cada celda representa {@code porCelda} marcos
 * consecutivos y toma el dueño que más marcos tiene en ella (o libre). Con pocos marcos hay una
 * celda por marco; con muchos (16 384 en la máquina por defecto) se agrupan para que la rejilla
 * quepa en pantalla. Sin JavaFX: la UI solo dibuja las celdas.
 */
public final class MapaMarcos {

    /** Dueño de una celda sin marcos ocupados. */
    public static final int LIBRE = -1;

    private final int marcos, porCelda;
    private final int[] dueno;           // por celda: PID o LIBRE
    private final boolean[] mixta;       // por celda: tiene marcos de más de un dueño (o libres y ocupados)

    private MapaMarcos(int marcos, int porCelda, int[] dueno, boolean[] mixta) {
        this.marcos = marcos;
        this.porCelda = porCelda;
        this.dueno = dueno;
        this.mixta = mixta;
    }

    /**
     * Arma la rejilla de la memoria de la foto con a lo sumo {@code maxCeldas} celdas.
     */
    public static MapaMarcos de(Instantanea.Memoria m, int maxCeldas) {
        if (maxCeldas < 1) throw new ParametroInvalidoException("La rejilla necesita al menos 1 celda (" + maxCeldas + ")");
        int marcos = m.marcos();
        int porCelda = (int) Math.max(1, ((long) marcos + maxCeldas - 1) / maxCeldas);
        int celdas = (marcos + porCelda - 1) / porCelda;
        int[] dueno = new int[celdas];
        boolean[] mixta = new boolean[celdas];
        java.util.Arrays.fill(dueno, LIBRE);
        Map<Integer, Map<Integer, Integer>> conteo = new HashMap<>();   // celda → (pid → marcos)
        for (GestorMemoria.BloqueMarcos b : m.ocupacion())
            for (int marco = b.marcoInicial(); marco < b.marcoInicial() + b.marcos(); ) {
                int celda = marco / porCelda;
                int finCelda = Math.min((celda + 1) * porCelda, b.marcoInicial() + b.marcos());
                conteo.computeIfAbsent(celda, k -> new HashMap<>()).merge(b.pid(), finCelda - marco, Integer::sum);
                marco = finCelda;
            }
        for (Map.Entry<Integer, Map<Integer, Integer>> e : conteo.entrySet()) {
            int celda = e.getKey();
            int tamano = Math.min(porCelda, marcos - celda * porCelda);
            int ocupados = 0, mejor = LIBRE, maximo = 0;
            for (Map.Entry<Integer, Integer> x : e.getValue().entrySet()) {
                ocupados += x.getValue();
                if (x.getValue() > maximo || (x.getValue() == maximo && x.getKey() < mejor)) { mejor = x.getKey(); maximo = x.getValue(); }
            }
            int libres = tamano - ocupados;
            dueno[celda] = libres > maximo ? LIBRE : mejor;
            mixta[celda] = e.getValue().size() > 1 || libres > 0;
        }
        return new MapaMarcos(marcos, porCelda, dueno, mixta);
    }

    public int marcos() { return marcos; }
    public int porCelda() { return porCelda; }
    public int celdas() { return dueno.length; }

    /** PID dueño de la celda, o {@link #LIBRE}. */
    public int dueno(int celda) { return dueno[celda]; }

    /** ¿La celda junta marcos de distintos dueños (o libres y ocupados)? */
    public boolean mixta(int celda) { return mixta[celda]; }

    /** Celda que contiene el marco. */
    public int celdaDe(int marco) {
        if (marco < 0 || marco >= marcos) throw new ParametroInvalidoException("Marco fuera de la RAM: " + marco);
        return marco / porCelda;
    }

    /** Primer marco de la celda. */
    public int primerMarco(int celda) { return celda * porCelda; }

    /** Columnas para una rejilla de ancho dado con celdas de lado {@code lado} (al menos 1). */
    public static int columnas(double ancho, double lado) { return Math.max(1, (int) Math.floor(ancho / lado)); }
}
