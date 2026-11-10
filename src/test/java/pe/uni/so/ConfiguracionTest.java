package pe.uni.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Configuracion.Carga;
import pe.uni.so.configuracion.Configuracion.Cpu;
import pe.uni.so.configuracion.Configuracion.Estrategia;
import pe.uni.so.configuracion.Configuracion.Pagina;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.configuracion.Configuracion.Ram;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.configuracion.RepositorioMaquinas;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.SolicitudES;
import pe.uni.so.es.SolicitudES.Operacion;
import pe.uni.so.es.VelocidadDispositivo;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.EspacioProceso;
import pe.uni.so.memoria.MapaBits;
import pe.uni.so.memoria.Reemplazo;
import pe.uni.so.motor.Kernel;
import pe.uni.so.procesos.Pcb;

class ConfiguracionTest {

    private static final long KIB = 1024;

    /** Una máquina igual a la por defecto salvo lo que se cambia. */
    private static Configuracion con(Ram ram, Pagina pagina, Cpu cpu, Politica politica, int quantum, Carga carga,
                                     Map<Dispositivo, VelocidadDispositivo> dispositivos) {
        return new Configuracion(ram, pagina, cpu, politica, quantum, 0.1, 10, Estrategia.PRIMER_AJUSTE, carga,
                Reemplazo.LRU, dispositivos, 42);
    }

    private static Configuracion con(Cpu cpu, Politica politica, int quantum) {
        return con(Ram.GIB_1, Pagina.KIB_64, cpu, politica, quantum, Carga.TOTAL, Configuracion.dispositivos(VelocidadDispositivo.NORMAL));
    }

    /** Proceso con un espacio pequeño (texto de 100 KiB) en páginas de {@code b}. */
    private static Pcb proceso(int pid, int llegada, int burst, long b) {
        Pcb p = new Pcb(pid, llegada, burst);
        p.asignarEspacio(new EspacioProceso(100 * KIB, 0.20, 0.10, b));
        return p;
    }

    private static Path carpetaTemporal() throws IOException { return Files.createTempDirectory("maquinas"); }

    @Test @DisplayName("[G5] Máquina por defecto: 1 GiB, páginas de 64 KiB (16 384 marcos), 1 GHz, RR q=4, primer ajuste, carga total, semilla 42")
    void porDefecto() {
        Configuracion c = Configuracion.porDefecto();
        assertEquals(Ram.GIB_1, c.ram());
        assertEquals(Pagina.KIB_64, c.pagina());
        assertEquals(16_384, c.marcos());
        assertEquals(Cpu.GHZ_1, c.cpu());
        assertEquals(1.0, c.cpu().instruccionesPorTick, 0);
        assertEquals("RR q=4", c.crearPolitica().nombre());
        assertEquals(10, c.grado());
        assertEquals(Carga.TOTAL, c.carga());
        assertFalse(c.crearGestorMemoria().bajoDemanda());
        assertEquals(42L, c.semilla());
        assertTrue(c.advertencias().isEmpty());
        assertEquals("Máquina del curso", Maquina.porDefecto().nombre());
    }

    @Test @DisplayName("[G5] RAM y página solo toman potencias de 2 válidas; toda combinación da marcos exactos")
    void opcionesDeMemoria() {
        for (Ram r : Ram.values()) {
            assertEquals(1, Long.bitCount(r.bytes));
            assertTrue(r.bytes <= 4L << 30, r + " pasa de 4 GiB");
            for (Pagina p : Pagina.values()) {
                MapaBits.validarBloque(p.bytes);                       // 32 KiB a 2 MiB, potencia de 2
                assertEquals(0L, r.bytes % p.bytes);
            }
        }
        assertEquals(131_072, con(Ram.GIB_4, Pagina.KIB_32, Cpu.GHZ_1, Politica.FCFS, 1, Carga.TOTAL,
                Configuracion.dispositivos(VelocidadDispositivo.NORMAL)).marcos());
        assertEquals(64, con(Ram.MIB_128, Pagina.MIB_2, Cpu.GHZ_1, Politica.FCFS, 1, Carga.TOTAL,
                Configuracion.dispositivos(VelocidadDispositivo.NORMAL)).marcos());
    }

