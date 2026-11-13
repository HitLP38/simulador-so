package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.procesos.CreadorProcesos;
import pe.uni.so.sesion.SesionSimulacion;

class SesionSimulacionTest {

    private static SesionSimulacion lote() { return new SesionSimulacion(Maquina.porDefecto()); }

    @Test @DisplayName("[G5][P16] La sesión abre en t = 0 con los 20 procesos del lote oficial en Nuevo")
    void abreEnCero() {
        SesionSimulacion s = lote();
        assertEquals(0, s.vista().t());
        assertEquals(0, s.tActual());
        assertTrue(s.enVivo());
        assertTrue(s.puedeAvanzar());
        assertEquals(20, s.vista().procesos().size());
        assertEquals(0, s.primerTickEnHistorial());
    }

    @Test @DisplayName("[P16] Avanzar simula ticks nuevos; retroceder mira el historial sin deshacer la simulación")
    void avanzarYRetroceder() {
        SesionSimulacion s = lote();
        assertEquals(10, s.avanzar(10));
        assertEquals(10, s.tActual());
        s.retroceder(4);
        assertEquals(6, s.vista().t());
        assertEquals(10, s.tActual());                                 // el Kernel no retrocede
        assertFalse(s.enVivo());
        assertEquals(3, s.avanzar(3));                                 // recorre fotos ya tomadas
        assertEquals(9, s.vista().t());
        assertEquals(10, s.tActual());
        assertEquals(3, s.avanzar(3));                                 // 1 del historial + 2 nuevos
        assertEquals(12, s.tActual());
        assertTrue(s.enVivo());
        s.retroceder(100);
        assertEquals(0, s.vista().t());                                // no pasa de t = 0
        assertThrows(ParametroInvalidoException.class, () -> s.avanzar(0));
        assertThrows(ParametroInvalidoException.class, () -> s.irA(-1));
    }

    @Test @DisplayName("[P16] Ir a un tick: del historial si ya pasó y simulando si es futuro; la foto es la del Kernel")
    void irATick() {
        SesionSimulacion s = lote();
        s.irA(50);
        assertEquals(50, s.tActual());
        assertTrue(s.enVivo());
        Instantanea en50 = s.vista();
        s.irA(20);
        assertEquals(20, s.vista().t());
        s.irA(50);
        assertSame(en50, s.vista());                                   // la misma foto, no una copia nueva
    }

    @Test @DisplayName("[G5][P16] Hasta el final: el lote oficial termina en la máquina por defecto y ya no se puede avanzar")
    void hastaElFinal() {
        SesionSimulacion s = lote();
        s.hastaElFinal();
        assertTrue(s.terminada());
        assertFalse(s.atascada());
        assertTrue(s.vista().terminada());
        assertFalse(s.puedeAvanzar());
        int fin = s.tActual();
        assertEquals(0, s.avanzar(5));
        assertEquals(fin, s.tActual());
        s.retroceder(1);
        assertTrue(s.puedeAvanzar());                                  // desde el pasado sí se avanza
    }

    @Test @DisplayName("[P16] Un tick más antiguo que el historial se vuelve a simular y da la misma foto (determinista)")
    void reSimulaFueraDelHistorial() {
        SesionSimulacion referencia = lote();
        referencia.irA(30);
        Instantanea esperada = referencia.vista();

        SesionSimulacion s = lote();
        s.limitarHistorial(50);
        s.irA(200);
        assertEquals(151, s.primerTickEnHistorial());                  // solo quedan las 50 últimas fotos
        s.irA(30);                                                     // fuera del historial: vuelve a simular
        assertEquals(30, s.vista().t());
        assertEquals(30, s.tActual());
        assertEquals(esperada, s.vista());                              // record: mismos valores
        assertThrows(ParametroInvalidoException.class, () -> s.limitarHistorial(0));
    }

    @Test @DisplayName("[G5] Reiniciar vuelve a t = 0 con una carga nueva igual a la original")
    void reiniciar() {
        SesionSimulacion s = lote();
        Instantanea inicial = s.vista();
        s.avanzar(40);
        s.reiniciar();
        assertEquals(0, s.tActual());
        assertEquals(inicial, s.vista());
        s.avanzar(40);
        assertEquals(40, s.tActual());
    }

