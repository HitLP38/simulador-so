package pe.uni.so.planificacion;

import java.util.List;
import pe.uni.so.procesos.Pcb;

/**
 * [P5][P7] Patrón Strategy (LP2 S05): cada política de corto plazo es un objeto
 * intercambiable. El planificador no cambia al agregar una política nueva.
 */
public interface PoliticaPlanificacion {
    String nombre();

    /** Elige (sin quitar) el siguiente proceso de la cola de listos. */
    Pcb elegir(List<Pcb> listos);

    /** Momentos b, c y d (Sesión 6): ¿el candidato quita la CPU al actual? */
    default boolean expropia(Pcb candidato, Pcb actual) { return false; }

    /** Quantum en unidades de tiempo; sin límite salvo en Round Robin. */
    default int quantum() { return Integer.MAX_VALUE; }
}
