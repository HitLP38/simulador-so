package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.motor.ColorEstado;
import pe.uni.so.motor.Instantanea;
import pe.uni.so.procesos.Estado;
import pe.uni.so.sesion.AnimacionArena;
import pe.uni.so.sesion.Camara;
import pe.uni.so.sesion.MapaEstados;
import pe.uni.so.sesion.MapaEstados.Punto;
import pe.uni.so.sesion.MapaEstados.Sala;
import pe.uni.so.sesion.Propiedades;
import pe.uni.so.sesion.Reproductor;
import pe.uni.so.sesion.SesionSimulacion;
import pe.uni.so.sesion.Vista;

class EspacioTrabajoTest {

    private static final double EPS = 1e-9;
    private static final long SEGUNDO = 1_000_000_000L;

    @Test @DisplayName("[G5] Cámara: ajustar centra el dibujo en la ventana con margen y escala acotada")
    void camaraAjustar() {
        Camara c = new Camara();
        c.ajustar(1000, 400, 1200, 800, 20);
        assertEquals(1.16, c.escala(), EPS);                           // limita el ancho: (1200 − 40) / 1000
        assertEquals(20, c.x(), EPS);
        assertEquals((800 - 400 * 1.16) / 2, c.y(), EPS);
        c.ajustar(10, 10, 1200, 800, 20);
        assertEquals(Camara.ESCALA_MAXIMA, c.escala(), EPS);           // un dibujo diminuto no pasa de 500 %
        double antes = c.escala();
        c.ajustar(1000, 400, 0, 0, 20);                                // ventana aún sin tamaño: no cambia
        assertEquals(antes, c.escala(), EPS);
    }

    @Test @DisplayName("[G5] Cámara: el zoom deja fijo el punto bajo el cursor y arrastrar mueve el dibujo")
    void camaraZoomYMover() {
        Camara c = new Camara();
        c.mover(100, 50);
        double mx = c.aMundoX(300), my = c.aMundoY(200);
        c.zoom(2, 300, 200);
        assertEquals(2, c.escala(), EPS);
        assertEquals(300, c.aPantallaX(mx), EPS);                      // el punto bajo el cursor no se mueve
        assertEquals(200, c.aPantallaY(my), EPS);
        assertEquals(200, c.porcentaje());
        for (int i = 0; i < 100; i++) c.zoom(Camara.PASO_ZOOM, 0, 0);
        assertEquals(Camara.ESCALA_MAXIMA, c.escala(), EPS);
        for (int i = 0; i < 100; i++) c.zoom(1 / Camara.PASO_ZOOM, 0, 0);
        assertEquals(Camara.ESCALA_MINIMA, c.escala(), EPS);
        c.zoom(0, 0, 0);                                               // factor inválido: se ignora
        assertEquals(Camara.ESCALA_MINIMA, c.escala(), EPS);
    }

    @Test @DisplayName("[G5] Reproductor: convierte tiempo real en ticks a la velocidad elegida, sin perder fracciones")
    void reproductor() {
        Reproductor r = new Reproductor();
        assertFalse(r.reproduciendo());
        assertEquals(0, r.ticks(SEGUNDO));                             // en pausa no avanza
        assertTrue(r.alternar());
        r.velocidad(10);
        assertEquals(10, r.ticks(SEGUNDO));
        int total = 0;
        for (int i = 0; i < 60; i++) total += r.ticks(SEGUNDO / 60);   // 60 cuadros de 1/60 s
        assertEquals(10, total, 1);                                    // la fracción se acumula entre cuadros
        r.velocidad(100);
        assertEquals(Reproductor.MAXIMO_POR_CUADRO, r.ticks(10 * SEGUNDO));   // tras una pausa larga del equipo
        assertEquals(0, r.ticks(-5));
        assertFalse(r.alternar());
        assertThrows(ParametroInvalidoException.class, () -> r.velocidad(3));
        assertEquals("10 ticks/s", Reproductor.etiqueta(10));
    }

