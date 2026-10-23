package pe.uni.so.excepciones;

import pe.uni.so.procesos.Estado;

/** [P1] Se intentó una transición fuera de las seis del modelo de 5 estados. */
public class TransicionInvalidaException extends SimuladorException {
    public TransicionInvalidaException(int pid, Estado desde, Estado hacia) {
        super("P" + pid + ": transición inválida " + desde + " -> " + hacia);
    }
}
