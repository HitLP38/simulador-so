package pe.uni.so.sesion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.motor.ColorEstado;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.procesos.CreadorProcesos;
import pe.uni.so.procesos.Estado;

/**
 * [G5][P16] Disposición de la «arena del kernel»: el diagrama «Ingreso de un proceso» del curso
 * hecho salas por donde caminan los procesos (estilo Kernel Arena). Un proceso nace en «creado
 * por el SO» o «ingresado por el usuario», pasa por el módulo de creación e ingreso (que lo anota
 * en la cola de procesos/PCB), espera en la cola de listos, el módulo de planificar lo elige, el
 * de otorgar CPU lo despacha y la CPU lo ejecuta. Al pedir E/S sale por la puerta de E/S (la cola
 * de eventos E/S, las señales y los eventos varios se ven en el panel «Eventos y E/S»); al terminar
 * la E/S, o al ser desalojado por el TIMER, vuelve a listos por el pasillo de retorno.
 *
 * <p>Calcula solo geometría (coordenadas del mundo) y rutas por los pasillos; el lienzo de JavaFX
 * la dibuja y {@link AnimacionArena} mueve las fichas.
 */
public final class MapaEstados {

    /**
     * Salas de la arena. Las de módulos (planificar, otorgar CPU, señales, eventos varios) son de
     * paso; la cola de procesos (PCB) es una tabla y no recibe fichas.
     */
    public enum Sala {
        ORIGEN_SO("CREADO POR EL SO", "lote oficial · aleatorio", ColorEstado.NUEVO, false),
        ORIGEN_USUARIO("INGRESADO POR USUARIO", "ingreso manual", ColorEstado.NUEVO, false),
        CREACION("CREACIÓN E INGRESO", "planificador de largo plazo", ColorEstado.NUEVO, false),
        PCB("COLA DE PROCESOS (PCB)", "tabla de procesos", "#94A3B8", true),
        LISTOS("COLA DE LISTOS (PCB)", "ready queue", ColorEstado.LISTO, false),
        PLANIFICAR("MÓD. PLANIFICAR", "scheduler · corto plazo", "#7C5CFF", true),
        OTORGAR_CPU("MÓD. OTORGAR CPU", "despachador · dispatcher", "#7C5CFF", true),
        CPU("CPU", "ejecuta la instrucción del PC", ColorEstado.EJECUTANDO, false),
        ES("E/S", "cola de eventos E/S", ColorEstado.BLOQUEADO, false),
        TERMINADO("TERMINADO", "fin de ejecución", ColorEstado.TERMINADO, false);

        /** Nombre del diagrama del curso y término técnico (subtítulo). */
        public final String titulo, tecnico, acento;
        /** Módulo o tabla: ningún proceso se queda dentro (se dibuja compacto). */
        public final boolean modulo;

        Sala(String titulo, String tecnico, String acento, boolean modulo) {
            this.titulo = titulo;
            this.tecnico = tecnico;
            this.acento = acento;
            this.modulo = modulo;
        }

        /** Sala donde reside un proceso en el instante t (los que aún no llegan esperan en su origen). */
        public static Sala de(Instantanea.Proceso p, int t) {
            return switch (p.estado()) {
                case NUEVO -> p.llegada() > t ? ORIGEN_SO : CREACION;
                case LISTO -> LISTOS;
                case EJECUTANDO -> CPU;
                case BLOQUEADO -> ES;
                case TERMINADO -> TERMINADO;
            };
        }
    }

    public record Punto(double x, double y) {
        public double distancia(Punto o) { return Math.hypot(o.x - x, o.y - y); }
    }

    public record Rect(double x, double y, double ancho, double alto) {
        public boolean contiene(double px, double py) { return px >= x && px <= x + ancho && py >= y && py <= y + alto; }
        public Punto centro() { return new Punto(x + ancho / 2, y + alto / 2); }
    }

    // ---------------------------------------------------------------- geometría fija

