package pe.uni.so.sesion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.procesos.Estado;

/**
 * [G5][P16] Textos del panel de propiedades y de la barra de estado, calculados solo a partir
 * de una {@link Instantanea}. Sin JavaFX: el panel solo pinta estas filas.
 */
public final class Propiedades {

    private Propiedades() {}

    /** Una fila del panel, agrupada por sección. */
    public record Fila(String grupo, String clave, String valor) {}

    /** Propiedades de la máquina en el instante de la foto (nada seleccionado). */
    public static List<Fila> deMaquina(Instantanea f) {
        exigir(f);
        List<Fila> filas = new ArrayList<>();
        Instantanea.Cpu c = f.cpu();
        filas.add(new Fila("Instante", "t", Integer.toString(f.t())));
        filas.add(new Fila("Instante", "Modo de la CPU", c.modo().name()));
        filas.add(new Fila("CPU", "Política", c.politica()));
        filas.add(new Fila("CPU", "En CPU", c.libre() ? "libre" : "P" + c.enCpu()));
        filas.add(new Fila("CPU", "Ejecutó en el tick", c.ejecuto() == Instantanea.NINGUNO ? "nadie" : "P" + c.ejecuto()));
        if (c.quantum() > 0) filas.add(new Fila("CPU", "Quantum usado", c.usado() + " de " + c.quantum()));
        filas.add(new Fila("CPU", "Despachos / desalojos", c.despachos() + " / " + c.desalojos()));
        for (Estado e : Estado.values())
            filas.add(new Fila("Procesos", MapaEstados.titulo(e), Integer.toString(f.en(e).size())));
        filas.add(new Fila("Colas", "Listos", pids(f.listos())));
        filas.add(new Fila("Colas", "Nuevos en espera", pids(f.nuevos())));
        Instantanea.Memoria m = f.memoria();
        if (m != null) {
            filas.add(new Fila("Memoria", "Marcos ocupados", m.muestra().ocupados() + " de " + m.marcos()));
            filas.add(new Fila("Memoria", "Huecos", Integer.toString(m.muestra().huecos())));
            filas.add(new Fila("Memoria", "Fallos de página", Integer.toString(m.fallosPagina())));
        }
        filas.add(new Fila("Interrupciones", "Pendientes (IRR)", f.pic().pendientes().isEmpty() ? "—" : f.pic().pendientes().toString()));
        return filas;
    }

    /** Propiedades de un proceso: los campos de su PCB en la foto. */
    public static List<Fila> deProceso(Instantanea.Proceso p) {
        if (p == null) throw new ParametroInvalidoException("Falta el proceso");
        List<Fila> filas = new ArrayList<>();
        filas.add(new Fila("Proceso", "PID", "P" + p.pid()));
        filas.add(new Fila("Proceso", "Estado", MapaEstados.titulo(p.estado()) + (p.cambio() ? " (antes " + MapaEstados.titulo(p.anterior()) + ")" : "")));
        if (!p.insignias().isEmpty()) filas.add(new Fila("Proceso", "Marcas", insignias(p)));
        if (p.momento() != null) filas.add(new Fila("Proceso", "Momento", p.momento().name().toLowerCase(Locale.ROOT) + ": " + p.motivo()));
        filas.add(new Fila("PCB", "Llegada", Integer.toString(p.llegada())));
        filas.add(new Fila("PCB", "Ráfaga / restante", p.burst() + " / " + p.restante()));
        filas.add(new Fila("PCB", "Prioridad", Integer.toString(p.prioridad())));
        filas.add(new Fila("PCB", "PC", p.pcHex()));
        if (p.quantumRestante() > 0) filas.add(new Fila("PCB", "Quantum restante", Integer.toString(p.quantumRestante())));
        filas.add(new Fila("Tiempos", "CPU", Integer.toString(p.cpu())));
        filas.add(new Fila("Tiempos", "Espera (Listo)", Integer.toString(p.espera())));
        filas.add(new Fila("Tiempos", "Bloqueado", Integer.toString(p.bloqueado())));
        filas.add(new Fila("E/S", "Pedidos pendientes", Integer.toString(p.pedidosES())));
        if (p.dispositivo() != null) filas.add(new Fila("E/S", "Espera en", nombre(p.dispositivo().name())));
        filas.add(new Fila("Memoria", "Marcos", p.cuota() > 0 ? p.marcos() + " (cuota " + p.cuota() + ")" : Integer.toString(p.marcos())));
        if (p.error() != null) filas.add(new Fila("Proceso", "Error", p.error().toString()));
        return filas;
    }

    /**
     * Pestaña «Kernel»: lo que en la arena son módulos compactos. Señales (registros del PIC
     * 8259), eventos varios (TIMER, expropiaciones, aborts), dispositivos y despachador.
     */
    public static List<Fila> deKernel(Instantanea f) { return deKernel(f, -1); }

