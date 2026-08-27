package pe.edu.curso.prestamofacil.platform;

import com.gluonhq.attach.util.Platform;

/** Punto único de decisión escritorio-vs-móvil; nadie más debe hacer new sobre estas clases. */
public final class FabricaPlataforma {

    private static Notificador notificador;
    private static MonitorConectividad monitorConectividad;

    private FabricaPlataforma() { }

    public static synchronized Notificador notificador() {
        if (notificador == null) {
            notificador = Platform.isDesktop() ? new NotificadorEscritorio() : new NotificadorMovil();
        }
        return notificador;
    }

    public static synchronized MonitorConectividad monitorConectividad() {
        if (monitorConectividad == null) {
            monitorConectividad = Platform.isDesktop()
                ? new MonitorConectividadEscritorio()
                : new MonitorConectividadAttach();
        }
        return monitorConectividad;
    }
}
