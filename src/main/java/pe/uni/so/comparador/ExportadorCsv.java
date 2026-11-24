package pe.uni.so.comparador;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import pe.uni.so.excepciones.ParametroInvalidoException;

/**
 * [G1] Exporta la comparación a CSV para las tablas del informe: UTF-8 con BOM (Excel reconoce
 * las tildes) y separador «;». Primero va el bloque de parámetros (máquina, carga, semilla,
 * procesos) y luego una fila por corrida con todas las {@link Columna}s.
 *
 * <p>El separador decimal es el de la configuración regional (en una PC en español, la coma),
 * para que Excel lea los números como números. Nunca puede ser el «;».
 */
public final class ExportadorCsv {

    public static final char SEPARADOR = ';';
    private static final String BOM = "﻿";

    /** Parámetros de la comparación que encabezan el archivo. */
    public record Parametros(String maquina, String carga, long semilla, int procesos) {}

    private final char decimal;

    /** Con el separador decimal de la configuración regional. */
    public ExportadorCsv() { this(DecimalFormatSymbols.getInstance().getDecimalSeparator()); }

    public ExportadorCsv(char decimal) {
        if (decimal == SEPARADOR || decimal == '"' || Character.isLetterOrDigit(decimal) || Character.isWhitespace(decimal))
            throw new ParametroInvalidoException("Separador decimal inválido para el CSV: «" + decimal + "»");
        this.decimal = decimal;
    }

    /** El contenido del CSV (sin BOM). */
    public String texto(Parametros p, List<Resultado> resultados) {
        if (p == null) throw new ParametroInvalidoException("Faltan los parámetros de la comparación");
        if (resultados == null || resultados.isEmpty()) throw new ParametroInvalidoException("No hay resultados para exportar");
        StringBuilder sb = new StringBuilder();
        linea(sb, List.of("Comparación de configuraciones — simulador de SO (SW407)"));
        linea(sb, List.of("Máquina", p.maquina()));
        linea(sb, List.of("Carga", p.carga()));
        linea(sb, List.of("Semilla", Long.toString(p.semilla())));
        linea(sb, List.of("Procesos", Integer.toString(p.procesos())));
        sb.append("\r\n");
        List<String> cabecera = new ArrayList<>(List.of("Serie", "Política", "Estrategia", "Procesos", "Terminados"));
        for (Columna c : Columna.values()) cabecera.add(c.etiqueta);
        cabecera.add("Mejor en");
        cabecera.add("Nota");
        linea(sb, cabecera);
        for (Resultado r : resultados) {
            List<String> celdas = new ArrayList<>(List.of(r.serie().etiqueta, r.politica(), r.estrategia(),
                    Integer.toString(r.procesos()), Integer.toString(r.terminados())));
            List<String> mejores = new ArrayList<>();
            for (Columna c : Columna.values()) {
                celdas.add(numero(c.valor(r), c.decimales));
                if (c.esMejor(r, resultados)) mejores.add(c.etiqueta);
            }
            celdas.add(String.join(", ", mejores));
            celdas.add(r.nota());
            linea(sb, celdas);
        }
        return sb.toString();
    }

    /** Escribe el CSV (con BOM), reemplazando el archivo si ya existe. */
    public void escribir(Path archivo, Parametros p, List<Resultado> resultados) throws IOException {
        String contenido = texto(p, resultados);
        Path carpeta = archivo.toAbsolutePath().getParent();
        if (carpeta != null) Files.createDirectories(carpeta);
        try (Writer w = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8)) {
            w.write(BOM);
            w.write(contenido);
        }
    }

    String numero(double v, int decimales) {
        String s = BigDecimal.valueOf(v).setScale(decimales, RoundingMode.HALF_UP).toPlainString();
        return decimal == '.' ? s : s.replace('.', decimal);
    }

    private static void linea(StringBuilder sb, List<String> celdas) {
        for (int i = 0; i < celdas.size(); i++) {
            if (i > 0) sb.append(SEPARADOR);
            sb.append(celda(celdas.get(i)));
        }
        sb.append("\r\n");
    }

    /**
     * Entre comillas si lleva separador, comillas o saltos de línea (RFC 4180). Un texto que empieza
     * con = + - @ lleva un apóstrofo delante para que Excel no lo tome como fórmula (los números del
     * comparador nunca son negativos).
     */
    static String celda(String s) {
        if (s == null) return "";
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.indexOf(SEPARADOR) < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0 && s.indexOf('\r') < 0) return s;
        return '"' + s.replace("\"", "\"\"") + '"';
    }
}
