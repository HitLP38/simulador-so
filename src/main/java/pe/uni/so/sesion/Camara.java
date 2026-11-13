package pe.uni.so.sesion;

/**
 * [G5] Cámara del lienzo, al estilo de AutoCAD: zoom centrado en el cursor, arrastrar para
 * mover y ajustar el dibujo a la ventana. Convierte entre coordenadas del mundo (las del
 * diagrama) y de la pantalla: {@code pantalla = mundo · escala + desplazamiento}. No depende de
 * JavaFX, así se prueba sin abrir ventanas.
 */
public class Camara {

    public static final double ESCALA_MINIMA = 0.2, ESCALA_MAXIMA = 5.0;
    /** Factor de cada paso de zoom (rueda del ratón o botones + / −). */
    public static final double PASO_ZOOM = 1.15;

    private double escala = 1, x, y;

    public double escala() { return escala; }
    public double x() { return x; }
    public double y() { return y; }

    /**
     * Encaja un contenido de {@code contAncho × contAlto} en una vista de {@code vistaAncho ×
     * vistaAlto}, centrado y con {@code margen} píxeles libres alrededor. No hace nada si alguna
     * medida no es positiva (por ejemplo, mientras la ventana todavía no tiene tamaño).
     */
    public void ajustar(double contAncho, double contAlto, double vistaAncho, double vistaAlto, double margen) {
        double anchoUtil = vistaAncho - 2 * margen, altoUtil = vistaAlto - 2 * margen;
        if (contAncho <= 0 || contAlto <= 0 || anchoUtil <= 0 || altoUtil <= 0) return;
        escala = limitar(Math.min(anchoUtil / contAncho, altoUtil / contAlto));
        x = (vistaAncho - contAncho * escala) / 2;
        y = (vistaAlto - contAlto * escala) / 2;
    }

    /** Multiplica la escala por {@code factor} dejando fijo el punto de pantalla (px, py). */
    public void zoom(double factor, double px, double py) {
        if (!(factor > 0)) return;
        double mx = aMundoX(px), my = aMundoY(py);
        escala = limitar(escala * factor);
        x = px - mx * escala;
        y = py - my * escala;
    }

    /** Desplaza el dibujo {@code dx, dy} píxeles de pantalla (arrastrar). */
    public void mover(double dx, double dy) {
        x += dx;
        y += dy;
    }

    public double aMundoX(double px) { return (px - x) / escala; }
    public double aMundoY(double py) { return (py - y) / escala; }
    public double aPantallaX(double mx) { return mx * escala + x; }
    public double aPantallaY(double my) { return my * escala + y; }

    /** Zoom en porcentaje, para la barra de estado. */
    public int porcentaje() { return (int) Math.round(escala * 100); }

    private static double limitar(double e) { return Math.max(ESCALA_MINIMA, Math.min(ESCALA_MAXIMA, e)); }
}
