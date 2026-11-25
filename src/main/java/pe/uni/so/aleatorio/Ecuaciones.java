package pe.uni.so.aleatorio;

/** Ecuaciones 2 a 4 del informe [P11][P12][E10][E11]. LAMBDA = peso del proceso frente al azar. */
public final class Ecuaciones {
    public static final double LAMBDA = 0.6;

    private Ecuaciones() {}

    /** Ec. 2: peso del proceso en (0, 1]. */
    public static double peso(int burst, int burstMax, long tamano, long tamanoMax) {
        return 0.5 * burst / burstMax + 0.5 * (double) tamano / tamanoMax;
    }

    /** Ec. 3: pedidos de E/S en [5, 10]; cada uno genera 2 interrupciones => [10, 20]. */
    public static int pedidosES(double w, Lcg rng) {
        return 5 + (int) Math.round(5 * (LAMBDA * w + (1 - LAMBDA) * rng.uniforme()));
    }

    /** Ec. 4: duración de un servicio de E/S en [5, 20] unidades de tiempo. */
    public static int duracionES(double w, Lcg rng) {
        return 5 + (int) Math.round(15 * (LAMBDA * w + (1 - LAMBDA) * rng.uniforme()));
    }

    /** Ec. 7: cada cuántos procesos del acumulado falla exactamente uno (1/200 = 0,5 %). */
    public static final int BLOQUE_ERRORES = 200;

    /** Ec. 7: posición (desde 0) del proceso errado del bloque k del acumulado: eₖ = 200k + ⌊200U⌋. */
    public static int posicionErrado(int k, double u) {
        return BLOQUE_ERRORES * k + (int) Math.floor(BLOQUE_ERRORES * u);
    }

    /** Ec. 7: instrucción (1 a b − 1) en la que falla un proceso de ráfaga b: 1 + ⌊U(b − 1)⌋ (b = 1 ⇒ 1). */
    public static int instanteFallo(int burst, double u) {
        return 1 + (int) Math.floor(u * Math.max(0, burst - 1));
    }
}
