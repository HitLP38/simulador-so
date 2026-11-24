package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.comparador.Columna;
import pe.uni.so.comparador.Comparador;
import pe.uni.so.comparador.ExportadorCsv;
import pe.uni.so.comparador.OpcionesComparacion;
import pe.uni.so.comparador.Resultado;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Configuracion.Estrategia;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.sesion.PlanCarga;

/** [P17][M8][G1] Opciones del diálogo «Comparar configuraciones» (la parte que no depende de JavaFX). */
class ComparadorOpcionesTest {

    private static final Configuracion BASE = Configuracion.porDefecto();

    @Test @DisplayName("[P17] Carga: la de la máquina o N aleatorios con semilla; N fuera de 1–1000 se rechaza")
    void carga() {
        PlanCarga lote = PlanCarga.loteOficial();
        assertSame(lote, OpcionesComparacion.carga(lote, true, 5, 7));
        assertEquals(PlanCarga.aleatoria(300, 9), OpcionesComparacion.carga(lote, false, 300, 9));
        assertThrows(ParametroInvalidoException.class, () -> OpcionesComparacion.carga(lote, false, 1001, 9));
        assertThrows(ParametroInvalidoException.class, () -> OpcionesComparacion.carga(lote, false, 0, 9));
    }

    @Test @DisplayName("[P10][P17] N y semilla iniciales: los de la carga aleatoria de la máquina; si no, 1000 y 42 (estrés)")
    void valoresIniciales() {
        assertEquals(1000, OpcionesComparacion.nInicial(PlanCarga.loteOficial()));
        assertEquals(42, OpcionesComparacion.semillaInicial(PlanCarga.loteOficial()));
        assertEquals(250, OpcionesComparacion.nInicial(PlanCarga.aleatoria(250, 7)));
        assertEquals(7, OpcionesComparacion.semillaInicial(PlanCarga.aleatoria(250, 7)));
    }

    @Test @DisplayName("[P17][M8] Escenarios de las casillas marcadas y parámetros del CSV con la carga elegida")
    void escenariosYParametros() {
        OpcionesComparacion o = new OpcionesComparacion("Máquina del curso", BASE, PlanCarga.loteOficial(),
                List.of(Politica.FCFS, Politica.ROUND_ROBIN), List.of(Estrategia.PEOR_AJUSTE));
        List<Comparador.Escenario> es = o.escenarios();
        assertEquals(3, es.size());
        assertEquals(Resultado.Serie.PLANIFICACION, es.get(0).serie());
        assertEquals(Estrategia.PEOR_AJUSTE, es.get(2).configuracion().estrategia());
        ExportadorCsv.Parametros p = o.parametros();
        assertEquals("Máquina del curso", p.maquina());
        assertEquals("lote oficial de 20 procesos", p.carga());
        assertEquals(20, p.procesos());

        OpcionesComparacion a = new OpcionesComparacion("M", BASE, PlanCarga.aleatoria(30, 5), List.of(), List.of(Estrategia.PRIMER_AJUSTE));
        assertEquals(30, a.parametros().procesos());
        assertEquals(5, a.parametros().semilla());
    }

    @Test @DisplayName("[P17] Sin casillas marcadas no hay comparación; faltan máquina o carga ⇒ error claro")
    void validaciones() {
        assertThrows(ParametroInvalidoException.class, () -> new OpcionesComparacion("M", BASE, PlanCarga.loteOficial(), List.of(), List.of()));
        assertThrows(ParametroInvalidoException.class, () -> new OpcionesComparacion(" ", BASE, PlanCarga.loteOficial(), List.of(Politica.FCFS), null));
        assertThrows(ParametroInvalidoException.class, () -> new OpcionesComparacion("M", BASE, null, List.of(Politica.FCFS), null));
    }

    @Test @DisplayName("[G1] Nombre sugerido del CSV: comparacion-<máquina sin tildes ni espacios>-<fecha>.csv")
    void nombreArchivo() {
        LocalDate d = LocalDate.of(2026, 11, 23);
        assertEquals("comparacion-maquina-del-curso-2026-11-23.csv", OpcionesComparacion.nombreArchivo("Máquina del curso", d));
        assertEquals("comparacion-ram-chica-rr-q-4-2026-11-23.csv", OpcionesComparacion.nombreArchivo("  RAM chica / RR (q=4)! ", d));
        assertEquals("comparacion-maquina-2026-11-23.csv", OpcionesComparacion.nombreArchivo("¿¡!?", d));
    }

    @Test @DisplayName("[G1] Celdas de la tabla: decimales de la columna, redondeo y separador decimal; resumen de casillas")
    void textoYResumen() {
        Resultado r = Comparador.correr(new Comparador.Escenario(Resultado.Serie.PLANIFICACION, BASE), PlanCarga.loteOficial());
        assertTrue(r.completa());
        assertEquals("793", OpcionesComparacion.texto(Columna.T_FINAL, r, ','));
        String espera = OpcionesComparacion.texto(Columna.ESPERA, r, ',');
        assertTrue(espera.matches("\\d+,\\d{2}"), espera);
        assertTrue(OpcionesComparacion.texto(Columna.THROUGHPUT, r, '.').matches("\\d+\\.\\d{3}"));
        assertEquals("9 configuraciones (6 políticas y 3 estrategias)",
                OpcionesComparacion.resumen(List.of(Politica.values()), List.of(Estrategia.values())));
        assertEquals("1 configuración (1 política y 0 estrategias)", OpcionesComparacion.resumen(List.of(Politica.SJF), List.of()));
    }
}
