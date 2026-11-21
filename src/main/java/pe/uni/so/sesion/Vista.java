package pe.uni.so.sesion;

/**
 * [G5][P16][M3][E1][I1] Vistas del explorador del espacio de trabajo. Las cinco ya funcionan; una vista
 * nueva puede marcarse como no disponible mientras se construye y el lienzo mostrará un aviso.
 */
public enum Vista {
    MAPA_ESTADOS("Arena del kernel", true),
    PROCESOS("Procesos y Gantt", true),
    MEMORIA("Memoria", true),
    ENTRADA_SALIDA("Entrada/salida", true),
    INTERRUPCIONES("Interrupciones", true);

    public final String etiqueta;
    public final boolean disponible;

    Vista(String etiqueta, boolean disponible) {
        this.etiqueta = etiqueta;
        this.disponible = disponible;
    }

    public static final String AVISO_EN_CONSTRUCCION = "Vista en construcción: llegará en una próxima versión del simulador.";

    @Override public String toString() { return etiqueta; }
}
