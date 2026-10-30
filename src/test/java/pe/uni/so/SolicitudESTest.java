package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.SolicitudES;
import pe.uni.so.es.SolicitudES.Operacion;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.motor.Kernel;
import pe.uni.so.motor.Modo;
import pe.uni.so.motor.Momento;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.procesos.CampoPcb;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

class SolicitudESTest {

    @Test @DisplayName("[E4] Taller p.1 ej. 8: leer 4 sectores de 512 B hacia el búfer 0x8000 ocupa hasta 0x87FF")
    void ej8BloqueDePeticion() {
        SolicitudES s = new SolicitudES(1, Dispositivo.DISCO, Operacion.LEER, 0x8000, 4 * 512, 6);
        assertEquals(2048, s.bytes());
        assertEquals(0x87FF, s.finBufer());
        assertEquals(0x2E, s.vectorRetorno());             // IRQ14 del disco
        assertTrue(s.toString().contains("LEER 2048 B en DISCO"));
    }

    @Test @DisplayName("[E4] El SO rechaza peticiones inválidas antes de programar el controlador")
    void peticionesInvalidas() {
        assertThrows(ParametroInvalidoException.class, () -> new SolicitudES(1, null, Operacion.LEER, 0, 8, 1));
        assertThrows(ParametroInvalidoException.class, () -> new SolicitudES(1, Dispositivo.DISCO, null, 0, 8, 1));
        assertThrows(ParametroInvalidoException.class, () -> new SolicitudES(1, Dispositivo.DISCO, Operacion.LEER, -4, 8, 1));
        assertThrows(ParametroInvalidoException.class, () -> new SolicitudES(1, Dispositivo.DISCO, Operacion.LEER, 0, 0, 1));
        assertThrows(ParametroInvalidoException.class, () -> new SolicitudES(1, Dispositivo.DISCO, Operacion.LEER, 0, 8, 0));
    }

    @Test @DisplayName("[E2][E3] La llamada 0x80 bloquea al proceso en la cola del dispositivo, en modo kernel, y deja la petición")
    void llamadaSistema() {
        Pcb p1 = new Pcb(1, 0, 5), p2 = new Pcb(2, 0, 3);
        Kernel k = new Kernel(new Fcfs(), List.of(p1, p2));
        k.paso();
        SolicitudES s = new SolicitudES(1, Dispositivo.IMPRESORA, Operacion.ESCRIBIR, 0x400, 128, 4);
        k.llamadaSistema(s);

        assertEquals(Estado.BLOQUEADO, p1.estado());
        assertEquals(List.of(p1), k.colas().cola(Dispositivo.IMPRESORA));
        assertEquals(Modo.KERNEL, k.modo());
        assertEquals(s, k.solicitud(p1));
        assertEquals(1, p1.leer(CampoPcb.PEDIDOS_ES));
        assertEquals(1, p1.leer(CampoPcb.ERROR_E_INTERRUPCIONES));
        assertEquals(Dispositivo.IMPRESORA.ordinal(), p1.leer(CampoPcb.DISPOSITIVO));
        assertTrue(k.eventos(Momento.A).get(0).detalle().contains("0x80"));

        k.paso();                                            // momento a: el planificador elige a P2
        assertEquals(p2, k.colas().enCpu());
        k.finES(Dispositivo.IMPRESORA);                      // fin de la E/S: la petición se cierra
        assertNull(k.solicitud(p1));
        assertEquals(0, p1.pedidosES());
        assertEquals(2, p1.interrupciones());                // la 0x80 y la IRQ de fin
    }

    @Test @DisplayName("[E2] Solo el proceso que ejecuta puede hacer la llamada al sistema")
    void soloElQueEjecuta() {
        Kernel k = new Kernel(new Fcfs(), List.of(new Pcb(1, 0, 5), new Pcb(2, 0, 3)));
        SolicitudES deP1 = new SolicitudES(1, Dispositivo.DISCO, Operacion.LEER, 0, 8, 2);
        assertThrows(EventoInvalidoException.class, () -> k.llamadaSistema(deP1));        // CPU libre
        k.paso();
        SolicitudES deP2 = new SolicitudES(2, Dispositivo.DISCO, Operacion.LEER, 0, 8, 2);
        assertThrows(EventoInvalidoException.class, () -> k.llamadaSistema(deP2));        // ejecuta P1
    }
}
