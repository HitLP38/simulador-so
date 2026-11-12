package pe.uni.so.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.FichaMaquina;

/** [G5] Ficha de la máquina en tarjetas por sección (Memoria, CPU, Dispositivos, Simulación). */
final class PanelFicha {

    private PanelFicha() {}

    static FlowPane crear(Configuracion c) {
        Map<String, GridPane> secciones = new LinkedHashMap<>();
        for (FichaMaquina.Fila f : FichaMaquina.filas(c)) {
            GridPane g = secciones.computeIfAbsent(f.seccion(), s -> {
                GridPane nueva = new GridPane();
                nueva.setHgap(12);
                nueva.setVgap(4);
                return nueva;
            });
            Label etiqueta = new Label(f.etiqueta());
            etiqueta.getStyleClass().add("etiqueta-campo");
            Label valor = new Label(f.valor());
            valor.getStyleClass().add("valor-campo");
            g.addRow(g.getRowCount(), etiqueta, valor);
        }
        FlowPane tarjetas = new FlowPane(12, 12);
        secciones.forEach((titulo, grilla) -> {
            Label t = new Label(titulo);
            t.getStyleClass().add("titulo-tarjeta");
            VBox tarjeta = new VBox(6, t, grilla);
            tarjeta.getStyleClass().add("tarjeta");
            tarjetas.getChildren().add(tarjeta);
        });
        return tarjetas;
    }

    static List<Label> mensajes(List<String> textos, String prefijo, String clase) {
        return textos.stream().map(t -> {
            Label l = new Label(prefijo + t);
            l.setWrapText(true);
            l.getStyleClass().add(clase);
            return l;
        }).toList();
    }
}