    @Test @DisplayName("[G5][P16] Arena con el flujo «Ingreso de un proceso»: en t = 0 los que no llegaron esperan en su origen")
    void arenaInicial() {
        SesionSimulacion s = new SesionSimulacion(Maquina.porDefecto());
        Instantanea f0 = s.vista();
        MapaEstados.Diagrama d = MapaEstados.de(f0, Politica.ROUND_ROBIN);
        assertEquals(List.of(Sala.values()), d.salas().stream().map(MapaEstados.SalaDibujo::sala).toList());
        assertEquals(11, d.pasillos().size());
        assertTrue(d.carriles().isEmpty());                                     // RR: sin carriles de prioridad; la E/S va al panel
        assertEquals(20, d.pcb().size());                                       // la cola de procesos lista todos los PCB
        assertEquals(20, d.sala(Sala.PCB).cantidad());
        long porLlegar = f0.procesos().stream().filter(p -> p.llegada() > 0).count();
        assertEquals(porLlegar, d.sala(Sala.ORIGEN_SO).cantidad());
        assertEquals(20 - porLlegar, d.sala(Sala.CREACION).cantidad());
        assertEquals(20, d.fichas().size());
        for (MapaEstados.Ficha f : d.fichas()) {
            assertTrue(MapaEstados.rect(f.sala()).contiene(f.destino().x(), f.destino().y()), "P" + f.pid());
            assertEquals(f.pid(), d.fichaEn(f.destino().x(), f.destino().y()).orElseThrow().pid());
            assertFalse(f.exceso());                                           // los 20 caben a la vista
        }
        assertEquals(20, d.fichas().stream().map(MapaEstados.Ficha::destino).distinct().count());   // ninguna encima de otra
        assertEquals(ColorEstado.LISTO, Sala.LISTOS.acento);
        assertEquals(ColorEstado.EJECUTANDO, Sala.CPU.acento);
        assertEquals(ColorEstado.BLOQUEADO, Sala.ES.acento);
        assertEquals(ColorEstado.TERMINADO, Sala.TERMINADO.acento);
        assertTrue(Sala.PLANIFICAR.modulo && Sala.OTORGAR_CPU.modulo && Sala.PCB.modulo);
        for (Sala s0 : Sala.values()) assertFalse(s0.tecnico.isBlank(), s0 + " tiene su término técnico");
        assertTrue(d.fichaEn(-5, -5).isEmpty());
        assertThrows(ParametroInvalidoException.class, () -> MapaEstados.de(null));
    }

    @Test @DisplayName("[G5][P16] Arena: cada proceso en la sala de su estado; los bloqueados esperan tras la puerta de E/S")
    void arenaSalasYCarriles() {
        SesionSimulacion s = new SesionSimulacion(Maquina.porDefecto());
        int vistosBloqueados = 0;
        for (int t = 0; t < 250; t++) {
            s.avanzar(1);
            Instantanea f = s.vista();
            MapaEstados.Diagrama d = MapaEstados.de(f, Politica.ROUND_ROBIN);
            for (MapaEstados.Ficha x : d.fichas()) {
                Instantanea.Proceso p = f.proceso(x.pid()).orElseThrow();
                assertEquals(Sala.de(p, f.t()), x.sala());
                assertFalse(x.sala().modulo);
                assertTrue(MapaEstados.rect(x.sala()).contiene(x.destino().x(), x.destino().y()), "t=" + f.t() + " P" + x.pid());
                if (x.sala() == Sala.CPU) assertEquals(MapaEstados.PUNTO_CPU, x.destino());
                if (x.sala() == Sala.ES) {
                    assertEquals(MapaEstados.PUERTA_ES, x.destino());
                    vistosBloqueados++;
                }
            }
            List<Integer> listos = d.fichas().stream().filter(x -> x.sala() == Sala.LISTOS)
                    .sorted(Comparator.comparingDouble((MapaEstados.Ficha x) -> x.destino().y()).thenComparingDouble(x -> x.destino().x()))
                    .map(MapaEstados.Ficha::pid).toList();
            assertEquals(f.listos(), listos.subList(0, f.listos().size()), "t=" + f.t());   // RR: una fila FIFO
            assertEquals(f.en(Estado.BLOQUEADO).size(), d.sala(Sala.ES).cantidad());
        }
        assertTrue(vistosBloqueados > 0, "el lote oficial bloquea procesos por E/S en los primeros 250 ticks");
    }

