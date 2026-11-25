package pe.uni.so.sesion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.motor.Momento;
import pe.uni.so.procesos.Estado;

/**
 * [P1][P6][P16] Registro de toda la corrida para la vista «Procesos y Gantt»: los tramos de estado
 * de cada proceso (el diagrama de Gantt), la bitácora de sucesos con su momento a–e y las métricas
 * por proceso. Se llena tick a tick con las fotos que produce la sesión, así que cubre la corrida
 * completa aunque el historial de fotos sea limitado.
 *
 * <p>Las consultas reciben la foto mirada y recortan todo a su instante: al retroceder en la línea
 * de tiempo, el Gantt, la bitácora y las métricas muestran lo que se sabía en ese momento.
 */
public final class RegistroProcesos {

    /** Un tramo del Gantt: el proceso estuvo en {@code estado} en los ticks [desde, hasta). */
    public record Tramo(Estado estado, int desde, int hasta) {
        public int duracion() { return hasta - desde; }
    }

    /**
     * Métricas de un proceso hasta el instante mirado. {@code fin}, {@code retorno} y
     * {@code respuesta} son −1 mientras no se conozcan.
     *
     * @param retorno   fin − llegada (tiempo de retorno o turnaround)
     * @param respuesta primer despacho − llegada (tiempo de respuesta)
     * @param espera    ticks en Listo
     * @param cpu       ticks en CPU
     * @param bloqueado ticks en Bloqueado
     */
    public record FilaMetricas(int pid, int llegada, int burst, int prioridad, Estado estado, int fin, int retorno,
                               int respuesta, int espera, int cpu, int bloqueado, boolean usuario) {
        public boolean terminado() { return fin >= 0; }
    }

    /**
     * Métricas de la corrida: una fila por proceso y promedios de los terminados (NaN si no hay).
     * {@code conError}: [P9] terminados con un código de error (E01–E03 por excepción, E04 por el usuario).
     */
    public record Metricas(List<FilaMetricas> filas, int terminados, double retornoMedio, double esperaMedia,
                           double respuestaMedia, double usoCpu, int conError) {
        public Metricas { filas = List.copyOf(filas); }
    }

    private final Map<Integer, List<Tramo>> tramos = new HashMap<>();
    private final List<Instantanea.Suceso> sucesos = new ArrayList<>();
    private int ultimoT = -1;
    private int ticksCpuOcupada;                               // ticks con algún proceso en CPU, hasta ultimoT
    private final List<Integer> cpuOcupadaHasta = new ArrayList<>();   // acumulado por t
    private final Map<Integer, Integer> primeraCpu = new HashMap<>();  // pid → tick en que ejecutó por primera vez

    /** Olvida todo (al reiniciar o volver a simular desde t = 0). */
    public void reiniciar() {
        tramos.clear();
        sucesos.clear();
        cpuOcupadaHasta.clear();
        primeraCpu.clear();
        ticksCpuOcupada = 0;
        ultimoT = -1;
    }

    /** Último instante registrado (−1 si aún no hay). */
    public int ultimoT() { return ultimoT; }

    /**
     * Registra la foto del instante t. Volver a registrar el mismo t (por un ingreso de procesos)
     * agrega los procesos nuevos sin duplicar sucesos.
     */
    public void registrar(Instantanea f) {
        int t = f.t();
        if (t < ultimoT) throw new EventoInvalidoException("La foto t = " + t + " es anterior a la última registrada (" + ultimoT + ")");
        for (Instantanea.Proceso p : f.procesos()) {
            if (p.llegada() > t) continue;                                // aún no llega: no aparece en el Gantt
            List<Tramo> lista = tramos.computeIfAbsent(p.pid(), k -> new ArrayList<>());
            Estado e = p.estado();
            if (lista.isEmpty()) { lista.add(new Tramo(e, t, t + 1)); continue; }
            int i = lista.size() - 1;
            Tramo u = lista.get(i);
            if (u.estado() == Estado.TERMINADO) continue;                 // el Gantt del proceso termina ahí
            if (u.hasta() > t) {                                         // el mismo t, otra vez
                if (u.estado() == e) continue;
                if (u.desde() == t) lista.set(i, new Tramo(e, t, t + 1));
                else { lista.set(i, new Tramo(u.estado(), u.desde(), t)); lista.add(new Tramo(e, t, t + 1)); }
            } else if (u.estado() == e && u.hasta() == t) lista.set(i, new Tramo(e, u.desde(), t + 1));
            else lista.add(new Tramo(e, t, t + 1));
        }
        if (t > ultimoT) {
            sucesos.addAll(f.sucesos());
            int ejecuto = f.cpu().ejecuto();                         // quién ejecutó en el tick [t − 1, t)
            if (ejecuto != Instantanea.NINGUNO) {
                ticksCpuOcupada++;
                primeraCpu.putIfAbsent(ejecuto, t - 1);
            }
            while (cpuOcupadaHasta.size() <= t) cpuOcupadaHasta.add(ticksCpuOcupada);
            ultimoT = t;
        }
    }

