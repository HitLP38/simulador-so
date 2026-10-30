package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.uni.so.interrupciones.TipoInterrupcion.*;

import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.interrupciones.ControladorInterrupciones;
import pe.uni.so.interrupciones.TipoInterrupcion;
import pe.uni.so.interrupciones.TipoInterrupcion.Clase;

class ControladorInterrupcionesTest {

    private static long contar(Clase c) { return Arrays.stream(TipoInterrupcion.values()).filter(t -> t.clase == c).count(); }

    @Test @DisplayName("[P2] EP 25-1 preg. 6 / EP 26-1 preg. 21: 11 vectores en IH, IS y EX; solo las IH son asíncronas")
    void clasificacion() {
        assertEquals(11, TipoInterrupcion.values().length);
        assertEquals(6, contar(Clase.HARDWARE));     // timer, teclado, disco, impresora, pantalla, red
        assertEquals(1, contar(Clase.SOFTWARE));     // llamada al sistema 0x80
        assertEquals(4, contar(Clase.EXCEPCION));    // división por cero, instrucción inválida, acceso ilegal, fallo de página
        assertEquals(Clase.EXCEPCION, DIVISION_CERO.clase);
        assertEquals(Clase.SOFTWARE, LLAMADA_SISTEMA.clase);
        assertEquals(0x80, LLAMADA_SISTEMA.vector);
        assertTrue(ControladorInterrupciones.esSincrona(FALLO_PAGINA));
        assertTrue(ControladorInterrupciones.esSincrona(LLAMADA_SISTEMA));
        assertFalse(ControladorInterrupciones.esSincrona(DISCO));
        assertEquals(0, ControladorInterrupciones.irq(TIMER));
        assertEquals(14, ControladorInterrupciones.irq(DISCO));
    }

    @Test @DisplayName("[P2] El PIC entrega primero la IRQ de mayor prioridad (menor número)")
    void prioridad() {
        ControladorInterrupciones pic = new ControladorInterrupciones();
        pic.levantar(DISCO);
        pic.levantar(TECLADO);
        pic.levantar(TIMER);
        assertEquals(Optional.of(TIMER), pic.reconocer());
        pic.finDeInterrupcion();
        assertEquals(Optional.of(TECLADO), pic.reconocer());
        pic.finDeInterrupcion();
        assertEquals(Optional.of(DISCO), pic.reconocer());
        pic.finDeInterrupcion();
        assertEquals(Optional.empty(), pic.reconocer());
    }

    @Test @DisplayName("[P2] Una IRQ enmascarada queda pendiente y se entrega al desenmascararla")
    void mascara() {
        ControladorInterrupciones pic = new ControladorInterrupciones();
        pic.enmascarar(TECLADO);
        pic.levantar(TECLADO);
        assertTrue(pic.pendiente(TECLADO));
        assertEquals(Optional.empty(), pic.siguiente());
        pic.desenmascarar(TECLADO);
        assertEquals(Optional.of(TECLADO), pic.reconocer());
    }

    @Test @DisplayName("[P2] Anidamiento: una IRQ de mayor prioridad interrumpe la rutina en curso; una de menor espera el EOI")
    void anidamiento() {
        ControladorInterrupciones pic = new ControladorInterrupciones();
        pic.levantar(TECLADO);
        pic.reconocer();                                     // atendiendo IRQ1
        pic.levantar(DISCO);                                 // IRQ14: menor prioridad, espera
        assertEquals(Optional.empty(), pic.reconocer());
        pic.levantar(TIMER);                                 // IRQ0: interrumpe a la rutina del teclado
        assertEquals(Optional.of(TIMER), pic.reconocer());
        assertEquals(2, pic.anidamiento());

        assertEquals(TIMER, pic.finDeInterrupcion());
        assertEquals(Optional.of(TECLADO), pic.enServicio());
        assertEquals(TECLADO, pic.finDeInterrupcion());
        assertEquals(Optional.of(DISCO), pic.reconocer());   // recién ahora
    }

    @Test @DisplayName("[P2] Excepciones y 0x80 no pasan por el PIC; un pedido repetido es un solo bit; EOI sin servicio es error")
    void casosInvalidos() {
        ControladorInterrupciones pic = new ControladorInterrupciones();
        assertThrows(EventoInvalidoException.class, () -> pic.levantar(DIVISION_CERO));
        assertThrows(EventoInvalidoException.class, () -> pic.levantar(LLAMADA_SISTEMA));
        assertThrows(EventoInvalidoException.class, () -> pic.enmascarar(ACCESO_ILEGAL));
        assertThrows(EventoInvalidoException.class, pic::finDeInterrupcion);

        pic.levantar(RED);
        pic.levantar(RED);                                   // el bit del IRR ya estaba en 1
        pic.reconocer();
        pic.finDeInterrupcion();
        assertEquals(1, pic.atendidas(RED));
        assertEquals(Optional.empty(), pic.reconocer());
    }
}
