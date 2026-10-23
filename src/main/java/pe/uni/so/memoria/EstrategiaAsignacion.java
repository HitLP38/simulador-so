package pe.uni.so.memoria;

import java.util.List;

/**
 * [M6] Strategy para primer, mejor y peor ajuste. Trabaja sobre huecos =
 * rachas de marcos libres consecutivos del mapa de bits.
 */
public interface EstrategiaAsignacion {
    /** Índice elegido (-1 si ninguno alcanza) y cuántos huecos se revisaron (costo de búsqueda). */
    record Eleccion(int indice, int comparaciones) {}

    String nombre();

    Eleccion elegir(List<Integer> huecos, int pedido);

    static EstrategiaAsignacion primerAjuste() {
        return new EstrategiaAsignacion() {
            public String nombre() { return "Primer ajuste"; }
            public Eleccion elegir(List<Integer> h, int pedido) {
                for (int i = 0; i < h.size(); i++) if (h.get(i) >= pedido) return new Eleccion(i, i + 1);
                return new Eleccion(-1, h.size());
            }
        };
    }

    static EstrategiaAsignacion mejorAjuste() { return recorrerTodo("Mejor ajuste", true); }

    static EstrategiaAsignacion peorAjuste() { return recorrerTodo("Peor ajuste", false); }

    /** Mejor y peor ajuste revisan todos los huecos; empate = el primero encontrado. */
    private static EstrategiaAsignacion recorrerTodo(String nombre, boolean menor) {
        return new EstrategiaAsignacion() {
            public String nombre() { return nombre; }
            public Eleccion elegir(List<Integer> h, int pedido) {
                int elegido = -1;
                for (int i = 0; i < h.size(); i++) {
                    if (h.get(i) < pedido) continue;
                    if (elegido < 0 || (menor ? h.get(i) < h.get(elegido) : h.get(i) > h.get(elegido))) elegido = i;
                }
                return new Eleccion(elegido, h.size());
            }
        };
    }
}
