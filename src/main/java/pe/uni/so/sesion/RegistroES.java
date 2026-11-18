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

/**
 * [E1][E6][I3][I4][P16] Registro de la corrida para las vistas «Entrada/salida» e «Interrupciones»:
 * los servicios de cada dispositivo (un Gantt por dispositivo), la cola más larga que tuvo y la
 * bitácora de interrupciones (cuándo se levantó y cuándo se atendió cada una). Se llena tick a
 * tick con las fotos de la sesión y sus consultas se recortan a la foto mirada.
 */
public final class RegistroES {

    /** Un servicio de E/S: el dispositivo atendió al proceso en los ticks [desde, hasta). */
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
    private final Map<Dispositivo, int[]> anterior = new EnumMap<>(Dispositivo.class);       // {pid, restante} del tick previo
    private final Map<Dispositivo, List<int[]>> colas = new EnumMap<>(Dispositivo.class);     // {t, largo} cuando cambia
    private final List<EventoInterrupcion> eventos = new ArrayList<>();
    private Instantanea.Pic picAnterior;
    private int ultimoT = -1;

    public void reiniciar() {
        servicios.clear();
        anterior.clear();
        colas.clear();
        eventos.clear();
        picAnterior = null;
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
            if (d.atendiendo() != Instantanea.NINGUNO) {
                int i = lista.size() - 1;
                boolean sigue = previo != null && previo[0] == d.atendiendo() && d.restante() < previo[1]
                        && i >= 0 && lista.get(i).hasta() == t;
                if (sigue) {
                    Servicio s = lista.get(i);
                    lista.set(i, new Servicio(s.dispositivo(), s.pid(), s.operacion(), s.desde(), t + 1));
                } else lista.add(new Servicio(d.dispositivo(), d.atendiendo(), d.operacion(), t, t + 1));
            }
            anterior.put(d.dispositivo(), new int[] {d.atendiendo(), d.restante()});
            List<int[]> c = colas.computeIfAbsent(d.dispositivo(), k -> new ArrayList<>());
            int largo = d.cola().size() - (d.atendiendo() == Instantanea.NINGUNO ? 0 : 1);   // en espera (sin el atendido)
            if (c.isEmpty() || c.get(c.size() - 1)[1] != largo) c.add(new int[] {t, largo});
        }
        Instantanea.Pic pic = f.pic();
        if (pic != null) {
            for (TipoInterrupcion x : pic.pendientes())
                if (picAnterior == null || !picAnterior.pendientes().contains(x)) eventos.add(new EventoInterrupcion(t, x, Accion.LEVANTADA, 1));
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
            if (s.desde() > t) break;
            r.add(s.hasta() > t + 1 ? new Servicio(d, s.pid(), s.operacion(), s.desde(), t + 1) : s);
        }
        return Collections.unmodifiableList(r);
    }

    /** Resumen del dispositivo hasta el instante t: servicios iniciados, ticks ocupado, uso (%) y cola más larga. */
    public ResumenDispositivo resumen(Dispositivo d, int t) {
        List<Servicio> lista = servicios(d, t);
        int ocupado = lista.stream().mapToInt(Servicio::duracion).sum();
        int max = 0;
        for (int[] c : colas.getOrDefault(d, List.of())) if (c[0] <= t) max = Math.max(max, c[1]);
        return new ResumenDispositivo(d, lista.size(), ocupado, t < 0 ? 0 : 100.0 * ocupado / (t + 1), max);
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
