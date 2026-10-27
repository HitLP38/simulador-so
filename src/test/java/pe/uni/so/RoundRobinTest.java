package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.planificacion.RoundRobin;

/** Capa REQUISITOS: el quantum de RR se ingresa al inicio y puede cambiarse. */
class RoundRobinTest {

    @Test @DisplayName("[P7] El quantum se puede cambiar en plena simulación")
    void cambiarQuantum() {
        RoundRobin rr = new RoundRobin(2);
        rr.setQuantum(5);
        assertEquals(5, rr.quantum());
        assertEquals("RR q=5", rr.nombre());
    }

    @Test @DisplayName("[P7] Un quantum menor que 1 se rechaza")
    void quantumInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new RoundRobin(0));
    }
}
