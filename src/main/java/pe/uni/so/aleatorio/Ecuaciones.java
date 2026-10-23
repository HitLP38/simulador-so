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
}
