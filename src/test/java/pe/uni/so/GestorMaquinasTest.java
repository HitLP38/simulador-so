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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.uni.so.configuracion.Configuracion;
import pe.uni.so.configuracion.Configuracion.Carga;
import pe.uni.so.configuracion.Configuracion.Pagina;
import pe.uni.so.configuracion.Configuracion.Politica;
import pe.uni.so.configuracion.Configuracion.Ram;
import pe.uni.so.configuracion.FichaMaquina;
import pe.uni.so.configuracion.FormularioMaquina;
import pe.uni.so.configuracion.GestorMaquinas;
import pe.uni.so.configuracion.Maquina;
import pe.uni.so.configuracion.RepositorioMaquinas;
import pe.uni.so.es.Dispositivo;
import pe.uni.so.es.VelocidadDispositivo;
import pe.uni.so.excepciones.EventoInvalidoException;
import pe.uni.so.excepciones.ParametroInvalidoException;
import pe.uni.so.memoria.Reemplazo;

class GestorMaquinasTest {

    private static GestorMaquinas gestor() throws IOException {
        return new GestorMaquinas(new RepositorioMaquinas(Files.createTempDirectory("gestor")));
    }

    private static List<String> nombres(GestorMaquinas g) { return g.maquinas().stream().map(Maquina::nombre).toList(); }

    @Test @DisplayName("[G5] Al abrir el gestor, la máquina por defecto está primero y preseleccionada")
    void porDefectoPreseleccionada() throws IOException {
        GestorMaquinas g = gestor();
        g.crear(new Maquina("Alfa", Configuracion.porDefecto()));
        assertEquals(List.of(Maquina.NOMBRE_POR_DEFECTO, "Alfa"), nombres(g));
        assertEquals(0, g.indiceASeleccionar(null));
        assertEquals(1, g.indiceASeleccionar("ALFA"));               // tras crear/editar se selecciona esa
        assertEquals(0, g.indiceASeleccionar("Borrada"));            // si ya no existe, la por defecto
    }

    @Test @DisplayName("[G5] El nombre se valida mientras se escribe: vacío, largo, reservado, repetido o de un archivo dañado")
    void validarNombre() throws IOException {
        GestorMaquinas g = gestor();
        g.crear(new Maquina("Alfa", Configuracion.porDefecto()));
        assertTrue(g.errorNombre("  ", null).isPresent());
        assertTrue(g.errorNombre("x".repeat(41), null).isPresent());
        assertTrue(g.errorNombre("maquina del curso", null).orElseThrow().contains("reservado"));
        assertTrue(g.errorNombre("alfa", null).orElseThrow().contains("Ya existe"));
        assertTrue(g.errorNombre("Alfa", "Alfa").isEmpty());          // configurar sin cambiar el nombre
        assertTrue(g.errorNombre("ALFA", "Alfa").isEmpty());          // solo cambia mayúsculas: mismo archivo
        assertTrue(g.errorNombre("Beta", null).isEmpty());
        assertTrue(g.errorNombre("Otro", Maquina.NOMBRE_POR_DEFECTO).orElseThrow().contains("no se puede renombrar"));
        assertTrue(g.errorNombre(Maquina.NOMBRE_POR_DEFECTO, Maquina.NOMBRE_POR_DEFECTO).isEmpty());
        Files.writeString(g.repositorio().carpeta().resolve("rota.maquina"), "nombre=Rota\n", StandardCharsets.UTF_8);
        assertTrue(g.errorNombre("Rota", null).orElseThrow().contains("archivo dañado"));
        assertThrows(EventoInvalidoException.class, () -> g.crear(new Maquina("alfa", Configuracion.porDefecto())));
    }

    @Test @DisplayName("[G5] Configurar con otro nombre renombra el archivo; la por defecto no se renombra")
    void configurarYRenombrar() throws IOException {
        GestorMaquinas g = gestor();
        Configuracion d = Configuracion.porDefecto();
        g.crear(new Maquina("Alfa", d));
        FormularioMaquina f = FormularioMaquina.configurar(g, g.repositorio().buscar("Alfa").orElseThrow());
        f.nombre("Gamma");
        f.ram(Ram.GIB_2);
        Maquina editada = f.construir();
        g.actualizar("Alfa", editada);
        assertEquals(List.of(Maquina.NOMBRE_POR_DEFECTO, "Gamma"), nombres(g));
        assertEquals(Ram.GIB_2, g.repositorio().buscar("Gamma").orElseThrow().configuracion().ram());
        assertFalse(Files.exists(g.repositorio().carpeta().resolve("alfa.maquina")));
        assertThrows(EventoInvalidoException.class, () -> g.actualizar(Maquina.NOMBRE_POR_DEFECTO, new Maquina("Otra", d)));
        assertThrows(EventoInvalidoException.class, () -> g.actualizar("No existe", new Maquina("X", d)));
        g.actualizar(Maquina.NOMBRE_POR_DEFECTO, new Maquina(Maquina.NOMBRE_POR_DEFECTO, editada.configuracion()));
        assertEquals(Ram.GIB_2, g.repositorio().porDefecto().configuracion().ram());
        assertEquals(Ram.GIB_1, g.restaurarPorDefecto().configuracion().ram());
    }

