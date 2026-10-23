package pe.uni.so.procesos;

import java.util.Set;

/**
 * [P1] Modelo de 5 estados (Sesión 4). Cada estado declara a cuáles puede pasar:
 * las seis transiciones válidas viven aquí y en ningún otro lugar.
 */
public enum Estado {
    NUEVO, LISTO, EJECUTANDO, BLOQUEADO, TERMINADO;

    public Set<Estado> siguientes() {
        return switch (this) {
            case NUEVO -> Set.of(LISTO);                             // admisión (largo plazo)
            case LISTO -> Set.of(EJECUTANDO);                        // despacho
            case EJECUTANDO -> Set.of(LISTO, BLOQUEADO, TERMINADO);  // expropiación, E/S, fin
            case BLOQUEADO -> Set.of(LISTO);                         // fin de E/S (nunca directo a CPU)
            case TERMINADO -> Set.of();
        };
    }

    public boolean puedePasarA(Estado destino) { return siguientes().contains(destino); }
}
