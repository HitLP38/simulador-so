package pe.uni.so.motor;

import pe.uni.so.procesos.Estado;

/**
 * [P16] Color de cada estado en todas las vistas (tabla de PCB, Gantt, arena del kernel y
 * leyenda). Se define una sola vez aquí para que todas las pantallas usen los mismos colores.
 */
public final class ColorEstado {
    private ColorEstado() {}

    public static final String NUEVO = "#8A8F98";        // gris
    public static final String LISTO = "#2FA84F";        // verde
    public static final String EJECUTANDO = "#E8457C";   // rosa
    public static final String BLOQUEADO = "#F28C28";    // naranja
    public static final String TERMINADO = "#7B1E3A";    // guinda

    /** Color en hexadecimal (#RRGGBB) del estado. */
    public static String de(Estado e) {
        if (e == null) throw new IllegalArgumentException("Falta el estado");
        return switch (e) {
            case NUEVO -> NUEVO;
            case LISTO -> LISTO;
            case EJECUTANDO -> EJECUTANDO;
            case BLOQUEADO -> BLOQUEADO;
            case TERMINADO -> TERMINADO;
        };
    }
}
