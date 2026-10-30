package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.planificacion.Fcfs;
import pe.uni.so.procesos.CampoPcb;
import pe.uni.so.procesos.Colas;
import pe.uni.so.procesos.Estado;
import pe.uni.so.procesos.Pcb;

class PcbTest {

    @Test @DisplayName("[P3] La PCB tiene 22 campos contiguos que caben en 128 bytes y el PC está en 0x10")
    void disposicion() {
        CampoPcb[] campos = CampoPcb.values();
        assertEquals(22, campos.length);
        assertEquals(0x00, campos[0].desplazamiento);
        for (int i = 1; i < campos.length; i++)               // sin huecos ni solapes
            assertEquals(campos[i - 1].desplazamiento + campos[i - 1].tamano(), campos[i].desplazamiento);
        CampoPcb ultimo = campos[campos.length - 1];
        assertTrue(ultimo.desplazamiento + ultimo.tamano() <= CampoPcb.TAMANO_PCB);
        assertEquals(0x10, CampoPcb.PC.desplazamiento);
        assertEquals(16, CampoPcb.REGISTROS.tamano());       // R1–R4, 4 bytes cada uno
    }

    @Test @DisplayName("[P3] La PCB de cada proceso está en la región del kernel: base + pid × 128")
    void direccionEnMemoria() {
        assertEquals(0x1000, CampoPcb.direccion(0x1000, 0));
        assertEquals(0x1180, CampoPcb.direccion(0x1000, 3));
        assertEquals(0x1180 + 0x10, CampoPcb.direccion(0x1000, 3) + CampoPcb.PC.desplazamiento);
    }

    @Test @DisplayName("[P3][P4] Los campos de la PCB reflejan el estado, el PC y el dispositivo esperado")
    void camposReflejanElProceso() {
        Colas c = new Colas();
        Pcb p = new Pcb(5, 2, 8, 3);
        c.admitir(p);
        c.despachar(new Fcfs());
        for (int i = 0; i < 3; i++) p.ejecutarInstruccion();

        assertEquals(5, p.leer(CampoPcb.PID));
        assertEquals(1, p.leer(CampoPcb.PPID));                // cuelga de init
        assertEquals(Estado.EJECUTANDO.ordinal(), p.leer(CampoPcb.ESTADO));
        assertEquals(0xC, p.leer(CampoPcb.PC));                 // 3 instrucciones × 4 bytes
        assertEquals(0x0008_0005, p.leer(CampoPcb.BURST));      // total 8, restante 5
        assertEquals(3, p.leer(CampoPcb.PRIORIDAD));
        assertEquals(2, p.leer(CampoPcb.LLEGADA));
        assertEquals(-1, p.leer(CampoPcb.DISPOSITIVO));

        c.bloquearEnCpu(Dispositivo.DISCO);
        assertEquals(Estado.BLOQUEADO.ordinal(), p.leer(CampoPcb.ESTADO));
        assertEquals(Dispositivo.DISCO.ordinal(), p.leer(CampoPcb.DISPOSITIVO));
        assertEquals(0xC, p.leer(CampoPcb.PC));                 // el PC guardado no avanza mientras espera
        c.terminarES(Dispositivo.DISCO);
        assertEquals(-1, p.leer(CampoPcb.DISPOSITIVO));
    }
}
