package pe.uni.so.sesion;

import java.util.ArrayList;
import java.util.List;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.procesos.CreadorProcesos;
import pe.uni.so.procesos.Pcb;

/**
 * [P14][P17][P18] Carga con la que se inicia una máquina, elegida en la ventana de carga: el lote
 * oficial de 20 procesos, N procesos aleatorios (ecuación 8, llegadas de Poisson) o una tabla
 * escrita por el usuario. Cada llamada a {@link #procesos(long)} crea procesos nuevos e
 * idénticos, como necesita la sesión para reiniciar o volver a simular.
 *
 * <p>Los procesos de la tabla manual entran por «Ingresado por usuario»; los otros dos, por
 * «Creado por el SO».
 *
 * @param tipo     cuál de las tres cargas
 * @param cantidad procesos aleatorios (solo ALEATORIA)
 * @param semilla  semilla de los aleatorios y del plan de E/S de la tabla (ALEATORIA y MANUAL)
 * @param filas    procesos escritos a mano (solo MANUAL)
 */
public record PlanCarga(Tipo tipo, int cantidad, long semilla, List<Fila> filas) {

    /** Máximo de procesos de una carga aleatoria (P10 pide corridas de 1000). */
    public static final int MAX_ALEATORIOS = 1_000;
    /** Máximo de filas de la tabla manual. */
    public static final int MAX_FILAS = 100;
    /** Tamaño del ejecutable en KiB, con los mismos límites que la carga aleatoria (ecuación 8). */
    public static final int KIB_MINIMO = (int) (CreadorProcesos.TEXTO_MINIMO / 1024), KIB_MAXIMO = (int) (CreadorProcesos.TEXTO_MAXIMO / 1024);

    public enum Tipo {
        LOTE_OFICIAL("Lote oficial"), ALEATORIA("Aleatoria"), MANUAL("Manual");

        public final String etiqueta;

        Tipo(String etiqueta) { this.etiqueta = etiqueta; }
    }

    /** Un proceso de la tabla manual: llegada (tick), ráfaga (instrucciones), ejecutable (KiB) y prioridad 1–5. */
    public record Fila(int llegada, int burst, int kib, int prioridad) {
        public Fila {
            if (llegada < 0) throw new ParametroInvalidoException("La llegada debe ser ≥ 0 (" + llegada + ")");
            if (burst < CreadorProcesos.BURST_MINIMO || burst > CreadorProcesos.BURST_MAXIMO)
                throw new ParametroInvalidoException("La ráfaga debe estar entre " + CreadorProcesos.BURST_MINIMO + " y "
                        + CreadorProcesos.BURST_MAXIMO + " (" + burst + ")");
            if (kib < KIB_MINIMO || kib > KIB_MAXIMO)
                throw new ParametroInvalidoException("El ejecutable debe medir entre " + KIB_MINIMO + " y " + KIB_MAXIMO + " KiB (" + kib + ")");
            if (prioridad < CreadorProcesos.PRIORIDAD_MINIMA || prioridad > CreadorProcesos.PRIORIDAD_MAXIMA)
                throw new ParametroInvalidoException("La prioridad debe estar entre " + CreadorProcesos.PRIORIDAD_MINIMA + " y "
                        + CreadorProcesos.PRIORIDAD_MAXIMA + " (" + prioridad + ")");
        }
    }

    public PlanCarga {
        if (tipo == null) throw new ParametroInvalidoException("Falta el tipo de carga");
        filas = filas == null ? List.of() : List.copyOf(filas);
        switch (tipo) {
            case ALEATORIA -> {
                if (cantidad < 1 || cantidad > MAX_ALEATORIOS)
                    throw new ParametroInvalidoException("La carga aleatoria tiene entre 1 y " + MAX_ALEATORIOS + " procesos (" + cantidad + ")");
                if (semilla < 0) throw new ParametroInvalidoException("La semilla debe ser ≥ 0 (" + semilla + ")");
            }
            case MANUAL -> {
                if (filas.isEmpty()) throw new ParametroInvalidoException("Agrega al menos un proceso a la tabla");
                if (filas.size() > MAX_FILAS) throw new ParametroInvalidoException("La tabla admite hasta " + MAX_FILAS + " procesos");
                if (semilla < 0) throw new ParametroInvalidoException("La semilla debe ser ≥ 0 (" + semilla + ")");
            }
            case LOTE_OFICIAL -> { }
        }
    }

    public static PlanCarga loteOficial() { return new PlanCarga(Tipo.LOTE_OFICIAL, 0, 0, List.of()); }

    public static PlanCarga aleatoria(int cantidad, long semilla) { return new PlanCarga(Tipo.ALEATORIA, cantidad, semilla, List.of()); }

    public static PlanCarga manual(List<Fila> filas, long semilla) { return new PlanCarga(Tipo.MANUAL, 0, semilla, filas); }

    /** Procesos nuevos de esta carga, en páginas de {@code bloque} bytes. */
    public List<Pcb> procesos(long bloque) {
        return switch (tipo) {
            case LOTE_OFICIAL -> CreadorProcesos.loteOficial(bloque);
            case ALEATORIA -> new CreadorProcesos(semilla, bloque).aleatorios(cantidad, 0);
            case MANUAL -> {
                CreadorProcesos c = new CreadorProcesos(semilla, bloque);
                List<Pcb> lista = new ArrayList<>(filas.size());
                for (Fila f : filas) {
                    Pcb p = c.crear(f.llegada(), f.burst(), f.kib() * 1024L, CreadorProcesos.DATOS_MANUAL, CreadorProcesos.HEAP_MANUAL, f.prioridad());
                    p.marcarIngresadoPorUsuario();
                    lista.add(p);
                }
                yield lista;
            }
        };
    }

    /** Texto corto para la ficha de la máquina. */
    public String descripcion() {
        return switch (tipo) {
            case LOTE_OFICIAL -> "lote oficial de 20 procesos";
            case ALEATORIA -> cantidad + (cantidad == 1 ? " proceso aleatorio" : " procesos aleatorios") + " (semilla " + semilla + ")";
            case MANUAL -> filas.size() + (filas.size() == 1 ? " proceso ingresado" : " procesos ingresados") + " por el usuario";
        };
    }
}
