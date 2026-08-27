package pe.edu.curso.prestamofacil.platform;

import com.gluonhq.attach.connectivity.ConnectivityService;

public class MonitorConectividadAttach implements MonitorConectividad {

    @Override
    public boolean hayConexion() {
        return ConnectivityService.create()
            .map(ConnectivityService::isConnected)
            .orElse(false);
    }

    @Override
    public void alRecuperarConexion(Runnable callback) {
        ConnectivityService.create().ifPresent(servicio ->
            servicio.connectedProperty().addListener((obs, antes, ahora) -> {
                if (Boolean.TRUE.equals(ahora)) {
                    callback.run();
                }
            }));
    }
}
