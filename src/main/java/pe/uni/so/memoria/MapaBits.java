package pe.uni.so.memoria;

import java.util.ArrayList;
import java.util.List;
import pe.uni.so.excepciones.MemoriaInsuficienteException;

/** [M5] Mapa de bits: un bit por marco de 2^n bytes. [I2][I5] bloques y desperdicio. */
public class MapaBits {
    public record Hueco(int inicio, int tamano) {}

    private final boolean[] ocupado;
    private long comparacionesAcumuladas;

    public MapaBits(int marcos) { ocupado = new boolean[marcos]; }

    /** [I2] El bloque debe ser potencia de 2 entre 32 KiB y 2 MiB. */
    public static void validarBloque(long bytes) {
        if (bytes < 32 * 1024 || bytes > 2 * 1024 * 1024 || Long.bitCount(bytes) != 1)
            throw new IllegalArgumentException("Bloque inválido: " + bytes + " B (potencia de 2 entre 32 KiB y 2 MiB)");
    }

    /** [I3] Bloques que ocupa un segmento: techo(tamaño / bloque). */
    public static long bloques(long tamano, long bloque) { return (tamano + bloque - 1) / bloque; }

    /** [I5] Fragmentación interna: espacio sin usar en el último bloque. */
    public static long desperdicio(long tamano, long bloque) { return bloques(tamano, bloque) * bloque - tamano; }

    public List<Hueco> huecos() {
        List<Hueco> lista = new ArrayList<>();
        for (int i = 0; i < ocupado.length; ) {
            if (ocupado[i]) { i++; continue; }
            int j = i;
            while (j < ocupado.length && !ocupado[j]) j++;
            lista.add(new Hueco(i, j - i));
            i = j;
        }
        return lista;
    }

    /** Marca k marcos contiguos en el hueco que elija la estrategia; devuelve el primer marco. */
    public int asignar(int k, EstrategiaAsignacion estrategia) {
        List<Hueco> huecos = huecos();
        var eleccion = estrategia.elegir(huecos.stream().map(Hueco::tamano).toList(), k);
        comparacionesAcumuladas += eleccion.comparaciones();
        if (eleccion.indice() < 0) throw new MemoriaInsuficienteException(k, libres());
        int inicio = huecos.get(eleccion.indice()).inicio();
        for (int i = inicio; i < inicio + k; i++) ocupado[i] = true;
        return inicio;
    }

    public void liberar(int inicio, int k) { for (int i = inicio; i < inicio + k; i++) ocupado[i] = false; }

    public int libres() { int n = 0; for (boolean b : ocupado) if (!b) n++; return n; }
    public int total() { return ocupado.length; }
    public long comparacionesAcumuladas() { return comparacionesAcumuladas; }
}
