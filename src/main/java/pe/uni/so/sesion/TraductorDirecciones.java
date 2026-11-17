package pe.uni.so.sesion;

import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.GestorMemoria;
import pe.uni.so.motor.Instantanea;

/**
 * [M3][M4][M1][P16] Traductor de direcciones de la vista Memoria: hace sobre la foto el mismo
 * recorrido que la MMU (segmento → página y desplazamiento → tabla de páginas → marco), sin
 * cambiar los bits R y M. Así se puede consultar cualquier instante de la línea de tiempo.
 */
public final class TraductorDirecciones {

    /** Cómo terminó la traducción. */
    public enum Tipo {
        TRADUCIDA("Dirección física"),
        FALLO_PAGINA("Fallo de página"),
        ACCESO_ILEGAL("Acceso ilegal"),
        NO_RESIDENTE("Proceso sin memoria asignada");

        public final String etiqueta;

        Tipo(String etiqueta) { this.etiqueta = etiqueta; }
    }

    /**
     * Resultado de traducir una dirección lógica. Los campos que no se llegaron a calcular quedan
     * en −1 (y el segmento en null).
     */
    public record Resultado(Tipo tipo, long direccion, EspacioProceso.Segmento segmento, long pagina, long desplazamiento,
                            int marco, long fisica, String detalle) {}

    private TraductorDirecciones() {}

    /** Traduce la dirección lógica {@code direccion} del proceso {@code pid} en la memoria de la foto. */
    public static Resultado traducir(Instantanea.Memoria m, int pid, long direccion) {
        Instantanea.MemoriaProceso mp = m.procesos().get(pid);
        if (mp == null)
            return new Resultado(Tipo.NO_RESIDENTE, direccion, null, -1, -1, -1, -1,
                    "P" + pid + " no está en memoria en este instante (aún no se admite, está suspendido o terminó)");
        EspacioProceso.Segmento seg = null;
        for (GestorMemoria.LimitesSegmento s : mp.segmentos())
            if (direccion >= s.base() && direccion < s.limite()) seg = s.segmento();
        long bloque = m.bloque();
        long pagina = direccion < 0 ? -1 : direccion / bloque, desp = direccion < 0 ? -1 : direccion % bloque;
        if (seg == null)
            return new Resultado(Tipo.ACCESO_ILEGAL, direccion, null, pagina, desp, -1, -1,
                    "La dirección cae fuera de los segmentos de P" + pid + " (en el hueco entre heap y pila o más allá del límite)");
        for (GestorMemoria.TramoPaginas t : mp.tabla()) {
            if (pagina < t.paginaInicial() || pagina > t.paginaFinal()) continue;
            if (!t.valida())
                return new Resultado(Tipo.FALLO_PAGINA, direccion, seg, pagina, desp, -1, -1,
                        "La página " + pagina + " tiene bit i: el SO la traería desde el swap a un marco"
                                + (m.bajoDemanda() ? " de la cuota (reemplazo " + m.reemplazo() + ")" : ""));
            int marco = t.marcoDe(pagina);
            long fisica = marco * bloque + desp;
            return new Resultado(Tipo.TRADUCIDA, direccion, seg, pagina, desp, marco, fisica,
                    "Marco " + marco + " × " + bloque + " B + " + desp + " = " + fisica);
        }
        return new Resultado(Tipo.ACCESO_ILEGAL, direccion, seg, pagina, desp, -1, -1,
                "La página " + pagina + " no existe todavía: está más allá del heap o de la pila actuales");
    }
}
