package pe.uni.so.motor;

/**
 * [P8] Bit de modo de la CPU. Es un registro del procesador, no un campo de la PCB:
 * pasa a KERNEL con cada interrupción o llamada al sistema, y el despachador lo devuelve
 * a USUARIO justo antes de saltar al PC del proceso (Sesión 6, «Despachador»).
 */
public enum Modo { USUARIO, KERNEL }
