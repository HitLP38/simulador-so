package pe.uni.so.sesion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.SolicitudES;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.interrupciones.TipoInterrupcion;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.procesos.Estado;

/**
 * [E1][E6][I3][I4][P16] Registro de la corrida para las vistas «Entrada/salida» e «Interrupciones»:
 * los servicios de cada dispositivo (un Gantt por dispositivo), la cola más larga que tuvo y la
 * bitácora de interrupciones (cuándo se levantó y cuándo se atendió cada una). Se llena tick a
 * tick con las fotos de la sesión y sus consultas se recortan a la foto mirada.
 */
public final class RegistroES {

    /**
     * Un servicio de E/S: el dispositivo trabajó para el proceso en los ticks [desde, hasta). La foto
     * t cierra el tick [t − 1, t): si en ella el dispositivo atiende a P y su restante bajó (o el
     * servicio empezó), trabajó para P en ese tick.
     */
    public record Servicio(Dispositivo dispositivo, int pid, SolicitudES.Operacion operacion, int desde, int hasta) {
        public int duracion() { return hasta - desde; }
    }

    /** Qué le pasó a una interrupción en el tick. */
    public enum Accion {
        LEVANTADA("levantada · IRR"), ATENDIDA("atendida · ISR y EOI");

        public final String etiqueta;

        Accion(String etiqueta) { this.etiqueta = etiqueta; }
    }

    /** Una línea de la bitácora de interrupciones (en orden de tiempo). */
    public record EventoInterrupcion(int t, TipoInterrupcion tipo, Accion accion, int veces) {}

    /** Resumen de un dispositivo hasta el instante mirado. */
    public record ResumenDispositivo(Dispositivo dispositivo, int servicios, int ticksOcupado, double uso, int colaMaxima) {}

    private final Map<Dispositivo, List<Servicio>> servicios = new EnumMap<>(Dispositivo.class);
    private final Map<Dispositivo, int[]> anterior = new EnumMap<>(Dispositivo.class);       // {pid, restante, trabajados} del tick previo
    private final Map<Dispositivo, List<int[]>> colas = new EnumMap<>(Dispositivo.class);     // {t, largo} cuando cambia
    private final List<EventoInterrupcion> eventos = new ArrayList<>();
    private Instantanea.Pic picAnterior;
    private int llamadasAnterior;                                                                // int 0x80 acumuladas en la foto previa
    private int fallosAnterior;                                                                  // [E7] fallos de página en la foto previa
    private int ultimoT = -1;

    public void reiniciar() {
        servicios.clear();
        anterior.clear();
        colas.clear();
        eventos.clear();
        picAnterior = null;
        llamadasAnterior = 0;
        fallosAnterior = 0;
        ultimoT = -1;
    }

    public int ultimoT() { return ultimoT; }

