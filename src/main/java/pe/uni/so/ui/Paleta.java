package pe.uni.so.ui;

import javafx.scene.paint.Color;

/**
 * [G5] Colores del lienzo para cada tema. El oscuro usa el mismo azul de la plataforma (el de
 * {@code estilos.css}); el claro, grises fríos. Los colores de los 5 estados no cambian con el
 * tema: son los del modelo ({@link pe.uni.so.motor.ColorEstado}).
 */
record Paleta(Color fondoArriba, Color fondoAbajo, Color salaArriba, Color salaAbajo, Color baldosa1, Color baldosa2,
              Color pasillo1, Color pasillo2, Color bordePasillo, Color texto, Color tenue, Color apagado, Color carril,
              Color bordeCarril, Color fondoBadge, Color textoFicha, Color oro, Color fondoOro, Color grupo, Color sombra) {

    static final Paleta OSCURO = new Paleta(
            Color.web("#0f172a"), Color.web("#0b1120"), Color.web("#131d33"), Color.web("#0f1829"),
            Color.web("#142038"), Color.web("#111b30"), Color.web("#1a2740"), Color.web("#16223a"), Color.web("#2a3b5c"),
            Color.web("#e2e8f0"), Color.web("#94a3b8"), Color.web("#64748b"), Color.web("#0f1a2e"), Color.web("#2a3b5c"),
            Color.web("#0b1120"), Color.WHITE, Color.web("#ffc857"), Color.web("#33280f"), Color.web("#475569"),
            Color.web("#00000066"));

    static final Paleta CLARO = new Paleta(
            Color.web("#f1f5f9"), Color.web("#e2e8f0"), Color.web("#ffffff"), Color.web("#f6f8fb"),
            Color.web("#f3f6fa"), Color.web("#eaeff5"), Color.web("#e2e8f0"), Color.web("#d8e0eb"), Color.web("#c3cfdf"),
            Color.web("#0f172a"), Color.web("#475569"), Color.web("#94a3b8"), Color.web("#f1f5f9"), Color.web("#cbd5e1"),
            Color.WHITE, Color.web("#0f172a"), Color.web("#c98a00"), Color.web("#fff4d6"), Color.web("#94a3b8"),
            Color.web("#0f172a22"));

    static Paleta de(boolean claro) { return claro ? CLARO : OSCURO; }
}
