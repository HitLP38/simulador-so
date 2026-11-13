package pe.uni.so.sesion;

/**
 * [G5] Vistas del explorador del espacio de trabajo. La arena del kernel (mapa de estados) ya funciona; las demás
 * se completan en los siguientes PR y, mientras tanto, el lienzo muestra un aviso.
 */
public enum Vista {
    MAPA_ESTADOS("Arena del kernel", true),
    PROCESOS("Procesos y Gantt", false),
    MEMORIA("Memoria", false),
    ENTRADA_SALIDA("Entrada/salida", false),
    INTERRUPCIONES("Interrupciones", false);

    public final String etiqueta;
    public final boolean disponible;

    Vista(String etiqueta, boolean disponible) {
        this.etiqueta = etiqueta;
        this.disponible = disponible;
    }

    public static final String AVISO_EN_CONSTRUCCION = "Vista en construcción: llegará en una próxima versión del simulador.";

    @Override public String toString() { return etiqueta; }
}