    /** Registra la foto del instante t (una vez por instante; repetir el mismo t no hace nada). */
    public void registrar(Instantanea f) {
        int t = f.t();
        if (t < ultimoT) throw new EventoInvalidoException("La foto t = " + t + " es anterior a la última registrada (" + ultimoT + ")");
        if (t == ultimoT) return;                                      // un ingreso de procesos no cambia la E/S
        for (Instantanea.FichaDispositivo d : f.dispositivos()) {
            List<Servicio> lista = servicios.computeIfAbsent(d.dispositivo(), k -> new ArrayList<>());
            int[] previo = anterior.get(d.dispositivo());
            int pid = d.atendiendo();
            // Mismo pedido si el dispositivo sigue con el mismo proceso: entre dos pedidos de un proceso siempre
            // hay una foto sin servicio (la IRQ se atiende, el proceso vuelve a la CPU y recién pide de nuevo).
            boolean mismo = previo != null && previo[0] == pid;
            boolean trabajo = pid != Instantanea.NINGUNO && t > 0 && d.trabajados() > (mismo ? previo[2] : 0);
            if (trabajo) {                                             // el dispositivo trabajó para P en [t − 1, t)
                int i = lista.size() - 1;
                if (mismo && i >= 0 && lista.get(i).pid() == pid && lista.get(i).hasta() == t - 1) {
                    Servicio s = lista.get(i);
                    lista.set(i, new Servicio(s.dispositivo(), s.pid(), s.operacion(), s.desde(), t));
                } else lista.add(new Servicio(d.dispositivo(), pid, d.operacion(), t - 1, t));
            }
            anterior.put(d.dispositivo(), new int[] {pid, d.restante(), d.trabajados()});
            List<int[]> c = colas.computeIfAbsent(d.dispositivo(), k -> new ArrayList<>());
            int largo = d.cola().size() - (d.atendiendo() == Instantanea.NINGUNO ? 0 : 1);   // en espera (sin el atendido)
            if (c.isEmpty() || c.get(c.size() - 1)[1] != largo) c.add(new int[] {t, largo});
        }
        // [E6][E9] Levantamientos: el contador acumulado del PIC, no el estado del dispositivo. Así queda la
        // IRQ de un fin forzado (tecla) aunque el kernel la atienda y libere el servicio antes de esta foto.
        Instantanea.Pic pic = f.pic();
        if (pic != null) {
            for (TipoInterrupcion x : TipoInterrupcion.values()) {
                int delta = pic.levantadas(x) - (picAnterior == null ? 0 : picAnterior.levantadas(x));
                if (delta > 0) eventos.add(new EventoInterrupcion(t, x, Accion.LEVANTADA, delta));
            }
        }
        // [I3] Fin de quantum: el kernel lo resuelve en el momento b sin pasar por el PIC (no hay IRQ 0 en el
        // IRR), pero es la interrupción del TIMER; la foto lo marca con la insignia FIN_QUANTUM.
        int finesQuantum = (int) f.procesos().stream().filter(p -> p.tiene(Instantanea.Insignia.FIN_QUANTUM)).count();
        if (finesQuantum > 0) {
            eventos.add(new EventoInterrupcion(t, TipoInterrupcion.TIMER, Accion.LEVANTADA, finesQuantum));
            eventos.add(new EventoInterrupcion(t, TipoInterrupcion.TIMER, Accion.ATENDIDA, finesQuantum));
        }
        // [E2] Llamada al sistema int 0x80: síncrona, la provoca la instrucción y la CPU la atiende al instante.
        if (f.cpu() != null) {
            int llamadas = f.cpu().llamadasSistema() - llamadasAnterior;
            if (llamadas > 0) {
                eventos.add(new EventoInterrupcion(t, TipoInterrupcion.LLAMADA_SISTEMA, Accion.LEVANTADA, llamadas));
                eventos.add(new EventoInterrupcion(t, TipoInterrupcion.LLAMADA_SISTEMA, Accion.ATENDIDA, llamadas));
            }
            llamadasAnterior = f.cpu().llamadasSistema();
        }
        // [E7][M1] Fallo de página (0x0E): síncrono; bajo demanda lo provoca la DMA de una E/S (o un acceso manual).
        if (f.memoria() != null) {
            int fallos = f.memoria().fallosPagina() - fallosAnterior;
            if (fallos > 0) {
                eventos.add(new EventoInterrupcion(t, TipoInterrupcion.FALLO_PAGINA, Accion.LEVANTADA, fallos));
                eventos.add(new EventoInterrupcion(t, TipoInterrupcion.FALLO_PAGINA, Accion.ATENDIDA, fallos));
            }
            fallosAnterior = f.memoria().fallosPagina();
        }
        // [P9] Excepciones (0x00, 0x06, 0x0D): síncronas como la int 0x80, la instrucción las provoca y la rutina
        // de servicio aborta al proceso en el mismo tick. Se ven en los procesos que pasan a Terminado con E01–E03.
        Map<TipoInterrupcion, Integer> excepciones = new EnumMap<>(TipoInterrupcion.class);
        for (Instantanea.Proceso p : f.procesos())
            if (p.estado() == Estado.TERMINADO && p.anterior() != Estado.TERMINADO && p.error() != null && p.error().excepcion != null)
                excepciones.merge(p.error().excepcion, 1, Integer::sum);
        excepciones.forEach((x, n) -> {
            eventos.add(new EventoInterrupcion(t, x, Accion.LEVANTADA, n));
            eventos.add(new EventoInterrupcion(t, x, Accion.ATENDIDA, n));
        });
        if (pic != null) {
            for (TipoInterrupcion x : TipoInterrupcion.values()) {
                int delta = pic.atendidas(x) - (picAnterior == null ? 0 : picAnterior.atendidas(x));
                if (delta > 0) eventos.add(new EventoInterrupcion(t, x, Accion.ATENDIDA, delta));
            }
            picAnterior = pic;
        }
        ultimoT = t;
    }

    /** Servicios del dispositivo recortados al instante t (incluido). */
    public List<Servicio> servicios(Dispositivo d, int t) {
        List<Servicio> r = new ArrayList<>();
        for (Servicio s : servicios.getOrDefault(d, List.of())) {
            if (s.desde() >= t) break;                                 // la foto t solo conoce los ticks hasta [t − 1, t)
            r.add(s.hasta() > t ? new Servicio(d, s.pid(), s.operacion(), s.desde(), t) : s);
        }
        return Collections.unmodifiableList(r);
    }

    /** Resumen del dispositivo hasta el instante t: servicios, ticks trabajados, uso (ticks trabajados / t, en %) y cola más larga. */
    public ResumenDispositivo resumen(Dispositivo d, int t) {
        List<Servicio> lista = servicios(d, t);
        int ocupado = lista.stream().mapToInt(Servicio::duracion).sum();
        int max = 0;
        for (int[] c : colas.getOrDefault(d, List.of())) if (c[0] <= t) max = Math.max(max, c[1]);
        return new ResumenDispositivo(d, lista.size(), ocupado, t <= 0 ? 0 : 100.0 * ocupado / t, max);   // t ticks transcurridos
    }

    /** Interrupciones del tipo atendidas hasta el instante t (las de E/S por el PIC; el TIMER por fin de quantum; la int 0x80 y las excepciones por la CPU). */
    public int atendidas(TipoInterrupcion tipo, int t) {
        int n = 0;
        for (EventoInterrupcion e : eventos) if (e.t() <= t && e.tipo() == tipo && e.accion() == Accion.ATENDIDA) n += e.veces();
        return n;
    }

    /**
     * Bitácora de interrupciones hasta el instante t, de la más reciente a la más antigua.
     *
     * @param tipos solo esos tipos; vacío para todos
     */
    public List<EventoInterrupcion> bitacora(int t, Set<TipoInterrupcion> tipos, int maximo) {
        List<EventoInterrupcion> r = new ArrayList<>();
        for (int i = eventos.size() - 1; i >= 0 && r.size() < maximo; i--) {
            EventoInterrupcion e = eventos.get(i);
            if (e.t() > t) continue;
            if (!tipos.isEmpty() && !tipos.contains(e.tipo())) continue;
            r.add(e);
        }
        return r;
    }
}
