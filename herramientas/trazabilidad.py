"""Matriz de trazabilidad: lee los reportes de JUnit y cruza los códigos [P5] de cada prueba
con los 48 requisitos del enunciado. Uso: python3 herramientas/trazabilidad.py target/surefire-reports"""
import glob
import re
import sys
import xml.etree.ElementTree as ET

PAQUETES = {
    "A Ingreso": [f"I{i}" for i in range(1, 7)],
    "B Procesos": [f"P{i}" for i in list(range(1, 15)) + [16, 17, 18]],
    "C Memoria": [f"M{i}" for i in range(1, 9)],
    "D E/S": [f"E{i}" for i in range(1, 12)],
}

carpeta = sys.argv[1] if len(sys.argv) > 1 else "target/surefire-reports"
pruebas = {}  # código -> lista de (nombre, pasa)
for archivo in glob.glob(f"{carpeta}/TEST-*.xml"):
    for caso in ET.parse(archivo).getroot().iter("testcase"):
        nombre = caso.get("name", "")
        pasa = caso.find("failure") is None and caso.find("error") is None
        for codigo in re.findall(r"\[([A-Z]\d+)\]", nombre):
            pruebas.setdefault(codigo, []).append((nombre, pasa))

print("## Matriz de trazabilidad\n")
print("| Paquete | Requisito | Pruebas | Estado |")
print("| --- | --- | --- | --- |")
cubiertos = total = 0
for paquete, codigos in PAQUETES.items():
    for codigo in codigos:
        total += 1
        lista = pruebas.get(codigo, [])
        if not lista:
            estado = "sin prueba"
        elif all(p for _, p in lista):
            estado = "pasa"
            cubiertos += 1
        else:
            estado = "FALLA"
        print(f"| {paquete} | {codigo} | {len(lista)} | {estado} |")
print(f"\n**Requisitos del simulador con pruebas que pasan: {cubiertos} de {total} ({100 * cubiertos // total} %).**")
