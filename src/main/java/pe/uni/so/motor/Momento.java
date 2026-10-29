package pe.uni.so.motor;

/**
 * [P6] Los 5 momentos en que se invoca al planificador de corto plazo (Sesión 6,
 * «Esquemas de planificación»). En a y e el propio proceso deja la CPU, así que siempre hay
 * que elegir a otro; b, c y d son los que vuelven expropiativo al planificador.
 */
public enum Momento {
    A("el proceso en ejecución pide E/S y pasa a Bloqueado"),
    B("el proceso en ejecución vuelve a Listo (fin de quantum o expropiación)"),
    C("un proceso termina su E/S y pasa de Bloqueado a Listo"),
    D("un proceso nuevo es admitido y pasa a Listo"),
    E("el proceso en ejecución termina");

    public final String descripcion;

    Momento(String descripcion) { this.descripcion = descripcion; }

    /** b, c y d: con una política expropiativa se compara el candidato contra el que ejecuta. */
    public boolean puedeExpropiar() { return this == B || this == C || this == D; }
}
