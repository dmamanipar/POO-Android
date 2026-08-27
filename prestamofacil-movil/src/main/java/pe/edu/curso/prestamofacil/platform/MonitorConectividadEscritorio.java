package pe.edu.curso.prestamofacil.platform;

import java.net.InetSocketAddress;
import java.net.Socket;

/** Fallback simple para desarrollo en escritorio: intenta abrir un socket a un host conocido. */
public class MonitorConectividadEscritorio implements MonitorConectividad {

    @Override
    public boolean hayConexion() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void alRecuperarConexion(Runnable callback) {
        // En escritorio no hay listener nativo; SincronizacionService puede
        // sondear hayConexion() periódicamente o el usuario dispara la
        // sincronización manualmente. No se implementa polling aquí para no
        // introducir un hilo de fondo permanente sin necesidad real.
    }
}