    @Test @DisplayName("[G5][P5] La casilla «siguiente» es el proceso que el planificador despacha en el tick siguiente, en las 6 políticas")
    void siguienteCoincideConElMotor() {
        Configuracion d = Configuracion.porDefecto();
        for (Politica pol : Politica.values()) {
            Configuracion c = new Configuracion(d.ram(), d.pagina(), d.cpu(), pol, d.quantum(), d.costoDespacho(), d.grado(),
                    d.estrategia(), d.carga(), d.reemplazo(), d.dispositivos(), d.semilla());
            SesionSimulacion s = new SesionSimulacion(new Maquina("Prueba", c));
            int comprobados = 0;
            for (int t = 0; t < 400 && !s.terminada(); t++) {
                Instantanea antes = s.vista();
                MapaEstados.Diagrama dg = MapaEstados.de(antes, pol);
                s.avanzar(1);
                Instantanea despues = s.vista();
                boolean despacho = antes.cpu().libre() && !despues.cpu().libre() && antes.listos().contains(despues.cpu().enCpu())
                        && despues.procesos().stream().noneMatch(p -> p.cambio() && p.estado() == Estado.LISTO && p.anterior() != Estado.EJECUTANDO);
                if (!despacho) continue;
                int esperado = dg.fichas().stream().filter(MapaEstados.Ficha::siguiente).findFirst().orElseThrow().pid();
                assertEquals(esperado, despues.cpu().enCpu(), pol + " t=" + antes.t());
                comprobados++;
            }
            assertTrue(comprobados > 3, pol + ": se comprobaron " + comprobados + " despachos");
        }
    }

    @Test @DisplayName("[G5] Con prioridades, la cola de listos se dibuja en 5 carriles con su mapa de bits (como el ready summary de Windows)")
    void carrilesPorPrioridad() {
        Configuracion d = Configuracion.porDefecto();
        Configuracion c = new Configuracion(d.ram(), d.pagina(), d.cpu(), Politica.PRIORIDAD, d.quantum(), d.costoDespacho(), d.grado(),
                d.estrategia(), d.carga(), d.reemplazo(), d.dispositivos(), d.semilla());
        SesionSimulacion s = new SesionSimulacion(new Maquina("Prioridades", c));
        boolean vioVarios = false;
        for (int t = 0; t < 200; t++) {
            s.avanzar(1);
            Instantanea f = s.vista();
            MapaEstados.Diagrama dg = MapaEstados.de(f, Politica.PRIORIDAD);
            assertTrue(dg.porPrioridad());
            List<MapaEstados.Carril> niveles = dg.carriles().stream().filter(k -> k.sala() == Sala.LISTOS).toList();
            assertEquals(MapaEstados.NIVELES, niveles.size());
            int mapa = 0;
            for (Instantanea.Proceso p : f.en(Estado.LISTO)) {
                mapa |= 1 << (p.prioridad() - 1);
                MapaEstados.Carril k = niveles.get(p.prioridad() - 1);
                MapaEstados.Ficha x = dg.ficha(p.pid()).orElseThrow();
                assertTrue(k.rect().contiene(x.destino().x(), x.destino().y()), "P" + p.pid() + " en su carril de prioridad");
            }
            assertEquals(mapa, dg.mapaBits());
            assertEquals(MapaEstados.NIVELES, dg.mapaBitsTexto().length());
            vioVarios |= Integer.bitCount(mapa) >= 2;
        }
        assertTrue(vioVarios, "hubo listos en al menos 2 niveles a la vez");
        assertFalse(MapaEstados.de(s.vista(), Politica.ROUND_ROBIN).porPrioridad());
    }

