package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.excepciones.TransicionInvalidaException;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

/** Capa REQUISITOS: modelo de 5 estados y PCB. */
class EstadosYPcbTest {

    @Test @DisplayName("[P1][E3][E8] Ciclo válido: nuevo, listo, ejecutando, bloqueado, listo, ejecutando, terminado")
    void cicloValido() {
        Pcb p = new Pcb(1, 0, 5);
        p.pasarA(Estado.LISTO); p.pasarA(Estado.EJECUTANDO); p.pasarA(Estado.BLOQUEADO);
        p.pasarA(Estado.LISTO); p.pasarA(Estado.EJECUTANDO); p.pasarA(Estado.TERMINADO);
        assertEquals(Estado.TERMINADO, p.estado());
    }

    @Test @DisplayName("[P1][E8] Taller ej. 5: terminar la E/S no entrega la CPU directamente")
    void bloqueadoNoPasaAEjecutando() {
        Pcb p = new Pcb(1, 0, 5);
        p.pasarA(Estado.LISTO); p.pasarA(Estado.EJECUTANDO); p.pasarA(Estado.BLOQUEADO);
        assertThrows(TransicionInvalidaException.class, () -> p.pasarA(Estado.EJECUTANDO));
    }

    @Test @DisplayName("[P1] Solo existen 6 transiciones válidas")
    void seisTransiciones() {
        int total = 0;
        for (Estado e : Estado.values()) total += e.siguientes().size();
        assertEquals(6, total);
    }

    @Test @DisplayName("[P3] Taller ej. 2: el PC avanza 4 bytes por instrucción")
    void contadorDePrograma() {
        Pcb p = new Pcb(120, 0, 5);
        for (int i = 0; i < 3; i++) p.ejecutarInstruccion();
        assertEquals(0xC, p.pc());
        assertEquals(2, p.restante());
    }
}
