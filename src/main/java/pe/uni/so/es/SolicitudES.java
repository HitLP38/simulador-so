package pe.uni.so.es;

import pe.uni.so.excepciones.ParametroInvalidoException;

/**
 * [E4] Bloque de petición que el SO entrega al controlador del dispositivo con la llamada
 * al sistema 0x80 (Sesión 9: registros de estado, control, data-in y data-out).
 *
 * @param pid        proceso que pide la operación
 * @param dispositivo dispositivo destino; su vector es el de retorno al terminar (IRQ)
 * @param operacion  lectura (dispositivo → búfer) o escritura (búfer → dispositivo)
 * @param bufer      dirección lógica del búfer del proceso
 * @param bytes      cantidad de bytes a transferir
 * @param duracion   ticks de servicio del dispositivo (E11)
 */
public record SolicitudES(int pid, Dispositivo dispositivo, Operacion operacion, int bufer, int bytes, int duracion) {

    public enum Operacion { LEER, ESCRIBIR }

    /** El SO valida antes de programar el controlador (taller parte 1, ej. 8 d). */
    public SolicitudES {
        if (dispositivo == null || operacion == null)
            throw new ParametroInvalidoException("P" + pid + ": la solicitud de E/S necesita dispositivo y operación");
        if (bufer < 0) throw new ParametroInvalidoException("P" + pid + ": búfer en dirección negativa (" + bufer + ")");
        if (bytes <= 0) throw new ParametroInvalidoException("P" + pid + ": cantidad de bytes debe ser > 0 (" + bytes + ")");
        if (duracion < 1) throw new ParametroInvalidoException("P" + pid + ": la duración debe ser >= 1 tick (" + duracion + ")");
    }

    /** Última dirección del búfer que ocupa la transferencia (destino contiguo). */
    public int finBufer() { return bufer + bytes - 1; }

    /** Vector con el que el dispositivo avisará el fin de la operación. */
    public int vectorRetorno() { return dispositivo.interrupcion.vector; }

    @Override public String toString() {
        return operacion + " " + bytes + " B en " + dispositivo + " (búfer 0x" + Integer.toHexString(bufer).toUpperCase()
                + ", " + duracion + " ticks, retorno 0x" + Integer.toHexString(vectorRetorno()).toUpperCase() + ")";
    }
}