    /** Tramos del proceso recortados al instante t (incluido). */
    public List<Tramo> tramos(int pid, int t) {
        List<Tramo> lista = tramos.getOrDefault(pid, List.of());
        List<Tramo> r = new ArrayList<>(lista.size());
        for (Tramo x : lista) {
            if (x.desde() > t) break;
            r.add(x.hasta() > t + 1 ? new Tramo(x.estado(), x.desde(), t + 1) : x);
        }
        return Collections.unmodifiableList(r);
    }

    /**
     * Bitácora hasta el instante t, del más reciente al más antiguo.
     *
     * @param pid      solo ese proceso, o {@link Instantanea#NINGUNO} para todos
     * @param momentos solo esos momentos; vacío para todos
     * @param maximo   cuántos sucesos devolver como mucho
     */
    public List<Instantanea.Suceso> bitacora(int t, int pid, Set<Momento> momentos, int maximo) {
        List<Instantanea.Suceso> r = new ArrayList<>();
        for (int i = sucesos.size() - 1; i >= 0 && r.size() < maximo; i--) {
            Instantanea.Suceso s = sucesos.get(i);
            if (s.t() > t) continue;
            if (pid != Instantanea.NINGUNO && s.pid() != pid) continue;
            if (!momentos.isEmpty() && !momentos.contains(s.momento())) continue;
            r.add(s);
        }
        return r;
    }

    /** Cuántos sucesos de cada momento hubo hasta el instante t. */
    public Map<Momento, Integer> conteoMomentos(int t) {
        Map<Momento, Integer> c = new java.util.EnumMap<>(Momento.class);
        for (Momento m : Momento.values()) c.put(m, 0);
        for (Instantanea.Suceso s : sucesos) if (s.t() <= t && s.momento() != null) c.merge(s.momento(), 1, Integer::sum);
        return c;
    }

    /** Métricas de los procesos de la foto, calculadas hasta su instante. */
    public Metricas metricas(Instantanea f) {
        int t = f.t();
        List<FilaMetricas> filas = new ArrayList<>(f.procesos().size());
        long sumaRetorno = 0, sumaEspera = 0, sumaRespuesta = 0;
        int terminados = 0, conError = 0;
        for (Instantanea.Proceso p : f.procesos()) {
            int fin = -1;
            for (Tramo x : tramos(p.pid(), t)) if (x.estado() == Estado.TERMINADO) fin = x.desde();
            Integer cpu = primeraCpu.get(p.pid());                       // tick real de su primera instrucción
            int primerDespacho = cpu != null && cpu < t ? cpu : -1;
            int retorno = fin >= 0 ? fin - p.llegada() : -1;
            int respuesta = primerDespacho >= 0 ? primerDespacho - p.llegada() : -1;
            filas.add(new FilaMetricas(p.pid(), p.llegada(), p.burst(), p.prioridad(), p.estado(), fin, retorno, respuesta,
                    p.espera(), p.cpu(), p.bloqueado(), p.usuario()));
            if (fin >= 0) {
                terminados++;
                sumaRetorno += retorno;
                sumaEspera += p.espera();
                sumaRespuesta += Math.max(0, respuesta);
                if (p.error() != null) conError++;
            }
        }
        double n = terminados;
        int ocupada = t < cpuOcupadaHasta.size() ? cpuOcupadaHasta.get(t) : ticksCpuOcupada;
        double uso = t <= 0 ? 0 : 100.0 * ocupada / t;                    // ticks transcurridos: [0, t)
        return new Metricas(filas, terminados,
                terminados == 0 ? Double.NaN : sumaRetorno / n,
                terminados == 0 ? Double.NaN : sumaEspera / n,
                terminados == 0 ? Double.NaN : sumaRespuesta / n, uso, conError);
    }
}
