package pe.uni.so.procesos;

import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.SolicitudES.Operacion;
import pe.uni.so.excepciones.ParametroInvalidoException;

/**
 * [E2][E10][P11] Un pedido de E/S planificado dentro de la ráfaga del proceso: cuando el proceso
 * lleva {@code instante} instrucciones ejecutadas, hace la llamada al sistema 0x80 a ese dispositivo.
 *
 * @param instante    instrucciones ejecutadas al momento del pedido (1 … burst − 1, ecuación 5)
 * @param dispositivo destino (ecuación 6)
 * @param operacion   leer o escribir
 * @param duracion    ticks de servicio a velocidad normal (ecuación 4)
 */
public record PedidoES(int instante, Dispositivo dispositivo, Operacion operacion, int duracion) {
    public PedidoES {
        if (instante < 1) throw new ParametroInvalidoException("Un pedido de E/S ocurre tras al menos 1 instrucción (" + instante + ")");
        if (dispositivo == null || operacion == null) throw new ParametroInvalidoException("Falta el dispositivo o la operación del pedido de E/S");
        if (duracion < 1) throw new ParametroInvalidoException("La duración de una E/S debe ser ≥ 1 tick (" + duracion + ")");
    }
}
