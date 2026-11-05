package pe.uni.so.memoria;

/** [M7] Algoritmo de reemplazo local de páginas, a elección en la configuración (Sesión 8). */
public enum Reemplazo {
    /** Sale la página que lleva más tiempo cargada. Puede sufrir la anomalía de Belady. */
    FIFO,
    /** Sale la página usada hace más tiempo. No sufre la anomalía de Belady. */
    LRU
}
