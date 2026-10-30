# Simulador de Sistema Operativo — SW407 (UNI, 2026-2)

Simulador de gestión de **procesos, memoria y dispositivos de E/S** para el Trabajo Final de Sistemas Operativos. Motor en Java 17 puro (sin interfaz) e interfaz en JavaFX con CSS.

| Seguimiento | Documentos |
| --- | --- |
| [Notion — requisitos, pruebas y decisiones](https://app.notion.com/p/4022a793ea1981769170d354d72d1722) | [Drive — proyecto final](https://drive.google.com/drive/folders/1Whkfyop0zoCWvsbbNxIhk6anHhCM3zdJ) |

## Requisitos para compilar

- JDK 17 o superior (recomendado: Temurin 21). Trae `jpackage`.
- Maven 3.9 o superior.

## Comandos

| Comando | Qué hace |
| --- | --- |
| `mvn verify` | Compila y corre todas las pruebas (JUnit 5) |
| `mvn javafx:run` | Abre la interfaz |
| `mvn package` | Genera `target/libs/simulador-so.jar` con sus dependencias |
| `jpackage --type app-image --name SimuladorSO --input target/libs --main-jar simulador-so.jar --main-class pe.uni.so.Lanzador --dest dist` | Genera `dist/SimuladorSO/SimuladorSO.exe` |
| `python herramientas/trazabilidad.py target/surefire-reports` | Matriz requisito → pruebas → estado |

GitHub Actions corre `mvn verify` y la matriz de trazabilidad en cada push y PR, y genera el `.exe` de Windows en cada push a `main` (artefacto `SimuladorSO-windows`).

## Estructura

```text
src/main/java/pe/uni/so/
  procesos/        Estado (5 estados, 6 transiciones), Pcb, CampoPcb (22 campos en memoria), Colas
  motor/           Kernel (tick a tick, los 5 momentos a–e de invocación del planificador), Momento, Modo
  planificacion/   PoliticaPlanificacion (Strategy): Fcfs, Sjf, Srtf, Prioridad, RoundRobin; SimuladorCpu
  memoria/         EstrategiaAsignacion (Strategy): primer, mejor y peor ajuste; MapaBits
  interrupciones/  TipoInterrupcion (tabla de vectores)
  es/              Gestor de E/S, dispositivos y señales (pendiente)
  aleatorio/       Lcg (generador con semilla), Ecuaciones
  excepciones/     Errores propios del simulador
  ui/              App JavaFX + estilos.css
src/test/java/     Pruebas: ejercicios del profesor, requisitos e invariantes
herramientas/      trazabilidad.py
```

## Reglas del equipo

1. Cada cambio sale de una tarjeta de Notion y usa su código (P5, M6…).
2. Rama `feat/<código>-<tema>` desde `develop`; PR hacia `develop` con la plantilla.
3. Cada prueba lleva `@DisplayName("[CÓDIGO] …")`; si reproduce un ejercicio del profesor, usa sus valores exactos.
4. El motor no importa nada de `ui` ni de JavaFX.
5. `main` solo recibe merges desde `develop` con todas las pruebas en verde.

## Estado al 29/10/2026

Fase 2 (procesos sin E/S) en `develop`: estados, PCB de 22 campos, colas, las 4 políticas en contexto expropiativo y no expropiativo, despachador con costo y bit de modo, planificadores de largo y mediano plazo, y `Kernel` con los 5 momentos de invocación. Pruebas: taller 2026-2 ej. 2, 5, 9, 10 b, 11, 16, 17, 20, 21, 22, 23, 24, 26 y 27; PC2 25-2 preg. 3; PC2 26-1 preg. 22; PC3 25-I preg. 2; rangos de las ecuaciones aleatorias. Siguiente: Fase 3, E/S e interrupciones.

