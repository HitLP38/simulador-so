package pe.uni.so.motor;

/**
 * [P16] Patrón Observer: la interfaz se suscribe al Kernel y recibe una {@link Instantanea} al
 * cerrar cada tick. El Kernel no conoce a las vistas; solo avisa a quien se haya suscrito.
 */
@FunctionalInterface
public interface ObservadorTick {
    void alCerrarTick(Instantanea foto);
}
