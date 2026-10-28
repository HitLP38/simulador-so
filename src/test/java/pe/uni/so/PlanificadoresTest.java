package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.planificacion.PlanificadorLargoPlazo;
import pe.uni.so.planificacion.PlanificadorMedianoPlazo;
import pe.uni.so.procesos.Colas;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

class PlanificadoresTest {

    private static Pcb proceso(int pid, int memoria) { return new Pcb(pid, 0, 5, 0, memoria); }

    @Test @DisplayName("[P5] Taller ej. 16 a: con 800 MB usados de 1 024, D (400 MB) no se admite")
    void ej16a() {
        Colas c = new Colas();
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(1024);
        assertTrue(largo.admitir(proceso(1, 300), c));       // A
        assertTrue(largo.admitir(proceso(2, 300), c));       // B
        assertTrue(largo.admitir(proceso(3, 200), c));       // C
        Pcb d = proceso(4, 400);

        assertEquals(800, largo.usada());
        assertEquals(224, largo.libre());
        assertFalse(largo.admitir(d, c));
        assertEquals(Estado.NUEVO, d.estado());              // sigue sin admitir
        assertEquals(3, c.trabajos().size());
    }

    @Test @DisplayName("[P5] Taller ej. 16 b: suspender B libera 300 MB; con D admitido el uso es 900 y quedan 124")
    void ej16b() {
        Colas c = new Colas();
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(1024);
        PlanificadorMedianoPlazo mediano = new PlanificadorMedianoPlazo(largo);
        Pcb b = proceso(2, 300), d = proceso(4, 400);
        largo.admitir(proceso(1, 300), c);
        largo.admitir(b, c);
        largo.admitir(proceso(3, 200), c);

        mediano.suspender(b);
        assertEquals(524, largo.libre());
        assertTrue(largo.admitir(d, c));
        assertEquals(900, largo.usada());
        assertEquals(124, largo.libre());
        assertTrue(b.suspendido());
        assertEquals(Estado.LISTO, b.estado());              // B sigue existiendo: 5 estados, no 7
    }

    @Test @DisplayName("[P4][P5] Taller ej. 16 c: el corto plazo no elige a un proceso suspendido")
    void cortoPlazoIgnoraSuspendidos() {
        Colas c = new Colas();
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(1024);
        PlanificadorMedianoPlazo mediano = new PlanificadorMedianoPlazo(largo);
        Pcb a = proceso(1, 300), b = proceso(2, 300);
        largo.admitir(a, c);
        largo.admitir(b, c);

        mediano.suspender(a);                                 // A está primero en listos
        assertEquals(b, c.despachar(new Fcfs()));
    }

    @Test @DisplayName("[P5] Reanudar solo si vuelve a caber; luego el proceso compite de nuevo")
    void reanudar() {
        Colas c = new Colas();
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(1024);
        PlanificadorMedianoPlazo mediano = new PlanificadorMedianoPlazo(largo);
        Pcb a = proceso(1, 300), b = proceso(2, 300), d = proceso(4, 400), e = proceso(5, 300);
        largo.admitir(a, c);
        largo.admitir(b, c);
        largo.admitir(proceso(3, 200), c);
        mediano.suspender(b);
        largo.admitir(d, c);                                  // quedan 124

        assertFalse(mediano.reanudar(b));                     // 300 > 124
        assertTrue(b.suspendido());
        assertThrows(EventoInvalidoException.class, () -> largo.liberar(d));   // D aún puede ejecutar

        assertEquals(a, c.despachar(new Fcfs()));             // A termina: quedan 424
        c.terminarEnCpu();
        largo.liberar(a);
        assertTrue(mediano.reanudar(b));
        assertFalse(b.suspendido());
        assertEquals(900, largo.usada());                     // B 300 + C 200 + D 400
        assertFalse(largo.admitir(e, c));                     // 300 > 124
    }

    @Test @DisplayName("[P5] El grado de multiprogramación limita la admisión aunque sobre memoria")
    void gradoDeMultiprogramacion() {
        Colas c = new Colas();
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(1024, 2);
        assertTrue(largo.admitir(proceso(1, 100), c));
        assertTrue(largo.admitir(proceso(2, 100), c));
        assertFalse(largo.admitir(proceso(3, 100), c));
        assertEquals(2, largo.residentes());
    }

    @Test @DisplayName("[P1][P5] Solo se suspende un proceso Listo o Bloqueado y una sola vez")
    void suspensionValida() {
        Colas c = new Colas();
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(1024);
        PlanificadorMedianoPlazo mediano = new PlanificadorMedianoPlazo(largo);
        Pcb a = proceso(1, 100), b = proceso(2, 100);
        largo.admitir(a, c);
        largo.admitir(b, c);
        c.despachar(new Fcfs());                              // A en CPU

        assertThrows(EventoInvalidoException.class, () -> mediano.suspender(a));
        c.bloquearEnCpu(Dispositivo.DISCO);                   // A bloqueado: sí se puede
        mediano.suspender(a);
        assertThrows(EventoInvalidoException.class, () -> mediano.suspender(a));
        assertThrows(EventoInvalidoException.class, () -> largo.liberar(a));   // ya no está residente
        assertEquals(100, largo.usada());
    }

    @Test @DisplayName("[P5] Solo se suspende un proceso admitido por el planificador")
    void suspenderNoAdmitido() {
        Colas c = new Colas();
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(1024);
        PlanificadorMedianoPlazo mediano = new PlanificadorMedianoPlazo(largo);
        Pcb x = proceso(1, 100);
        c.admitir(x);                                         // entró a listos sin pasar por largo plazo
        assertThrows(EventoInvalidoException.class, () -> mediano.suspender(x));
        assertFalse(x.suspendido());
    }

    @Test @DisplayName("[P5] Una memoria negativa se rechaza al crear el proceso")
    void memoriaNegativa() {
        assertThrows(ParametroInvalidoException.class, () -> new Pcb(1, 0, 5, 0, -300));
    }

    @Test @DisplayName("[P5] Un proceso más grande que toda la memoria se rechaza")
    void nuncaCabe() {
        PlanificadorLargoPlazo largo = new PlanificadorLargoPlazo(1024);
        assertThrows(ParametroInvalidoException.class, () -> largo.admitir(proceso(1, 2048), new Colas()));
        assertThrows(ParametroInvalidoException.class, () -> new PlanificadorLargoPlazo(0));
    }
}