    @Test @DisplayName("[G5] Arena: las rutas siguen las flechas del diagrama del curso y terminan en el destino")
    void rutasPorPasillos() {
        Punto cpu = MapaEstados.PUNTO_CPU, enListos = new Punto(450, 220), puerta = MapaEstados.PUERTA_ES, fin = new Punto(1265, 60);
        Punto planificar = MapaEstados.rect(Sala.PLANIFICAR).centro(), otorgar = MapaEstados.rect(Sala.OTORGAR_CPU).centro();
        List<Punto> despacho = MapaEstados.ruta(Sala.LISTOS, Sala.CPU, enListos, cpu);
        assertTrue(despacho.indexOf(planificar) >= 0 && despacho.indexOf(planificar) < despacho.indexOf(otorgar), "listos → planificar → otorgar");
        assertEquals(cpu, despacho.get(despacho.size() - 1));
        List<Punto> pedidoES = MapaEstados.ruta(Sala.CPU, Sala.ES, cpu, puerta);
        assertTrue(pedidoES.contains(otorgar), "el pedido de E/S pasa por otorgar CPU");
        assertEquals(puerta, pedidoES.get(pedidoES.size() - 1));
        List<Punto> finES = MapaEstados.ruta(Sala.ES, Sala.LISTOS, puerta, enListos);
        assertTrue(finES.stream().anyMatch(p -> p.y() == MapaEstados.Y_RETORNO), "fin de E/S → pasillo de retorno: " + finES);
        List<Punto> desalojo = MapaEstados.ruta(Sala.CPU, Sala.LISTOS, cpu, enListos);
        assertTrue(desalojo.stream().anyMatch(p -> p.y() == MapaEstados.Y_RETORNO), "desalojo → pasillo de retorno: " + desalojo);
        List<Punto> terminar = MapaEstados.ruta(Sala.CPU, Sala.TERMINADO, cpu, fin);
        assertEquals(fin, terminar.get(terminar.size() - 1));
        assertEquals(List.of(cpu), MapaEstados.ruta(Sala.LISTOS, Sala.LISTOS, enListos, cpu));
        List<Punto> finEsYDespacho = MapaEstados.ruta(Sala.ES, Sala.CPU, puerta, cpu);      // E/S termina y entra directo a la CPU
        assertTrue(finEsYDespacho.indexOf(planificar) > 0 && finEsYDespacho.indexOf(otorgar) > finEsYDespacho.indexOf(planificar),
                "pasa por planificar y otorgar CPU: " + finEsYDespacho);
        for (List<Punto> r : List.of(despacho, pedidoES, finES, desalojo, terminar,
                MapaEstados.ruta(Sala.ORIGEN_SO, Sala.CREACION, new Punto(60, 60), new Punto(240, 90)),
                MapaEstados.ruta(Sala.CREACION, Sala.LISTOS, new Punto(240, 90), enListos)))
            assertTrue(r.stream().allMatch(p -> p.x() >= 0 && p.x() <= MapaEstados.ANCHO && p.y() >= 0 && p.y() <= MapaEstados.ALTO), r.toString());
    }

    @Test @DisplayName("[G5] Animación: al pasar al tick siguiente las fichas caminan por su ruta; al saltar o retroceder se colocan")
    void animacion() {
        SesionSimulacion s = new SesionSimulacion(Maquina.porDefecto());
        AnimacionArena a = new AnimacionArena();
        a.mostrar(MapaEstados.de(s.vista()));
        assertFalse(a.hayMovimiento());                                    // la primera foto se coloca
        MapaEstados.Diagrama d;
        do {                                                               // hasta un tick en que alguien cambie de sala
            s.avanzar(1);
            d = MapaEstados.de(s.vista());
            a.mostrar(d);
        } while (!a.hayMovimiento());
        AnimacionArena.Sprite movil = a.sprites().stream().filter(AnimacionArena.Sprite::enMovimiento).findFirst().orElseThrow();
        MapaEstados.Ficha destino = d.ficha(movil.pid()).orElseThrow();
        assertEquals(destino.sala(), movil.sala());
        assertEquals(destino.destino(), movil.ruta().get(movil.ruta().size() - 1));
        a.avanzar(0.05);
        assertFalse(movil.estela().isEmpty() && a.sprite(movil.pid()).orElseThrow().estela().isEmpty());
        a.avanzar(30);                                                     // tiempo de sobra: todos llegan
        a.avanzar(1);                                                      // y la estela se apaga
        assertFalse(a.hayMovimiento());
        for (MapaEstados.Ficha f : d.fichas()) {
            AnimacionArena.Sprite sp = a.sprite(f.pid()).orElseThrow();
            assertEquals(f.destino().x(), sp.x(), EPS);
            assertEquals(f.destino().y(), sp.y(), EPS);
        }
        MapaEstados.Ficha cualquiera = d.fichas().get(0);
        assertEquals(cualquiera.pid(), a.pidEn(cualquiera.destino().x(), cualquiera.destino().y(), 46, 34).orElseThrow());
        s.irA(s.vista().t() + 40);                                         // salto largo: se colocan
        a.mostrar(MapaEstados.de(s.vista()));
        assertFalse(a.hayMovimiento());
        s.retroceder(1);                                                   // hacia atrás: se colocan
        a.mostrar(MapaEstados.de(s.vista()));
        assertFalse(a.hayMovimiento());
        assertThrows(ParametroInvalidoException.class, () -> a.velocidad(0));
        a.velocidad(900);
        assertEquals(900, a.velocidad(), EPS);
    }