    public static final double ANCHO = 1370, ALTO = 452;
    /** Alto de la cabecera de cada sala (título del curso + término técnico). */
    static final double CABECERA = 40;
    public static final double FICHA_ANCHO = 46, FICHA_ALTO = 34, FICHA_CPU_ANCHO = 54, FICHA_CPU_ALTO = 40;
    public static final double FICHA_MINI_ANCHO = 30, FICHA_MINI_ALTO = 18;

    private static final Map<Sala, Rect> SALAS = new EnumMap<>(Sala.class);
    static {
        SALAS.put(Sala.ORIGEN_SO, new Rect(20, 20, 150, 160));
        SALAS.put(Sala.ORIGEN_USUARIO, new Rect(20, 200, 150, 80));
        SALAS.put(Sala.CREACION, new Rect(200, 40, 150, 330));
        SALAS.put(Sala.PCB, new Rect(390, 18, 300, 76));
        SALAS.put(Sala.LISTOS, new Rect(390, 100, 300, 270));
        SALAS.put(Sala.PLANIFICAR, new Rect(750, 120, 130, 90));
        SALAS.put(Sala.OTORGAR_CPU, new Rect(750, 250, 130, 90));
        SALAS.put(Sala.CPU, new Rect(940, 90, 200, 240));
        SALAS.put(Sala.ES, new Rect(770, 404, 90, 36));             // puerta: el detalle está en el panel «Eventos y E/S»
        SALAS.put(Sala.TERMINADO, new Rect(1180, 20, 170, 400));
    }

    /** Recuadro punteado del módulo de planificación (planificar + otorgar CPU), como en el diagrama. */
    public static final Rect GRUPO_PLANIFICACION = new Rect(730, 92, 170, 262);

    /** Pasillos que unen las salas. */
    public static final List<Rect> PASILLOS = List.of(
            new Rect(170, 76, 30, 28),          // SO → creación
            new Rect(170, 226, 30, 28),         // usuario → creación
            new Rect(350, 216, 40, 28),         // creación → listos
            new Rect(690, 151, 60, 28),         // listos → planificar
            new Rect(801, 210, 28, 40),         // planificar → otorgar CPU
            new Rect(880, 281, 60, 28),         // otorgar CPU → CPU
            new Rect(801, 340, 28, 64),         // otorgar CPU → puerta de E/S
            new Rect(1026, 330, 28, 62),        // CPU → retorno (TIMER: desalojo)
            new Rect(451, 378, 603, 28),        // retorno: E/S terminada o desalojo → listos (señal)
            new Rect(451, 370, 28, 22),         // retorno → cola de listos
            new Rect(1140, 196, 40, 28));       // CPU → terminado

    public static final double Y_RETORNO = 392;
    static final double Y_CREACION = 230, Y_LISTOS_PLAN = 165, Y_OTORGAR = 295, Y_TERMINAR = 210;
    static final double X_PLAN = 815, X_RETORNO_LISTOS = 465, X_RETORNO_CPU = 1040, X_TERMINAR = 1160, X_SALIDA_LISTOS = 690;
    /** Donde esperan (ocultos) los procesos bloqueados: la puerta hacia la cola de eventos E/S. */
    public static final Punto PUERTA_ES = new Punto(815, 422);

    public static final Punto PUNTO_CPU = new Punto(1040, 200);
    static final double PASO_FICHA = 52;
    static final int SEPARACION_FILAS = 10;
    /** Carriles por prioridad en la cola de listos (políticas de prioridad). */
    public static final int NIVELES = CreadorProcesos.PRIORIDAD_MAXIMA - CreadorProcesos.PRIORIDAD_MINIMA + 1;

    public static Rect rect(Sala s) { return SALAS.get(s); }

    // ---------------------------------------------------------------- resultado

    /**
     * Una sala con cuántos procesos tiene, si alguno llegó en este tick (destella) y cuántos no
     * caben a la vista ({@code exceso}, se muestra «+N»).
     */
    public record SalaDibujo(Sala sala, Rect rect, int cantidad, boolean llegada, int exceso) {}

