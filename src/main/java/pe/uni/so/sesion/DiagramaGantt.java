package pe.uni.so.sesion;

import java.util.ArrayList;
import java.util.List;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.procesos.Estado;

/**
 * [P16] Lo que dibuja el diagrama de Gantt en el instante mirado: una fila por proceso con sus
 * tramos de estado, dentro de una ventana de ticks que termina en ese instante. Sin JavaFX: la UI
 * solo convierte ticks y filas a píxeles.
 *
 * @param desde primer tick visible
 * @param hasta tick siguiente al último visible (el instante mirado + 1)
 * @param filas procesos visibles, en orden de PID
 * @param ocultos procesos que el filtro dejó fuera
 */
public record DiagramaGantt(int desde, int hasta, List<Fila> filas, int ocultos) {

    /** Qué procesos muestra el Gantt. */
    public enum Filtro {
        TODOS("Todos"),
        EN_CURSO("En curso"),          // ya llegaron y aún no terminan
        TERMINADOS("Terminados");

        public final String etiqueta;

        Filtro(String etiqueta) { this.etiqueta = etiqueta; }

        @Override public String toString() { return etiqueta; }
    }

    /** Una barra del Gantt (recortada a la ventana). */
    public record Barra(Estado estado, int desde, int hasta) {}

    /** Fila de un proceso: sus barras y si entró por «Ingresado por usuario». */
    public record Fila(int pid, boolean usuario, Estado estado, List<Barra> barras) {
        public Fila { barras = List.copyOf(barras); }
    }

    public DiagramaGantt { filas = List.copyOf(filas); }

    public int ancho() { return hasta - desde; }

    /**
     * Arma el Gantt de la foto mirada.
     *
     * @param ventana cuántos ticks mostrar hacia atrás (≤ 0: toda la corrida)
     */
    public static DiagramaGantt de(RegistroProcesos registro, Instantanea foto, int ventana, Filtro filtro) {
        int t = foto.t();
        int hasta = t + 1;
        int desde = ventana <= 0 ? 0 : Math.max(0, hasta - ventana);
        List<Fila> filas = new ArrayList<>();
        int ocultos = 0;
        List<Instantanea.Proceso> orden = foto.procesos().stream()
                .sorted(java.util.Comparator.comparingInt(Instantanea.Proceso::pid)).toList();
        for (Instantanea.Proceso p : orden) {
            boolean llego = p.llegada() <= t, termino = p.estado() == Estado.TERMINADO;
            boolean entra = switch (filtro) {
                case TODOS -> llego;
                case EN_CURSO -> llego && !termino;
                case TERMINADOS -> termino;
            };
            if (!entra) { ocultos++; continue; }
            List<Barra> barras = new ArrayList<>();
            for (RegistroProcesos.Tramo x : registro.tramos(p.pid(), t)) {
                int a = Math.max(x.desde(), desde), b = Math.min(x.hasta(), hasta);
                if (a < b) barras.add(new Barra(x.estado(), a, b));
            }
            filas.add(new Fila(p.pid(), p.usuario(), p.estado(), barras));
        }
        return new DiagramaGantt(desde, hasta, filas, ocultos);
    }
}
