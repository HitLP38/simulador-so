package pe.uni.so.configuracion;

import java.util.ArrayList;
import java.util.List;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.VelocidadDispositivo;
import pe.uni.so.excepciones.SimuladorException;
import pe.uni.so.motor.Kernel;
import pe.uni.so.procesos.CreadorProcesos;
import pe.uni.so.procesos.Pcb;

/**
 * [G5] Ficha de una máquina para la interfaz (panel de detalles del gestor y ventana de la
 * máquina): filas etiqueta–valor y una corrida de prueba con el lote oficial. No depende de
 * JavaFX, así se prueba igual que el motor.
 */
public final class FichaMaquina {

    private FichaMaquina() {}

    /** Una fila de la ficha, agrupada por sección (Memoria, CPU, …). */
    public record Fila(String seccion, String etiqueta, String valor) {}

    public static List<Fila> filas(Configuracion c) {
        List<Fila> f = new ArrayList<>();
        f.add(new Fila("Memoria", "RAM", c.ram().toString()));
        f.add(new Fila("Memoria", "Página / marco", c.pagina().toString()));
        f.add(new Fila("Memoria", "Marcos", Integer.toString(c.marcos())));
        f.add(new Fila("Memoria", "Carga", c.carga().toString()));
        f.add(new Fila("Memoria", "Reemplazo", c.carga().usaReemplazo() ? c.reemplazo().name() : "— (carga total)"));
        f.add(new Fila("Memoria", "Asignación", c.estrategia().toString()));
        f.add(new Fila("CPU", "Velocidad", c.cpu() + " (" + instrucciones(c.cpu().instruccionesPorTick) + " instr/tick)"));
        f.add(new Fila("CPU", "Política", c.politica().toString()));
        f.add(new Fila("CPU", "Quantum", c.politica().usaQuantum() ? c.quantum() + " ticks" : "— (solo Round Robin)"));
        f.add(new Fila("CPU", "Cambio de contexto", FormularioMaquina.etiquetaCosto(c.costoDespacho())));
        f.add(new Fila("CPU", "Grado de multiprogramación", Integer.toString(c.grado())));
        for (Dispositivo d : Dispositivo.values()) {
            VelocidadDispositivo v = c.dispositivos().get(d);
            f.add(new Fila("Dispositivos", nombre(d), v.toString()));
        }
        f.add(new Fila("Simulación", "Semilla", Long.toString(c.semilla())));
        return f;
    }

    /** Resumen de una línea para la lista del gestor. */
    public static String resumen(Configuracion c) {
        String plan = c.politica().usaQuantum() ? c.politica() + " q=" + c.quantum() : c.politica().toString();
        return c.ram() + " · " + c.cpu() + " · " + plan + " · " + (c.carga().usaReemplazo() ? "bajo demanda " + c.reemplazo() : "carga total");
    }

    /**
     * Resultado de correr el lote oficial completo en la máquina.
     *
     * @param ok          true si la corrida terminó
     * @param mensaje     resumen para mostrar (o el motivo del fallo)
     * @param ticks       instante final
     * @param terminados  procesos terminados
     * @param desalojos   expropiaciones
     * @param gantt       diagrama de Gantt en texto (pid por tick)
     */
    public record Prueba(boolean ok, String mensaje, int ticks, int terminados, int desalojos, String gantt) {}

    /** Corre el lote oficial de 20 procesos hasta el final (la vista completa llega con el espacio de trabajo). */
    public static Prueba probarLoteOficial(Configuracion c) {
        List<Pcb> lote = CreadorProcesos.loteOficial(c.pagina().bytes);
        try {
            Kernel k = c.crearKernel(lote);
            k.ejecutarTodo();
            int terminados = (int) lote.stream().filter(p -> p.fin() >= 0 && p.restante() == 0).count();
            String msg = "Lote oficial terminado en t=" + k.t() + ": " + terminados + " de " + lote.size()
                    + " procesos, " + k.desalojos() + " desalojos";
            return new Prueba(true, msg, k.t(), terminados, k.desalojos(), k.gantt());
        } catch (SimuladorException ex) {
            return new Prueba(false, "No se pudo correr el lote oficial: " + ex.getMessage(), 0, 0, 0, "");
        }
    }

    private static String instrucciones(double porTick) {
        return porTick == Math.rint(porTick) ? Long.toString((long) porTick) : Double.toString(porTick).replace('.', ',');
    }

    private static String nombre(Dispositivo d) {
        String n = d.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