    /**
     * Un carril: de la cola de E/S (uno por dispositivo y uno de «otros») o de la cola de listos
     * (uno por nivel de prioridad).
     *
     * @param dispositivo dispositivo del carril de E/S, o null (otros o carril de prioridad)
     * @param prioridad   nivel del carril de listos, o 0 si es de E/S
     * @param atendiendo  PID en servicio en el dispositivo, o {@link Instantanea#NINGUNO}
     * @param restante    ticks que le faltan al pedido en servicio, o -1
     */
    public record Carril(Sala sala, Dispositivo dispositivo, int prioridad, String titulo, Rect rect,
                         int atendiendo, int restante, int cantidad) {}

    /** Celda de la tabla de procesos (PCB): un proceso y su estado. */
    public record CeldaPcb(int pid, Estado estado, String color, Rect rect) {}

    /**
     * Un proceso dibujado en su lugar de destino.
     *
     * @param siguiente es el que el planificador elegiría ahora (casilla dorada)
     * @param exceso    no cupo en su sala o carril y se apila sobre la última ficha visible
     */
    public record Ficha(int pid, Estado estado, Sala sala, String color, boolean cambio, Set<Instantanea.Insignia> insignias,
                        int prioridad, int restante, boolean siguiente, boolean exceso, Punto destino) {
        public double ancho() { return sala == Sala.CPU ? FICHA_CPU_ANCHO : sala == Sala.ORIGEN_SO ? FICHA_MINI_ANCHO : FICHA_ANCHO; }
        public double alto() { return sala == Sala.CPU ? FICHA_CPU_ALTO : sala == Sala.ORIGEN_SO ? FICHA_MINI_ALTO : FICHA_ALTO; }
    }

    /** Datos de la CPU y de los módulos para rotular sus salas. */
    public record Cpu(String modo, String politica, int enCpu, int usado, int quantum, int restante, int burst, int despachos,
                      int desalojos, int abortos, List<String> irqPendientes, List<String> irqEnServicio) {
        /** Avance de la ráfaga del proceso en CPU (0..1). */
        public double avance() { return burst <= 0 ? 0 : 1.0 - (double) restante / burst; }
    }

