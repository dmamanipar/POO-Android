package pe.edu.curso.prestamofacil.platform;

public interface MonitorConectividad {

    boolean hayConexion();

    /** Registra un callback que se invoca cuando el dispositivo recupera la conexión. */
    void alRecuperarConexion(Runnable callback);
}
