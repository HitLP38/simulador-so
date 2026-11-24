package pe.uni.so.comparador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Configuracion.Estrategia;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.sesion.PlanCarga;

/**
 * [P17][M8][G1] Lo que el diálogo «Comparar configuraciones» elige antes de correr: la carga (la
 * de la máquina abierta o N aleatorios con semilla), las políticas y las estrategias marcadas. Arma
 * los escenarios y los parámetros del CSV, sugiere el nombre del archivo y da el texto de cada
 * celda. No depende de JavaFX, para probarlo sin ventana.
 *
 * @param maquina     nombre de la máquina abierta
 * @param base        su configuración
 * @param carga       carga con la que se corre la comparación
 * @param politicas   políticas de la serie de planificación (puede estar vacía)
 * @param estrategias estrategias de la serie de memoria (puede estar vacía)
 */
public record OpcionesComparacion(String maquina, Configuracion base, PlanCarga carga, List<Politica> politicas,
                                  List<Estrategia> estrategias) {

    /** N y semilla que se proponen cuando la máquina no se abrió con una carga aleatoria (prueba de estrés de P10). */
    public static final int N_SUGERIDO = PlanCarga.MAX_ALEATORIOS;
    public static final long SEMILLA_SUGERIDA = 42;

    public OpcionesComparacion {
        if (maquina == null || maquina.isBlank()) throw new ParametroInvalidoException("Falta el nombre de la máquina");
        if (base == null) throw new ParametroInvalidoException("Falta la configuración de la máquina");
        if (carga == null) throw new ParametroInvalidoException("Falta la carga de la comparación");
        politicas = politicas == null ? List.of() : List.copyOf(politicas);
        estrategias = estrategias == null ? List.of() : List.copyOf(estrategias);
        if (politicas.isEmpty() && estrategias.isEmpty())
            throw new ParametroInvalidoException("Marca al menos una política o una estrategia para comparar");
    }

    /**
     * La carga elegida: la de la máquina abierta, o N aleatorios con semilla (N ≤ {@link PlanCarga#MAX_ALEATORIOS}).
     */
    public static PlanCarga carga(PlanCarga deLaMaquina, boolean usarLaDeLaMaquina, int n, long semilla) {
        if (usarLaDeLaMaquina) {
            if (deLaMaquina == null) throw new ParametroInvalidoException("La máquina no tiene carga");
            return deLaMaquina;
        }
        return PlanCarga.aleatoria(n, semilla);
    }

    /** N inicial del diálogo: el de la carga aleatoria de la máquina o {@link #N_SUGERIDO}. */
    public static int nInicial(PlanCarga deLaMaquina) {
        return deLaMaquina != null && deLaMaquina.tipo() == PlanCarga.Tipo.ALEATORIA ? deLaMaquina.cantidad() : N_SUGERIDO;
    }

    /** Semilla inicial del diálogo: la de la carga aleatoria de la máquina o {@link #SEMILLA_SUGERIDA}. */
    public static long semillaInicial(PlanCarga deLaMaquina) {
        return deLaMaquina != null && deLaMaquina.tipo() == PlanCarga.Tipo.ALEATORIA ? deLaMaquina.semilla() : SEMILLA_SUGERIDA;
    }

    /** Los escenarios de la comparación, en el orden de las opciones. */
    public List<Comparador.Escenario> escenarios() {
        return Comparador.escenarios(base, politicas, estrategias);
    }

    /** Cuántos procesos tiene la carga en esta máquina. */
    public int procesos() {
        return carga.procesos(base.pagina().bytes).size();
    }

    /** Parámetros que encabezan el CSV. */
    public ExportadorCsv.Parametros parametros() {
        return new ExportadorCsv.Parametros(maquina, carga.descripcion(), carga.semilla(), procesos());
    }

    /**
     * Nombre sugerido para el CSV: {@code comparacion-<máquina>-<aaaa-mm-dd>.csv}, con el nombre de
     * la máquina en minúsculas, sin tildes y con guiones en lugar de espacios o símbolos.
     */
    public static String nombreArchivo(String maquina, LocalDate fecha) {
        String limpio = Normalizer.normalize(maquina == null ? "" : maquina, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (limpio.isEmpty()) limpio = "maquina";
        return "comparacion-" + limpio + "-" + fecha + ".csv";
    }

    /** Texto de una celda numérica con los decimales de la columna y el separador decimal dado. */
    public static String texto(Columna c, Resultado r, char decimal) {
        String s = BigDecimal.valueOf(c.valor(r)).setScale(c.decimales, RoundingMode.HALF_UP).toPlainString();
        return decimal == '.' ? s : s.replace('.', decimal);
    }

    /** Resumen de una lista de opciones marcadas, para la barra de estado del diálogo. */
    public static String resumen(Collection<Politica> politicas, Collection<Estrategia> estrategias) {
        int p = politicas == null ? 0 : politicas.size(), e = estrategias == null ? 0 : estrategias.size();
        int total = p + e;
        return total + (total == 1 ? " configuración" : " configuraciones") + " (" + p
                + (p == 1 ? " política" : " políticas") + " y " + e + (e == 1 ? " estrategia" : " estrategias") + ")";
    }
}