    @Test @DisplayName("[G5] Fuera de rango se rechaza: quantum 1–20, costo 0–1 ms en pasos de 0,1, grado 1–50, semilla ≥ 0")
    void rangos() {
        Map<Dispositivo, VelocidadDispositivo> normal = Configuracion.dispositivos(VelocidadDispositivo.NORMAL);
        assertThrows(ParametroInvalidoException.class, () -> con(Cpu.GHZ_1, Politica.ROUND_ROBIN, 0));
        assertThrows(ParametroInvalidoException.class, () -> con(Cpu.GHZ_1, Politica.ROUND_ROBIN, 21));
        for (double costo : new double[] {-0.1, 1.1, 0.15, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(ParametroInvalidoException.class, () -> new Configuracion(Ram.GIB_1, Pagina.KIB_64, Cpu.GHZ_1,
                    Politica.FCFS, 4, costo, 10, Estrategia.PRIMER_AJUSTE, Carga.TOTAL, Reemplazo.LRU, normal, 42));
        for (int grado : new int[] {0, 51})
            assertThrows(ParametroInvalidoException.class, () -> new Configuracion(Ram.GIB_1, Pagina.KIB_64, Cpu.GHZ_1,
                    Politica.FCFS, 4, 0.1, grado, Estrategia.PRIMER_AJUSTE, Carga.TOTAL, Reemplazo.LRU, normal, 42));
        assertThrows(ParametroInvalidoException.class, () -> new Configuracion(Ram.GIB_1, Pagina.KIB_64, Cpu.GHZ_1,
                Politica.FCFS, 4, 0.1, 10, Estrategia.PRIMER_AJUSTE, Carga.TOTAL, Reemplazo.LRU, normal, -1));
        assertThrows(ParametroInvalidoException.class, () -> new Configuracion(null, Pagina.KIB_64, Cpu.GHZ_1,
                Politica.FCFS, 4, 0.1, 10, Estrategia.PRIMER_AJUSTE, Carga.TOTAL, Reemplazo.LRU, normal, 42));
        assertThrows(ParametroInvalidoException.class, () -> new Configuracion(Ram.GIB_1, Pagina.KIB_64, Cpu.GHZ_1,
                Politica.FCFS, 4, 0.1, 10, Estrategia.PRIMER_AJUSTE, Carga.TOTAL, Reemplazo.LRU,
                Map.of(Dispositivo.DISCO, VelocidadDispositivo.NORMAL), 42));            // faltan 4 dispositivos
        assertEquals(1.0, new Configuracion(Ram.GIB_1, Pagina.KIB_64, Cpu.GHZ_1, Politica.FCFS, 20, 1.0, 50,
                Estrategia.PRIMER_AJUSTE, Carga.TOTAL, Reemplazo.LRU, normal, 0).costoDespacho(), 0);
    }

    @Test @DisplayName("[G5][P6] La configuración arma el Kernel: RR q=2 reproduce el taller ej. 26")
    void kernelDeLaMaquina() {
        Kernel k = con(Cpu.GHZ_1, Politica.ROUND_ROBIN, 2).crearKernel(List.of(
                proceso(1, 0, 7, 64 * KIB), proceso(2, 0, 4, 64 * KIB), proceso(3, 0, 2, 64 * KIB)));
        k.ejecutarTodo();
        assertEquals("1 1 2 2 3 3 1 1 2 2 1 1 1", k.gantt());
        assertEquals(0.1, k.despachador().costo(), 1e-12);
    }

    @Test @DisplayName("[G5] La velocidad de la CPU cambia los ticks de cada ráfaga: 2 GHz la mitad, 500 MHz el doble")
    void velocidadDeCpu() {
        Pcb rapido = proceso(1, 0, 6, 64 * KIB);
        Kernel k2 = con(Cpu.GHZ_2, Politica.FCFS, 1).crearKernel(List.of(rapido));
        k2.ejecutarTodo();
        assertEquals("1 1 1", k2.gantt());
        assertEquals(3, rapido.fin());
        assertEquals(3, rapido.cpu());                                  // tiempo de CPU en ticks
        assertEquals(0, rapido.restante());

        Pcb lento = proceso(1, 0, 3, 64 * KIB);
        Kernel k05 = con(Cpu.MHZ_500, Politica.FCFS, 1).crearKernel(List.of(lento));
        k05.ejecutarTodo();
        assertEquals(6, lento.fin());
        assertEquals(6, lento.cpu());
        assertEquals(lento.retorno(), lento.cpu() + lento.espera() + lento.bloqueado());
    }

    @Test @DisplayName("[G5] Con 2 GHz y RR q=2 cada turno avanza 4 instrucciones y nadie ejecuta de más")
    void cpuRapidaConQuantum() {
        Pcb a = proceso(1, 0, 5, 64 * KIB), b = proceso(2, 0, 3, 64 * KIB);
        Kernel k = con(Cpu.GHZ_2, Politica.ROUND_ROBIN, 2).crearKernel(List.of(a, b));
        k.ejecutarTodo();
        assertEquals("1 1 2 2 1", k.gantt());                         // A: 4 instr; B: 3 (termina); A: 1
        assertEquals(5, a.fin());
        assertEquals(4, b.fin());
    }

    @Test @DisplayName("[G5][E5] Un disco lento duplica la duración de la E/S y uno rápido la reduce a la mitad")
    void velocidadDeDispositivos() {
        assertEquals(10, VelocidadDispositivo.LENTO.ajustar(5));
        assertEquals(5, VelocidadDispositivo.NORMAL.ajustar(5));
        assertEquals(3, VelocidadDispositivo.RAPIDO.ajustar(5));       // ⌈2,5⌉
        assertEquals(1, VelocidadDispositivo.RAPIDO.ajustar(1));
        assertThrows(ParametroInvalidoException.class, () -> VelocidadDispositivo.LENTO.ajustar(0));

        Map<Dispositivo, VelocidadDispositivo> discoLento = Configuracion.dispositivos(VelocidadDispositivo.NORMAL);
        discoLento.put(Dispositivo.DISCO, VelocidadDispositivo.LENTO);
        Pcb p = proceso(1, 0, 3, 64 * KIB);
        Kernel k = con(Ram.GIB_1, Pagina.KIB_64, Cpu.GHZ_1, Politica.FCFS, 1, Carga.TOTAL, discoLento).crearKernel(List.of(p));
        k.paso();                                                       // P ejecuta en t = 0
        k.llamadaSistema(new SolicitudES(1, Dispositivo.DISCO, Operacion.LEER, 0x8000, 512, 4));
        k.ejecutarTodo();
        assertEquals(8, p.bloqueado());                                 // 4 ticks a velocidad normal → 8
        assertEquals(VelocidadDispositivo.LENTO, k.gestorES().velocidad(Dispositivo.DISCO));
    }

    @Test @DisplayName("[G5][M7] El reemplazo elegido solo se usa con paginación bajo demanda")
    void reemplazoSegunCarga() {
        Map<Dispositivo, VelocidadDispositivo> normal = Configuracion.dispositivos(VelocidadDispositivo.NORMAL);
        assertFalse(con(Ram.GIB_1, Pagina.KIB_64, Cpu.GHZ_1, Politica.FCFS, 1, Carga.TOTAL, normal).crearGestorMemoria().bajoDemanda());
        assertTrue(con(Ram.GIB_1, Pagina.KIB_64, Cpu.GHZ_1, Politica.FCFS, 1, Carga.BAJO_DEMANDA, normal).crearGestorMemoria().bajoDemanda());
        assertTrue(Politica.ROUND_ROBIN.usaQuantum());
        assertFalse(Politica.SRTF.usaQuantum());
        assertTrue(Carga.BAJO_DEMANDA.usaReemplazo());
        assertFalse(Carga.TOTAL.usaReemplazo());
    }

    @Test @DisplayName("[G5] Antes de iniciar se rechazan procesos sin espacio, con otra página o más grandes que toda la RAM")
    void problemasDeLaCarga() {
        Configuracion c = con(Ram.MIB_128, Pagina.KIB_64, Cpu.GHZ_1, Politica.FCFS, 1, Carga.TOTAL,
                Configuracion.dispositivos(VelocidadDispositivo.NORMAL));     // 2 048 marcos
        Pcb sinEspacio = new Pcb(1, 0, 3);
        Pcb otraPagina = proceso(2, 0, 3, 32 * KIB);
        Pcb gigante = new Pcb(3, 0, 3);
        gigante.asignarEspacio(new EspacioProceso(200L * 1024 * KIB, 0.20, 0.10, 64 * KIB));   // 200 MiB de texto
        List<String> errores = c.problemas(List.of(sinEspacio, otraPagina, gigante));
        assertEquals(3, errores.size());
        assertTrue(errores.get(0).startsWith("P1"));
        assertTrue(errores.get(1).contains("32 KiB"));
        assertTrue(errores.get(2).contains("2048 marcos"));
        ParametroInvalidoException ex = assertThrows(ParametroInvalidoException.class, () -> c.crearKernel(List.of(gigante)));
        assertTrue(ex.getMessage().contains("P3"));
        assertThrows(ParametroInvalidoException.class, () -> c.crearKernel(List.of()));
    }

    @Test @DisplayName("[G5] Las advertencias señalan la máquina modesta, la CPU lenta y los dispositivos lentos")
    void advertencias() {
        Configuracion modesta = con(Ram.MIB_128, Pagina.KIB_64, Cpu.MHZ_500, Politica.FCFS, 1, Carga.TOTAL,
                Configuracion.dispositivos(VelocidadDispositivo.LENTO));
        assertEquals(3, modesta.advertencias().size());
    }

    @Test @DisplayName("[G5] Una máquina se guarda en un archivo y se vuelve a leer igual")
    void guardarYLeer() throws IOException {
        Map<Dispositivo, VelocidadDispositivo> mixta = Configuracion.dispositivos(VelocidadDispositivo.NORMAL);
        mixta.put(Dispositivo.RED, VelocidadDispositivo.RAPIDO);
        Maquina m = new Maquina("PC modesta", new Configuracion(Ram.MIB_256, Pagina.KIB_128, Cpu.MHZ_500,
                Politica.SRTF, 7, 0.5, 3, Estrategia.PEOR_AJUSTE, Carga.BAJO_DEMANDA, Reemplazo.FIFO, mixta, 2026));
        Path archivo = carpetaTemporal().resolve("pc-modesta.maquina");
        m.escribir(archivo);
        assertEquals(m, Maquina.leer(archivo));
    }

    @Test @DisplayName("[G5] Un archivo con un valor inválido o incompleto se rechaza con un mensaje claro")
    void archivoInvalido() throws IOException {
        Path archivo = carpetaTemporal().resolve("rota.maquina");
        Maquina.porDefecto().escribir(archivo);
        String texto = Files.readString(archivo, StandardCharsets.UTF_8).replace("ram=GIB_1", "ram=MIB_200");
        Files.writeString(archivo, texto, StandardCharsets.UTF_8);
        ParametroInvalidoException ex = assertThrows(ParametroInvalidoException.class, () -> Maquina.leer(archivo));
        assertTrue(ex.getMessage().contains("MIB_200"));
        Files.writeString(archivo, texto.replace("ram=MIB_200", "ram=GIB_1").replace("quantum=4", "quantum=99"), StandardCharsets.UTF_8);
        assertThrows(ParametroInvalidoException.class, () -> Maquina.leer(archivo));
        Files.writeString(archivo, "nombre=Incompleta\n", StandardCharsets.UTF_8);
        assertThrows(ParametroInvalidoException.class, () -> Maquina.leer(archivo));
    }

    @Test @DisplayName("[G5] El gestor ofrece la máquina por defecto primero; se edita y se restaura, pero no se elimina")
    void repositorioPorDefecto() throws IOException {
        RepositorioMaquinas repo = new RepositorioMaquinas(carpetaTemporal());
        assertEquals(List.of(Maquina.porDefecto()), repo.listar());
        Configuracion d = Configuracion.porDefecto();
        Maquina editada = new Maquina(Maquina.NOMBRE_POR_DEFECTO, new Configuracion(Ram.GIB_2, d.pagina(), d.cpu(),
                d.politica(), d.quantum(), d.costoDespacho(), d.grado(), d.estrategia(), d.carga(), d.reemplazo(), d.dispositivos(), d.semilla()));
        repo.guardar(editada);
        assertEquals(Ram.GIB_2, repo.porDefecto().configuracion().ram());
        assertEquals(1, repo.listar().size());
        assertThrows(EventoInvalidoException.class, () -> repo.eliminar(Maquina.NOMBRE_POR_DEFECTO));
        assertEquals(Maquina.porDefecto(), repo.restaurarPorDefecto());
        assertEquals(Ram.GIB_1, repo.porDefecto().configuracion().ram());
        assertThrows(EventoInvalidoException.class, () -> repo.guardar(Maquina.porDefecto().conNombre("maquina del curso")));
    }

    @Test @DisplayName("[G5] Nueva, clonar y eliminar máquinas; los nombres no se repiten")
    void repositorioOtrasMaquinas() throws IOException {
        RepositorioMaquinas repo = new RepositorioMaquinas(carpetaTemporal());
        Configuracion d = Configuracion.porDefecto();
        repo.guardar(new Maquina("Zeta", d));
        Maquina copia = repo.clonar(Maquina.NOMBRE_POR_DEFECTO, "PC modesta");
        assertEquals(d, copia.configuracion());
        assertEquals(List.of(Maquina.NOMBRE_POR_DEFECTO, "PC modesta", "Zeta"), repo.listar().stream().map(Maquina::nombre).toList());
        assertThrows(EventoInvalidoException.class, () -> repo.clonar("Zeta", "pc MODESTA"));
        assertThrows(EventoInvalidoException.class, () -> repo.clonar("No existe", "Otra"));
        repo.eliminar("Zeta");
        assertFalse(repo.existe("Zeta"));
        assertThrows(EventoInvalidoException.class, () -> repo.eliminar("Zeta"));
        assertThrows(ParametroInvalidoException.class, () -> new Maquina("  ", d));
        assertThrows(ParametroInvalidoException.class, () -> new Maquina("x".repeat(41), d));
    }
}
