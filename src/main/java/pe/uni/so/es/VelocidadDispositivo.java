package pe.uni.so.es;

import pe.uni.so.excepciones.ParametroInvalidoException;

/**
 * [G5] Velocidad de un dispositivo de la máquina. Escala la duración de cada pedido de E/S
 * (la que dan la solicitud o la ecuación 4, de 5 a 20 ticks): un disco lento tarda el doble y
 * uno rápido la mitad, redondeando hacia arriba y nunca por debajo de 1 tick.
 */
public enum VelocidadDispositivo {
    LENTO("Lento", 2.0),
    NORMAL("Normal", 1.0),
    RAPIDO("Rápido", 0.5);

    public final String etiqueta;
    public final double factor;

    VelocidadDispositivo(String etiqueta, double factor) {
        this.etiqueta = etiqueta;
        this.factor = factor;
    }

    /** Duración en ticks de un pedido que, a velocidad normal, tardaría {@code duracion} ticks. */
    public int ajustar(int duracion) {
        if (duracion < 1) throw new ParametroInvalidoException("La duración de una E/S debe ser ≥ 1 tick (" + duracion + ")");
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, Math.ceil(duracion * factor)));
    }

    @Override public String toString() { return etiqueta; }
}