    @Test @DisplayName("[G5][P16] Propiedades de la máquina y del proceso, y barra de estado en vivo y en el historial")
    void propiedades() {
        SesionSimulacion s = new SesionSimulacion(Maquina.porDefecto());
        s.avanzar(5);
        Instantanea f = s.vista();
        List<Propiedades.Fila> m = Propiedades.deMaquina(f);
        assertTrue(m.contains(new Propiedades.Fila("Instante", "t", "5")));
        assertTrue(m.contains(new Propiedades.Fila("Memoria", "Marcos ocupados", f.memoria().muestra().ocupados() + " de 16384")));
        int suma = m.stream().filter(x -> x.grupo().equals("Procesos")).mapToInt(x -> Integer.parseInt(x.valor())).sum();
        assertEquals(20, suma);
        Instantanea.Proceso p = f.procesos().get(0);
        List<Propiedades.Fila> pf = Propiedades.deProceso(p);
        assertEquals(new Propiedades.Fila("Proceso", "PID", "P" + p.pid()), pf.get(0));
        assertTrue(pf.stream().anyMatch(x -> x.clave().equals("PC") && x.valor().startsWith("0x")));
        assertTrue(Propiedades.barraEstado(f, true, 5, false, false).startsWith("t = 5 · "));
        assertTrue(Propiedades.barraEstado(f, false, 9, false, false).endsWith("viendo el historial (simulado hasta t = 9)"));
        assertTrue(Propiedades.barraEstado(f, true, 5, true, false).endsWith("simulación terminada"));
        assertEquals("fin de quantum", Propiedades.insignia(Instantanea.Insignia.FIN_QUANTUM));
        List<Propiedades.Fila> k = Propiedades.deKernel(f);                   // pestaña «Kernel»
        assertTrue(k.stream().anyMatch(x -> x.grupo().equals("Señales (PIC 8259)") && x.clave().equals("IRR pendientes")));
        assertEquals(5, k.stream().filter(x -> x.grupo().equals("Cola de eventos E/S")).count());
        String acumulado = String.format(java.util.Locale.ROOT, "%.1f ms", f.cpu().despachos() * 0.1).replace('.', ',');
        assertTrue(k.stream().anyMatch(x -> x.clave().equals("Cambios de contexto") && x.valor().equals(acumulado)), k.toString());
        assertTrue(k.stream().noneMatch(x -> x.valor().matches(".*atiende P(\\d+) .*cola .*\\bP\\1\\b.*")), "el atendido no se repite en la cola");
        assertThrows(ParametroInvalidoException.class, () -> Propiedades.deMaquina(null));
    }

    @Test @DisplayName("[G5] Explorador: la arena del kernel está disponible y las demás vistas avisan que están en construcción")
    void vistas() {
        assertTrue(Vista.MAPA_ESTADOS.disponible);
        assertEquals(Set.of(Vista.PROCESOS, Vista.MEMORIA, Vista.ENTRADA_SALIDA, Vista.INTERRUPCIONES),
                Set.copyOf(java.util.Arrays.stream(Vista.values()).filter(v -> !v.disponible).toList()));
        assertEquals("Arena del kernel", Vista.MAPA_ESTADOS.toString());
    }
}