    /**
     * @param porPrioridad la cola de listos se dibuja en {@link #NIVELES} carriles (políticas de prioridad)
     * @param mapaBits     bit i encendido si el carril de prioridad i+1 tiene procesos (como el
     *                     <i>ready summary</i> de Windows)
     */
    public record Diagrama(int t, double ancho, double alto, List<SalaDibujo> salas, List<Rect> pasillos,
                           List<Carril> carriles, List<CeldaPcb> pcb, List<Ficha> fichas, Cpu cpu,
                           boolean porPrioridad, int mapaBits) {
        public Optional<Ficha> ficha(int pid) { return fichas.stream().filter(f -> f.pid() == pid).findFirst(); }

        public SalaDibujo sala(Sala s) { return salas.stream().filter(x -> x.sala() == s).findFirst().orElseThrow(); }

        /** Ficha cuyo destino está bajo el punto (mx, my); el lienzo animado usa {@link AnimacionArena#pidEn}. */
        public Optional<Ficha> fichaEn(double mx, double my) {
            return fichas.stream().filter(f -> Math.abs(mx - f.destino().x()) <= f.ancho() / 2
                    && Math.abs(my - f.destino().y()) <= f.alto() / 2).findFirst();
        }

        /** El mapa de bits como texto, del nivel 1 al 5 («01101»). */
        public String mapaBitsTexto() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < NIVELES; i++) sb.append((mapaBits >> i & 1) == 1 ? '1' : '0');
            return sb.toString();
        }
    }

    private MapaEstados() {}

    // ---------------------------------------------------------------- disposición

    /** Disposición con la cola de listos en una sola fila FIFO (sin saber la política). */
    public static Diagrama de(Instantanea foto) { return de(foto, null); }

    /**
     * @param politica política de la máquina: decide quién es «el siguiente» y, con prioridades, la
     *                 cola de listos se dibuja en un carril por nivel; null = FIFO
     */
    public static Diagrama de(Instantanea foto, Politica politica) {
        if (foto == null) throw new ParametroInvalidoException("Falta la foto para dibujar la arena");
        int t = foto.t();
        List<Ficha> fichas = new ArrayList<>();
        Map<Sala, Integer> cantidades = new EnumMap<>(Sala.class);
        Map<Sala, Integer> excesos = new EnumMap<>(Sala.class);
        Set<Sala> llegadas = new HashSet<>();
        for (Instantanea.Proceso p : foto.procesos()) {
            Sala s = Sala.de(p, t);
            cantidades.merge(s, 1, Integer::sum);
            if (p.cambio()) llegadas.add(s);
        }
        cantidades.put(Sala.PCB, foto.procesos().size());

        Comparator<Instantanea.Proceso> porLlegada = Comparator.comparingInt(Instantanea.Proceso::llegada)
                .thenComparingInt(Instantanea.Proceso::pid);
        rejilla(lista(foto, p -> Sala.de(p, t) == Sala.ORIGEN_SO, porLlegada), Sala.ORIGEN_SO, 4,
                FICHA_MINI_ALTO, 4, -1, fichas, excesos);
        rejilla(ordenar(lista(foto, p -> Sala.de(p, t) == Sala.CREACION, porLlegada), foto.nuevos()), Sala.CREACION, 2,
                FICHA_ALTO, SEPARACION_FILAS, -1, fichas, excesos);
        rejilla(lista(foto, p -> p.estado() == Estado.TERMINADO, Comparator.comparingInt(Instantanea.Proceso::pid)),
                Sala.TERMINADO, 3, FICHA_ALTO, SEPARACION_FILAS, -1, fichas, excesos);
        for (Instantanea.Proceso p : foto.en(Estado.EJECUTANDO)) fichas.add(ficha(p, Sala.CPU, false, false, PUNTO_CPU));

        List<Instantanea.Proceso> listos = ordenar(lista(foto, p -> p.estado() == Estado.LISTO, porLlegada), foto.listos());
        int siguiente = siguiente(listos, politica);
        boolean porPrioridad = politica == Politica.PRIORIDAD || politica == Politica.PRIORIDAD_EXPROPIATIVA;
        List<Carril> carriles = new ArrayList<>();
        int mapa = 0;
        if (porPrioridad) mapa = carrilesPrioridad(listos, siguiente, carriles, fichas, excesos);
        else rejilla(listos, Sala.LISTOS, 5, FICHA_ALTO, SEPARACION_FILAS, siguiente, fichas, excesos);
        for (Instantanea.Proceso p : foto.en(Estado.BLOQUEADO))           // bloqueados: tras la puerta de E/S (panel inferior)
            fichas.add(ficha(p, Sala.ES, false, false, PUERTA_ES));

        List<SalaDibujo> salas = new ArrayList<>();
        for (Sala s : Sala.values())
            salas.add(new SalaDibujo(s, rect(s), cantidades.getOrDefault(s, 0), llegadas.contains(s), excesos.getOrDefault(s, 0)));

        Instantanea.Cpu c = foto.cpu();
        Optional<Instantanea.Proceso> enCpu = c.libre() ? Optional.empty() : foto.proceso(c.enCpu());
        int abortos = (int) foto.procesos().stream().filter(p -> p.error() != null).count();
        Cpu cpu = new Cpu(c.modo().name(), c.politica(), c.enCpu(), c.usado(), c.quantum(),
                enCpu.map(Instantanea.Proceso::restante).orElse(0), enCpu.map(Instantanea.Proceso::burst).orElse(0),
                c.despachos(), c.desalojos(), abortos,
                foto.pic().pendientes().stream().map(Enum::name).toList(), foto.pic().enServicio().stream().map(Enum::name).toList());
        fichas.sort(Comparator.comparingInt(Ficha::pid));
        return new Diagrama(t, ANCHO, ALTO, salas, PASILLOS, carriles, tablaPcb(foto), fichas, cpu, porPrioridad, mapa);
    }

    private static List<Instantanea.Proceso> lista(Instantanea foto, java.util.function.Predicate<Instantanea.Proceso> filtro,
                                                   Comparator<Instantanea.Proceso> orden) {
        return foto.procesos().stream().filter(filtro).sorted(orden).toList();
    }

    /** Primero los que están en la cola (en su orden), luego el resto en el orden que ya tenían. */
    private static List<Instantanea.Proceso> ordenar(List<Instantanea.Proceso> procesos, List<Integer> cola) {
        List<Instantanea.Proceso> r = new ArrayList<>(procesos);
        r.sort(Comparator.comparingInt(p -> {
            int i = cola.indexOf(p.pid());
            return i < 0 ? Integer.MAX_VALUE : i;
        }));
        return r;
    }

    /**
     * PID que el planificador elegiría de la cola de listos, con los mismos desempates que el
     * motor: FCFS y RR el primero de la cola; SJF/SRTF el de menor ráfaga restante; prioridades el
     * de menor número; empates por llegada y luego PID.
     */
    static int siguiente(List<Instantanea.Proceso> listos, Politica politica) {
        if (listos.isEmpty()) return Instantanea.NINGUNO;
        Comparator<Instantanea.Proceso> desempate = Comparator.comparingInt(Instantanea.Proceso::llegada)
                .thenComparingInt(Instantanea.Proceso::pid);
        if (politica == null) return listos.get(0).pid();
        return switch (politica) {
            case FCFS, ROUND_ROBIN -> listos.get(0).pid();
            case SJF, SRTF -> listos.stream().min(Comparator.comparingInt(Instantanea.Proceso::restante).thenComparing(desempate))
                    .orElseThrow().pid();
            case PRIORIDAD, PRIORIDAD_EXPROPIATIVA -> listos.stream()
                    .min(Comparator.comparingInt(Instantanea.Proceso::prioridad).thenComparing(desempate)).orElseThrow().pid();
        };
    }

    /** Ubica procesos en rejilla dentro de su sala; los que no caben se apilan en la última celda. */
    private static void rejilla(List<Instantanea.Proceso> procesos, Sala s, int columnas, double altoFicha, double separacion,
                                int siguiente, List<Ficha> fichas, Map<Sala, Integer> excesos) {
        Rect r = rect(s);
        double ancho = r.ancho() / columnas;
        double paso = altoFicha + separacion;
        int filas = Math.max(1, (int) ((r.alto() - CABECERA - 4) / paso));
        int capacidad = filas * columnas;
        for (int i = 0; i < procesos.size(); i++) {
            int k = Math.min(i, capacidad - 1);
            Punto p = new Punto(r.x() + ancho * (k % columnas) + ancho / 2, r.y() + CABECERA + altoFicha / 2 + (k / columnas) * paso);
            Instantanea.Proceso pr = procesos.get(i);
            fichas.add(ficha(pr, s, pr.pid() == siguiente, i >= capacidad, p));
        }
        if (procesos.size() > capacidad) excesos.put(s, procesos.size() - capacidad + 1);
    }

    /** Cola de listos en un carril por nivel de prioridad; devuelve el mapa de bits de niveles ocupados. */
    private static int carrilesPrioridad(List<Instantanea.Proceso> listos, int siguiente, List<Carril> carriles,
                                         List<Ficha> fichas, Map<Sala, Integer> excesos) {
        Rect r = rect(Sala.LISTOS);
        double alto = (r.alto() - CABECERA - 6) / NIVELES;
        int porCarril = (int) ((r.ancho() - 50) / PASO_FICHA);
        int mapa = 0, exceso = 0;
        for (int nivel = CreadorProcesos.PRIORIDAD_MINIMA; nivel <= CreadorProcesos.PRIORIDAD_MAXIMA; nivel++) {
            int n = nivel;
            List<Instantanea.Proceso> enNivel = listos.stream().filter(p -> nivelDe(p) == n).toList();
            int i = nivel - CreadorProcesos.PRIORIDAD_MINIMA;
            Rect carril = new Rect(r.x() + 8, r.y() + CABECERA + i * alto, r.ancho() - 16, alto - 4);
            carriles.add(new Carril(Sala.LISTOS, null, nivel, "P" + nivel, carril, Instantanea.NINGUNO, -1, enNivel.size()));
            if (!enNivel.isEmpty()) mapa |= 1 << i;
            double y = carril.y() + carril.alto() / 2;
            for (int j = 0; j < enNivel.size(); j++) {
                int k = Math.min(j, porCarril - 1);
                Instantanea.Proceso p = enNivel.get(j);
                fichas.add(ficha(p, Sala.LISTOS, p.pid() == siguiente, j >= porCarril,
                        new Punto(carril.x() + 36 + FICHA_ANCHO / 2 + k * PASO_FICHA, y)));
            }
            exceso += Math.max(0, enNivel.size() - porCarril + 1) * (enNivel.size() > porCarril ? 1 : 0);
        }
        if (exceso > 0) excesos.put(Sala.LISTOS, exceso);
        return mapa;
    }

    /** Nivel de prioridad dentro de los carriles (fuera de rango se lleva al extremo más cercano). */
    private static int nivelDe(Instantanea.Proceso p) {
        return Math.max(CreadorProcesos.PRIORIDAD_MINIMA, Math.min(CreadorProcesos.PRIORIDAD_MAXIMA, p.prioridad()));
    }

    /**
     * Cola de procesos en miniatura: una celda por PCB, del color de su estado, en una franja bajo
     * el título (la tabla completa está en la pestaña «Procesos (PCB)» del panel derecho).
     */
    private static List<CeldaPcb> tablaPcb(Instantanea foto) {
        Rect r = rect(Sala.PCB);
        int n = foto.procesos().size();
        if (n == 0) return List.of();
        double anchoUtil = r.ancho() - 16, altoUtil = r.alto() - CABECERA + 2;
        int filas = Math.max(1, (int) Math.ceil(Math.sqrt(n * altoUtil / anchoUtil)));   // 20 PCB → 2 filas de 10
        int columnas = (int) Math.ceil((double) n / filas);
        double w = anchoUtil / columnas, h = Math.min(altoUtil / filas, 20);
        List<CeldaPcb> celdas = new ArrayList<>(n);
        List<Instantanea.Proceso> orden = foto.procesos().stream().sorted(Comparator.comparingInt(Instantanea.Proceso::pid)).toList();
        for (int i = 0; i < n; i++) {
            Instantanea.Proceso p = orden.get(i);
            celdas.add(new CeldaPcb(p.pid(), p.estado(), p.color(),
                    new Rect(r.x() + 8 + (i % columnas) * w, r.y() + CABECERA - 2 + (i / columnas) * h, w - 2, h - 2)));
        }
        return celdas;
    }

    private static Ficha ficha(Instantanea.Proceso p, Sala s, boolean siguiente, boolean exceso, Punto destino) {
        return new Ficha(p.pid(), p.estado(), s, p.color(), p.cambio(), p.insignias(), p.prioridad(), p.restante(),
                siguiente, exceso, destino);
    }

    // ---------------------------------------------------------------- rutas

    /**
     * Camino por los pasillos para ir de una sala a otra, como las flechas del diagrama del curso:
     * listos → planificar → otorgar CPU → CPU; CPU → otorgar CPU → puerta de E/S (pedido de E/S);
     * puerta de E/S → retorno → listos (fin de E/S, por señal); CPU → retorno → listos (desalojo
     * por el TIMER); CPU → terminado. Termina en {@code destino}; en la misma sala es una recta.
     */
    public static List<Punto> ruta(Sala desde, Sala hasta, Punto origen, Punto destino) {
        List<Punto> r = new ArrayList<>();
        if (desde == hasta) {
            r.add(destino);
            return r;
        }
        Punto planificar = rect(Sala.PLANIFICAR).centro(), otorgar = rect(Sala.OTORGAR_CPU).centro();
        Rect listos = rect(Sala.LISTOS), creacion = rect(Sala.CREACION);
        switch (desde) {
            case ORIGEN_SO, ORIGEN_USUARIO -> {
                r.add(new Punto(origen.x(), origen.y()));
                r.add(new Punto(creacion.x() + 10, origen.y()));
                if (hasta != Sala.CREACION) {
                    r.add(new Punto(creacion.x() + 10, Y_CREACION));
                    r.add(new Punto(listos.x() + 10, Y_CREACION));
                }
            }
            case CREACION -> {
                r.add(new Punto(creacion.x() + creacion.ancho() - 10, Y_CREACION));
                r.add(new Punto(listos.x() + 10, Y_CREACION));
            }
            case LISTOS -> {
                r.add(new Punto(X_SALIDA_LISTOS - 10, origen.y()));
                r.add(new Punto(X_SALIDA_LISTOS - 10, Y_LISTOS_PLAN));
                r.add(new Punto(planificar.x(), Y_LISTOS_PLAN));
                r.add(planificar);
                r.add(otorgar);
                if (hasta == Sala.CPU) r.add(new Punto(rect(Sala.CPU).x() + 20, Y_OTORGAR));
            }
            case CPU -> {
                if (hasta == Sala.ES) {                                    // pedido de E/S: otorgar CPU → puerta
                    r.add(new Punto(rect(Sala.CPU).x() + 20, Y_OTORGAR));
                    r.add(otorgar);
                } else if (hasta == Sala.TERMINADO) {
                    r.add(new Punto(X_TERMINAR, Y_TERMINAR));
                } else {                                                   // desalojo (TIMER): baja al retorno
                    r.add(new Punto(X_RETORNO_CPU, Y_RETORNO));
                    r.add(new Punto(X_RETORNO_LISTOS, Y_RETORNO));
                }
            }
            case ES -> {                                                   // fin de E/S (señal): sale por la puerta
                r.add(new Punto(PUERTA_ES.x(), Y_RETORNO));
                if (hasta != Sala.TERMINADO) r.add(new Punto(X_RETORNO_LISTOS, Y_RETORNO));
            }
            default -> { }
        }
        switch (hasta) {
            case LISTOS -> {
                if (desde == Sala.CPU || desde == Sala.ES) r.add(new Punto(X_RETORNO_LISTOS, listos.y() + listos.alto() - 10));
                r.add(new Punto(r.isEmpty() ? destino.x() : r.get(r.size() - 1).x(), destino.y()));
            }
            case ES -> r.add(new Punto(otorgar.x(), Y_RETORNO));
            case CPU -> {                                                  // llega a la CPU sin esperar en listos:
                if (desde != Sala.LISTOS) {                                // igual pasa por planificar y otorgar CPU
                    r.add(new Punto(X_SALIDA_LISTOS - 10, Y_LISTOS_PLAN));
                    r.add(new Punto(planificar.x(), Y_LISTOS_PLAN));
                    r.add(planificar);
                    r.add(otorgar);
                    r.add(new Punto(rect(Sala.CPU).x() + 20, Y_OTORGAR));
                }
            }
            case TERMINADO -> r.add(new Punto(X_TERMINAR, Math.max(Y_TERMINAR, Math.min(destino.y(), rect(Sala.TERMINADO).y() + rect(Sala.TERMINADO).alto()))));
            default -> { }
        }
        r.add(destino);
        return r;
    }

    // ---------------------------------------------------------------- textos

    /** «Nuevo», «Listo», «Ejecutando», … */
    public static String titulo(Estado e) {
        String n = e.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
