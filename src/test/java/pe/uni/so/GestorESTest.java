package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.SolicitudES;
import pe.uni.so.es.SolicitudES.Operacion;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.interrupciones.TipoInterrupcion;
import pe.uni.so.motor.Kernel;
import pe.uni.so.motor.Momento;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.RoundRobin;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

class GestorESTest {

    private static SolicitudES disco(int pid, int duracion) {
        return new SolicitudES(pid, Dispositivo.DISCO, Operacion.LEER, 0x8000, 512, duracion);
    }

    private static Kernel pasos(Kernel k, int n) {
        for (int i = 0; i < n; i++) k.paso();
        return k;
    }

    @Test @DisplayName("[E5][E6][E8] Taller p.2 ej. 4: estados y tiempos con una lectura de disco de 5 a 9")
    void ej4() {
        Pcb p = new Pcb(1, 0, 7), q = new Pcb(2, 0, 2), r = new Pcb(3, 9, 4);
        Kernel k = pasos(new Kernel(new RoundRobin(2), List.of(q, p, r)), 5);   // Q 0–2, P 2–5
        k.llamadaSistema(disco(1, 4));                                             // P pide el disco en t = 5
        k.ejecutarTodo();

        assertEquals("2 2 1 1 1 - - - - 3 3 1 1 3 3 1 1", k.gantt());
        assertEquals(7, p.cpu());                         // b) CPU = 3 + 2 + 2
        assertEquals(4, p.bloqueado());                   //    bloqueado 5–9
        assertEquals(6, p.espera());                      //    espera = 2 + 2 + 2
        assertEquals(17, p.retorno());
        assertEquals(2, p.respuesta());
        assertEquals(p.retorno(), p.cpu() + p.bloqueado() + p.espera());   // c)
        assertEquals(9, k.eventos(Momento.C).get(0).t()); // d) desde 9 espera CPU, no E/S
    }

    @Test @DisplayName("[E5] El disco atiende su cola FIFO de a un pedido: el segundo empieza cuando termina el primero")
    void colaFifoUnoALaVez() {
        Pcb a = new Pcb(1, 0, 3), b = new Pcb(2, 0, 3);
        Kernel k = new Kernel(new Fcfs(), List.of(a, b));
        k.paso();
        k.llamadaSistema(disco(1, 3));                    // A ocupa el disco de 1 a 4
        k.paso();
        k.llamadaSistema(disco(2, 2));                    // B espera detrás de A
        assertEquals(List.of(a, b), k.colas().cola(Dispositivo.DISCO));
        k.ejecutarTodo();

        List<Kernel.Evento> fin = k.eventos(Momento.C);
        assertEquals(a, fin.get(0).proceso());
        assertEquals(4, fin.get(0).t());                  // 1 + 3
        assertEquals(b, fin.get(1).proceso());
        assertEquals(6, fin.get(1).t());                  // empieza en 4, dura 2
        assertEquals(2, k.pic().atendidas(TipoInterrupcion.DISCO));
    }

    @Test @DisplayName("[E1][E6] Dispositivos distintos trabajan en paralelo; al terminar a la vez, el PIC atiende primero la IRQ más prioritaria")
    void dispositivosEnParalelo() {
        Pcb a = new Pcb(1, 0, 3), b = new Pcb(2, 0, 3);
        Kernel k = new Kernel(new Fcfs(), List.of(a, b));
        k.paso();
        k.llamadaSistema(disco(1, 2));                    // disco (IRQ14) de 1 a 3
        k.paso();
        k.llamadaSistema(new SolicitudES(2, Dispositivo.TECLADO, Operacion.LEER, 0x100, 1, 1));   // teclado (IRQ1) de 2 a 3
        k.paso();                                          // t = 2: CPU ociosa; ambos dispositivos terminan
        k.paso();                                          // t = 3: el PIC entrega las dos IRQ

        assertEquals(b, k.colas().enCpu());                // volvió primero, FCFS lo despacha
        assertEquals(List.of(a), k.colas().listos());
        List<Kernel.Evento> fin = k.eventos(Momento.C);
        assertEquals(b, fin.get(0).proceso());             // IRQ1 antes que IRQ14
        assertEquals(a, fin.get(1).proceso());
        assertTrue(fin.get(0).detalle().startsWith("IRQ 1"));
    }

    @Test @DisplayName("[E8][P2] Al terminar la E/S el proceso pasa a Listo (no a Ejecutando); con la IRQ enmascarada sigue bloqueado")
    void finDeEsYMascara() {
        Pcb a = new Pcb(1, 0, 4), b = new Pcb(2, 0, 6);
        Kernel k = new Kernel(new Fcfs(), List.of(a, b));
        k.paso();
        k.pic().enmascarar(TipoInterrupcion.DISCO);
        k.llamadaSistema(disco(1, 1));
        pasos(k, 3);                                       // el disco terminó en t = 2, pero la IRQ no entra
        assertEquals(Estado.BLOQUEADO, a.estado());
        assertTrue(k.pic().pendiente(TipoInterrupcion.DISCO));

        k.pic().desenmascarar(TipoInterrupcion.DISCO);
        k.paso();
        assertEquals(Estado.LISTO, a.estado());            // B sigue en la CPU (FCFS)
        assertEquals(b, k.colas().enCpu());
        assertEquals(0, a.pedidosES());
        assertEquals(2, a.interrupciones());
    }

    @Test @DisplayName("[E6][P2] Una IRQ adelantada (espuria) se descarta: el proceso sigue bloqueado hasta cumplir su duración")
    void irqEspuria() {
        Pcb a = new Pcb(1, 0, 2), b = new Pcb(2, 0, 9);
        Kernel k = new Kernel(new Fcfs(), List.of(a, b));
        k.paso();
        k.llamadaSistema(disco(1, 3));                    // disco de 1 a 4
        k.pic().levantar(TipoInterrupcion.DISCO);         // IRQ falsa en t = 1
        k.paso();
        assertEquals(Estado.BLOQUEADO, a.estado());
        assertEquals(1, k.pic().atendidas(TipoInterrupcion.DISCO));   // el PIC la entregó, pero no hubo fin de E/S
        k.ejecutarTodo();
        assertEquals(4, k.eventos(Momento.C).get(0).t());
    }

    @Test @DisplayName("[E6][P2] Si la única E/S pendiente tiene su IRQ enmascarada, ejecutarTodo avisa en vez de colgarse")
    void irqEnmascaradaNoCuelga() {
        Kernel k = new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 3)));
        k.paso();
        k.pic().enmascarar(TipoInterrupcion.DISCO);
        k.llamadaSistema(disco(1, 2));
        assertThrows(EventoInvalidoException.class, k::ejecutarTodo);   // el disco termina, pero su IRQ nunca entra
        assertTrue(k.pic().pendiente(TipoInterrupcion.DISCO));
    }
}