    @Test @DisplayName("[G5] Los oyentes reciben la foto mirada cada vez que cambia")
    void oyentes() {
        SesionSimulacion s = lote();
        List<Integer> vistos = new ArrayList<>();
        s.alCambiar(f -> vistos.add(f.t()));
        s.avanzar(3);
        s.retroceder(1);
        s.irA(10);
        s.reiniciar();
        assertEquals(List.of(3, 2, 10, 0), vistos);                    // un aviso por acción, no por tick
        assertThrows(ParametroInvalidoException.class, () -> s.alCambiar(null));
    }

    @Test @DisplayName("[G5] Una carga que no cabe en la máquina no abre la sesión")
    void cargaInvalida() {
        Configuracion d = Configuracion.porDefecto();
        Configuracion paginasGrandes = new Configuracion(d.ram(), Configuracion.Pagina.MIB_2, d.cpu(), d.politica(), d.quantum(),
                d.costoDespacho(), d.grado(), d.estrategia(), d.carga(), d.reemplazo(), d.dispositivos(), d.semilla());
        Maquina m = new Maquina("Otra", paginasGrandes);
        assertThrows(ParametroInvalidoException.class,
                () -> new SesionSimulacion(m, () -> CreadorProcesos.loteOficial(d.pagina().bytes)));   // páginas de 64 KiB
        assertEquals(20, new SesionSimulacion(m).vista().procesos().size());   // con su propio tamaño sí
    }

    @Test @DisplayName("[P16] Si la foto mirada sale del historial al acortarlo, se muestra la última y se avisa a los oyentes")
    void limitarHistorialAvisa() {
        SesionSimulacion s = lote();
        s.irA(10);
        s.irA(0);
        List<Integer> vistos = new ArrayList<>();
        s.alCambiar(f -> vistos.add(f.t()));
        s.limitarHistorial(1);                                         // t = 0 ya no está: queda t = 10
        assertEquals(10, s.vista().t());
        assertEquals(List.of(10), vistos);
        s.limitarHistorial(5);                                         // la foto sigue: no hay aviso
        assertEquals(List.of(10), vistos);
    }

    @Test @DisplayName("[P16] El máximo alcanzado no baja al volver a simular un tick antiguo; reiniciar lo vuelve a 0")
    void maximoAlcanzado() {
        SesionSimulacion s = lote();
        s.limitarHistorial(50);
        s.irA(200);
        assertEquals(200, s.maximoAlcanzado());
        s.irA(30);                                                     // re-simula: el Kernel queda en 30
        assertEquals(30, s.tActual());
        assertEquals(200, s.maximoAlcanzado());                        // la línea de tiempo sigue llegando a 200
        s.irA(150);
        assertEquals(150, s.vista().t());
        assertEquals(200, s.maximoAlcanzado());
        s.reiniciar();
        assertEquals(0, s.maximoAlcanzado());
    }

    @Test @DisplayName("[G5][P5] Con grado 1, el segundo proceso entra al liberarse la plaza: la sesión no se declara atascada antes")
    void sinAtascoAlLiberarElGrado() {
        Configuracion d = Configuracion.porDefecto();
        Configuracion grado1 = new Configuracion(d.ram(), d.pagina(), d.cpu(), Configuracion.Politica.FCFS, d.quantum(), d.costoDespacho(), 1,
                d.estrategia(), d.carga(), d.reemplazo(), d.dispositivos(), d.semilla());
        SesionSimulacion s = new SesionSimulacion(new Maquina("Grado 1", grado1), () -> {
            CreadorProcesos c = new CreadorProcesos(7, d.pagina().bytes);
            return List.of(c.manual(0, 12, 200 * 1024L), c.manual(0, 12, 200 * 1024L));
        });
        s.hastaElFinal();
        assertTrue(s.terminada(), "los dos procesos terminan");
        assertFalse(s.atascada());
    }
}
