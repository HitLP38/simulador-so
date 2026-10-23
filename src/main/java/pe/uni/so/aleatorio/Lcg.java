package pe.uni.so.aleatorio;

/**
 * Generador congruencial lineal (ecuación 1 del informe):
 * X(n+1) = (1664525 X(n) + 1013904223) mod 2^32,  U = X / 2^32.
 * Misma semilla => mismos resultados (reproducibilidad).
 */
public class Lcg {
    private long x;

    public Lcg(long semilla) { x = semilla & 0xFFFFFFFFL; }

    public double uniforme() {
        x = (1664525L * x + 1013904223L) & 0xFFFFFFFFL;
        return x / 4294967296.0;
    }

    /** Entero uniforme en [a, b]. */
    public int entero(int a, int b) { return a + (int) Math.floor(uniforme() * (b - a + 1)); }
}
