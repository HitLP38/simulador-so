package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.es.AccionTeclado;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.SolicitudES;
import pe.uni.so.es.SolicitudES.Operacion;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.motor.Kernel;
import pe.uni.so.motor.Momento;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.Prioridad;
import pe.uni.so.procesos.CampoPcb;
import pe.uni.so.procesos.CodigoError;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

class TecladoTest {

    /** P1 lee del teclado desde t = 1 con un plazo de {@code plazo} ticks; P2 sigue con la CPU. */
    private static Kernel leyendo(Kernel k, int plazo) {
        k.paso();                                                                   // P1 ejecuta en t = 0
        k.llamadaSistema(new SolicitudES(1, Dispositivo.TECLADO, Operacion.LEER, 0x200, 1, plazo));
        k.paso();                                                                   // P2 ejecuta en t = 1
        return k;
    }

    @Test @DisplayName("[E9] Enter: la lectura termina antes del plazo y el proceso continúa normalmente")
    void enterContinua() {
        Pcb p1 = new Pcb(1, 0, 2), p2 = new Pcb(2, 0, 5);
        Kernel k = leyendo(new Kernel(new Fcfs(), List.of(p1, p2)), 10);
        assertTrue(k.teclado('\n'));                      // t = 2
        k.ejecutarTodo();

        assertEquals(2, k.eventos(Momento.C).get(0).t()); // IRQ1 en t = 2, no en t = 11
        assertNull(p1.error());
        assertEquals(7, p1.fin());                        // espera a P2 (FCFS) y ejecuta su última instrucción
        assertEquals(2, p1.cpu());
    }

    @Test @DisplayName("[E9][P9] C: el proceso vuelve a Listo y, al recibir la CPU, termina con E04 sin ejecutar más")
    void cancelaConE04() {
        Pcb p1 = new Pcb(1, 0, 2, 1), p2 = new Pcb(2, 0, 5, 3);
        Kernel k = leyendo(new Kernel(new Prioridad(true), List.of(p1, p2)), 10);
        assertTrue(k.teclado('c'));                       // t = 2
        k.ejecutarTodo();

        assertEquals(Estado.TERMINADO, p1.estado());
        assertEquals(CodigoError.E04, p1.error());
        assertEquals(2, p1.fin());                        // expropia a P2 y termina en el mismo instante
        assertEquals(1, p1.cpu());                        // no ejecutó su segunda instrucción
        assertEquals("1 2 2 2 2 2", k.gantt());           // la entrega de la señal no consume un tick
        assertTrue(k.eventos(Momento.E).get(0).detalle().contains("E04"));
        assertEquals(4 << 16 | 2, p1.leer(CampoPcb.ERROR_E_INTERRUPCIONES));   // E04 y 2 interrupciones
    }

    @Test @DisplayName("[E9] Sin respuesta dentro del plazo, la lectura termina sola y el proceso continúa")
    void sinRespuesta() {
        Pcb p1 = new Pcb(1, 0, 2), p2 = new Pcb(2, 0, 5);
        Kernel k = leyendo(new Kernel(new Fcfs(), List.of(p1, p2)), 3);
        k.ejecutarTodo();
        assertEquals(4, k.eventos(Momento.C).get(0).t()); // 1 + 3
        assertNull(p1.error());
        assertEquals(Estado.TERMINADO, p1.estado());
    }

    @Test @DisplayName("[E9] Una C que llega después del plazo es tardía: no cancela y el proceso continúa")
    void teclaTardia() {
        Pcb p1 = new Pcb(1, 0, 2), p2 = new Pcb(2, 0, 5);
        Kernel k = leyendo(new Kernel(new Fcfs(), List.of(p1, p2)), 1);   // la lectura vence al final de t = 1
        assertFalse(k.teclado('C'));                                         // t = 2: la IRQ ya está pendiente
        k.ejecutarTodo();
        assertNull(p1.error());
        assertEquals(2, p1.cpu());
    }

    @Test @DisplayName("[E9] La señal solo vale para una lectura de teclado registrada (no escritura ni pedido manual)")
    void soloLecturaActiva() {
        Pcb p1 = new Pcb(1, 0, 2), p2 = new Pcb(2, 0, 5);
        Kernel k = new Kernel(new Fcfs(), List.of(p1, p2));
        k.paso();
        k.llamadaSistema(new SolicitudES(1, Dispositivo.TECLADO, Operacion.ESCRIBIR, 0x200, 1, 4));
        assertFalse(k.teclado('\n'));
        assertFalse(p1.cancelacionPendiente());

        Kernel manual = new Kernel(new Fcfs(), List.of(new Pcb(3, 0, 2), new Pcb(4, 0, 2)));
        manual.paso();
        manual.pedirES(Dispositivo.TECLADO);                                 // atajo sin bloque de petición
        assertFalse(manual.teclado('C'));
    }

    @Test @DisplayName("[E9] Una tecla sin nadie esperando se ignora; solo C y Enter son señales válidas")
    void teclasValidas() {
        Kernel k = new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 2)));
        assertFalse(k.teclado('C'));
        assertEquals(AccionTeclado.CANCELAR, AccionTeclado.de('c'));
        assertEquals(AccionTeclado.CONTINUAR, AccionTeclado.de('\r'));
        assertThrows(ParametroInvalidoException.class, () -> AccionTeclado.de('x'));
    }

    @Test @DisplayName("[P9] Los 4 códigos de terminación por error tienen número y descripción")
    void codigosDeError() {
        assertEquals(4, CodigoError.values().length);
        assertEquals(4, CodigoError.E04.numero);
        assertEquals("Cancelado por el usuario", CodigoError.E04.descripcion);
    }
}