    @Test @DisplayName("[G5] Clonar sugiere «Copia de X», «Copia de X (2)» y no pasa de 40 caracteres")
    void clonarConNombreSugerido() throws IOException {
        GestorMaquinas g = gestor();
        String primero = g.nombreParaCopia(Maquina.NOMBRE_POR_DEFECTO);
        assertEquals("Copia de Máquina del curso", primero);
        g.clonar(Maquina.NOMBRE_POR_DEFECTO, primero);
        assertEquals("Copia de Máquina del curso (2)", g.nombreParaCopia(Maquina.NOMBRE_POR_DEFECTO));
        String largo = "Una máquina con un nombre muy largo xx";       // 38 caracteres
        g.crear(new Maquina(largo, Configuracion.porDefecto()));
        String copia = g.nombreParaCopia(largo);
        assertTrue(copia.length() <= Maquina.LARGO_MAXIMO_NOMBRE);
        assertTrue(copia.startsWith(GestorMaquinas.PREFIJO_COPIA));
        g.clonar(largo, copia);
        String otra = g.nombreParaCopia(largo);
        assertTrue(otra.endsWith(" (2)") && otra.length() <= Maquina.LARGO_MAXIMO_NOMBRE, otra);
        assertThrows(EventoInvalidoException.class, () -> g.clonar(largo, copia));
    }

    @Test @DisplayName("[G5] Una máquina abierta no se configura ni elimina; al cerrarla sí")
    void maquinaAbiertaProtegida() throws IOException {
        GestorMaquinas g = gestor();
        Configuracion d = Configuracion.porDefecto();
        g.crear(new Maquina("Alfa", d));
        assertTrue(g.abrir("Alfa"));
        assertFalse(g.abrir("alfa"));                                 // ya estaba abierta: se trae al frente
        assertTrue(g.estaAbierta("Alfa"));
        assertFalse(g.sePuedeEliminar("Alfa"));
        assertThrows(EventoInvalidoException.class, () -> g.eliminar("Alfa"));
        assertThrows(EventoInvalidoException.class, () -> g.actualizar("Alfa", new Maquina("Alfa", d)));
        assertTrue(g.repositorio().existe("Alfa"));
        g.clonar("Alfa", "Beta");                                     // clonar sí se puede
        g.cerrar("Alfa");
        assertTrue(g.sePuedeEliminar("Alfa"));
        g.eliminar("Alfa");
        assertEquals(List.of(Maquina.NOMBRE_POR_DEFECTO, "Beta"), nombres(g));
        assertFalse(g.sePuedeEliminar(Maquina.NOMBRE_POR_DEFECTO));
        g.abrir(Maquina.NOMBRE_POR_DEFECTO);
        assertThrows(EventoInvalidoException.class, g::restaurarPorDefecto);
    }

    @Test @DisplayName("[G5] Los archivos dañados se listan aparte y se pueden eliminar desde el gestor")
    void archivosDanadosEnElGestor() throws IOException {
        GestorMaquinas g = gestor();
        Path rota = g.repositorio().carpeta().resolve("rota.maquina");
        Files.writeString(rota, "nombre=Rota\nram=MIB_200\n", StandardCharsets.UTF_8);
        assertEquals(List.of(Maquina.NOMBRE_POR_DEFECTO), nombres(g));
        assertEquals(1, g.archivosDanados().size());
        g.eliminarArchivoDanado(rota);
        assertTrue(g.archivosDanados().isEmpty());
        assertTrue(g.errorNombre("Rota", null).isEmpty());          // el nombre queda libre
    }