    /**
     * @param finesDeQuantum interrupciones del TIMER por fin de quantum acumuladas hasta la foto
     *                       ({@link SesionSimulacion#finesDeQuantum}), o -1 si no se conocen
     */
    public static List<Fila> deKernel(Instantanea f, int finesDeQuantum) {
        exigir(f);
        List<Fila> filas = new ArrayList<>();
        Instantanea.Pic pic = f.pic();
        filas.add(new Fila("Señales (PIC 8259)", "IRR pendientes", nombres(pic.pendientes())));
        filas.add(new Fila("Señales (PIC 8259)", "IMR enmascaradas", nombres(pic.enmascaradas())));
        filas.add(new Fila("Señales (PIC 8259)", "ISR en servicio", nombres(pic.enServicio())));
        filas.add(new Fila("Señales (PIC 8259)", "Atendidas", pic.atendidas().isEmpty() ? "—"
                : pic.atendidas().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> e.getKey().name() + " " + e.getValue()).collect(Collectors.joining(", "))));
        Instantanea.Cpu c = f.cpu();
        filas.add(new Fila("Eventos varios", "TIMER (fin de quantum)", finesDeQuantum < 0 ? "—" : Integer.toString(finesDeQuantum)));
        filas.add(new Fila("Eventos varios", "Expropiaciones", Integer.toString(c.desalojos())));
        filas.add(new Fila("Eventos varios", "Fin de quantum", pidsCon(f, Instantanea.Insignia.FIN_QUANTUM)));
        filas.add(new Fila("Eventos varios", "Expropiados", pidsCon(f, Instantanea.Insignia.EXPROPIADO)));
        String aborts = f.procesos().stream().filter(p -> p.error() != null)
                .map(p -> "P" + p.pid() + " " + p.error().name()).collect(Collectors.joining(", "));
        filas.add(new Fila("Eventos varios", "Aborts", aborts.isEmpty() ? "—" : aborts));
        for (Instantanea.FichaDispositivo d : f.dispositivos()) {
            String v = d.libre() ? "libre" : "atiende P" + d.atendiendo() + " (faltan " + d.restante() + ")";
            List<Integer> esperan = d.cola().stream().filter(pid -> pid != d.atendiendo()).toList();   // el atendido no se repite
            if (!esperan.isEmpty()) v += " · cola " + pids(esperan);
            filas.add(new Fila("Cola de eventos E/S", nombre(d.dispositivo().name()), v));
        }
        filas.add(new Fila("Despachador", "Despachos", Integer.toString(c.despachos())));
        filas.add(new Fila("Despachador", "Cambios de contexto", String.format(Locale.ROOT, "%.1f ms", c.tiempoDespacho()).replace('.', ',')));
        return filas;
    }

    private static String nombres(List<? extends Enum<?>> l) {
        return l.isEmpty() ? "—" : l.stream().map(Enum::name).collect(Collectors.joining(", "));
    }

    private static String pidsCon(Instantanea f, Instantanea.Insignia i) {
        List<Integer> l = f.procesos().stream().filter(p -> p.insignias().contains(i)).map(Instantanea.Proceso::pid).toList();
        return pids(l);
    }

    /** Texto de la barra de estado. */
    public static String barraEstado(Instantanea f, boolean enVivo, int tActual, boolean terminada, boolean atascada) {
        exigir(f);
        String cpu = f.cpu().libre() ? "CPU libre" : "CPU: P" + f.cpu().enCpu();
        String base = "t = " + f.t() + " · " + f.cpu().modo() + " · " + cpu
                + " · Listos " + f.listos().size()
                + " · Bloqueados " + f.en(Estado.BLOQUEADO).size()
                + " · Terminados " + f.en(Estado.TERMINADO).size() + "/" + f.procesos().size();
        if (!enVivo) return base + " · viendo el historial (simulado hasta t = " + tActual + ")";
        if (terminada) return base + " · simulación terminada";
        if (atascada) return base + " · la simulación no puede avanzar";
        return base;
    }

    static String insignias(Instantanea.Proceso p) {
        return p.insignias().stream().sorted().map(Propiedades::insignia).collect(Collectors.joining(", "));
    }

    /** Nombre legible de cada marca. */
    public static String insignia(Instantanea.Insignia i) {
        return switch (i) {
            case EXPROPIADO -> "expropiado";
            case FIN_QUANTUM -> "fin de quantum";
            case SUSPENDIDO -> "suspendido";
            case ESPERA_MEMORIA -> "espera memoria";
            case CANCELACION_PENDIENTE -> "cancelación pendiente";
            case ERROR -> "error";
        };
    }

    private static String pids(List<Integer> pids) {
        return pids.isEmpty() ? "—" : pids.stream().map(p -> "P" + p).collect(Collectors.joining(" "));
    }

    private static String nombre(String enumName) {
        String n = enumName.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static void exigir(Instantanea f) {
        if (f == null) throw new ParametroInvalidoException("Falta la foto");
    }
}
