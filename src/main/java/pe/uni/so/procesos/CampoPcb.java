package pe.uni.so.procesos;

/**
 * [P3] Disposición en memoria de la PCB: 22 campos con su desplazamiento en bytes.
 * La tabla de PCB vive en la región del kernel: PCB(pid) = base_tabla + pid × 128.
 * Los campos agrupados (registros, burst, prioridad) ocupan varios bytes contiguos.
 */
public enum CampoPcb {
    PID(0x00, "PID"),
    PPID(0x04, "PID del padre"),
    TIPO(0x08, "Tipo (SISTEMA o USUARIO)"),
    ESTADO(0x0C, "Estado"),
    PC(0x10, "Contador de programa (PC)"),
    REGISTROS(0x14, "Registros R1–R4"),
    SP(0x24, "Puntero de pila (SP)"),
    BURST(0x28, "Burst total y restante"),
    PRIORIDAD(0x2C, "Prioridad (estática y efectiva)"),
    QUANTUM_RESTANTE(0x30, "Quantum restante"),
    LLEGADA(0x34, "Instante de llegada"),
    PRIMERA_CPU(0x38, "Instante de primera CPU"),
    FIN(0x3C, "Instante de fin"),
    CPU(0x40, "CPU consumida"),
    ESPERA(0x44, "Tiempo en listos (espera)"),
    BLOQUEADO(0x48, "Tiempo bloqueado"),
    TABLA_PAGINAS(0x4C, "Puntero a tabla de páginas"),
    SEGMENTOS(0x50, "Tamaños de segmentos"),
    MARCOS(0x54, "Marcos asignados y desperdicio"),
    DISPOSITIVO(0x58, "Dispositivo esperado"),
    PEDIDOS_ES(0x5C, "Pedidos de E/S pendientes"),
    ERROR_E_INTERRUPCIONES(0x60, "Código de error y contador de interrupciones");

    /** Bytes reservados por PCB en la tabla (los 22 campos usan 100; el resto queda libre). */
    public static final int TAMANO_PCB = 128;

    public final int desplazamiento;
    public final String descripcion;

    CampoPcb(int desplazamiento, String descripcion) {
        this.desplazamiento = desplazamiento;
        this.descripcion = descripcion;
    }

    /** Bytes que ocupa: hasta el siguiente campo; el último ocupa 4. */
    public int tamano() {
        CampoPcb[] c = values();
        return ordinal() + 1 < c.length ? c[ordinal() + 1].desplazamiento - desplazamiento : 4;
    }

    /** Dirección física de la PCB de un proceso dentro de la región del kernel. */
    public static int direccion(int baseTabla, int pid) { return baseTabla + pid * TAMANO_PCB; }
}