    @Test @DisplayName("[G5] El formulario solo ofrece opciones válidas: quantum con RR, reemplazo bajo demanda, costos 0–1 ms")
    void formularioOpcionesValidas() throws IOException {
        GestorMaquinas g = gestor();
        FormularioMaquina f = FormularioMaquina.nueva(g);
        assertEquals("Máquina nueva", f.nombre());
        assertTrue(f.valido());
        assertEquals(11, FormularioMaquina.costos().size());
        assertEquals(0.0, FormularioMaquina.costos().get(0));
        assertEquals(1.0, FormularioMaquina.costos().get(10));
        assertEquals("0,1 ms", FormularioMaquina.etiquetaCosto(0.1));
        assertTrue(f.quantumHabilitado());                            // la por defecto usa Round Robin
        f.politica(Politica.FCFS);
        assertFalse(f.quantumHabilitado());
        assertFalse(f.reemplazoHabilitado());                         // la por defecto usa carga total
        f.carga(Carga.BAJO_DEMANDA);
        assertTrue(f.reemplazoHabilitado());
        f.ram(Ram.MIB_128);
        f.pagina(Pagina.MIB_2);
        assertEquals(64, f.marcos());
        assertThrows(ParametroInvalidoException.class, () -> f.quantum(0));
        assertThrows(ParametroInvalidoException.class, () -> f.quantum(21));
        assertThrows(ParametroInvalidoException.class, () -> f.grado(51));
        assertThrows(ParametroInvalidoException.class, () -> f.costoDespacho(0.15));
        f.quantum(20);
        f.grado(50);
        f.costoDespacho(0.3);
        f.reemplazo(Reemplazo.FIFO);
        f.dispositivo(Dispositivo.DISCO, VelocidadDispositivo.LENTO);
        Maquina m = f.construir();
        assertEquals(20, m.configuracion().quantum());
        assertEquals(0.3, m.configuracion().costoDespacho());
        assertEquals(VelocidadDispositivo.LENTO, m.configuracion().dispositivos().get(Dispositivo.DISCO));
        g.crear(m);
        assertEquals("Máquina nueva 2", FormularioMaquina.nueva(g).nombre());
    }

    @Test @DisplayName("[G5] El formulario bloquea «Aceptar» con nombre o semilla inválidos y muestra los avisos")
    void formularioErroresYAvisos() throws IOException {
        GestorMaquinas g = gestor();
        g.crear(new Maquina("Alfa", Configuracion.porDefecto()));
        FormularioMaquina f = FormularioMaquina.nueva(g);
        f.nombre("alfa");
        f.semilla("");
        assertEquals(2, f.errores().size());
        assertThrows(ParametroInvalidoException.class, f::construir);
        assertTrue(f.avisos().isEmpty());                             // sin semilla no hay máquina que evaluar
        f.semilla("12a");
        assertTrue(f.errores().get(1).contains("dígitos"));
        f.semilla("9".repeat(19));
        assertTrue(f.errores().get(1).contains("18"));
        f.nombre("Modesta");
        f.semilla("0");
        assertTrue(f.valido());
        f.ram(Ram.MIB_128);
        f.cpu(Configuracion.Cpu.MHZ_500);
        List<String> avisos = f.avisos();
        assertTrue(avisos.stream().anyMatch(a -> a.contains("128 MiB")), avisos.toString());
        assertTrue(avisos.stream().anyMatch(a -> a.contains("CPU lenta")), avisos.toString());
        assertTrue(avisos.get(avisos.size() - 1).startsWith("Lote oficial: Caben"), avisos.toString());
        FormularioMaquina conf = FormularioMaquina.configurar(g, g.repositorio().buscar("Alfa").orElseThrow());
        assertTrue(conf.valido());                                    // su propio nombre no choca
    }

    @Test @DisplayName("[G5][P17] La ficha resume la máquina y corre el lote oficial completo en la máquina por defecto")
    void fichaYLoteOficial() {
        Configuracion d = Configuracion.porDefecto();
        assertEquals("1 GiB · 1 GHz · Round Robin q=4 · carga total", FichaMaquina.resumen(d));
        List<FichaMaquina.Fila> filas = FichaMaquina.filas(d);
        assertTrue(filas.contains(new FichaMaquina.Fila("Memoria", "Marcos", "16384")));
        assertTrue(filas.contains(new FichaMaquina.Fila("Memoria", "Reemplazo", "— (carga total)")));
        assertTrue(filas.contains(new FichaMaquina.Fila("CPU", "Quantum", "4 ticks")));
        assertTrue(filas.contains(new FichaMaquina.Fila("CPU", "Cambio de contexto", "0,1 ms")));
        assertEquals(5, filas.stream().filter(f -> f.seccion().equals("Dispositivos")).count());
        FichaMaquina.Prueba p = FichaMaquina.probarLoteOficial(d);
        assertTrue(p.ok(), p.mensaje());
        assertEquals(20, p.terminados());
        assertTrue(p.ticks() > 0 && !p.gantt().isEmpty());
    }
}
