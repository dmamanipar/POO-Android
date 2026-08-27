package pe.edu.curso.prestamofacil.platform;

import com.gluonhq.attach.localnotifications.LocalNotificationsService;
import com.gluonhq.attach.localnotifications.Notification;

import java.time.ZonedDateTime;

/**
 * Implementación con Gluon Attach LocalNotificationsService. `id` se pasa tal
 * cual (el constructor de Notification lo acepta como String) para poder
 * cancelar la misma notificación más adelante buscando por ese id.
 *
 * API verificada contra com.gluonhq.attach:local-notifications:4.0.22: no
 * existe Notification.builder() ni NotificationsService — la interfaz es
 * LocalNotificationsService, expone getNotifications() (ObservableList) y
 * Notification se construye con su constructor.
 */
public class NotificadorMovil implements Notificador {

    @Override
    public void programar(String id, ZonedDateTime cuando, String titulo, String texto) {
        Notification notificacion = new Notification(titulo, texto, id, cuando, null);

        LocalNotificationsService.create()
            .ifPresentOrElse(
                servicio -> servicio.getNotifications().add(notificacion),
                () -> System.err.println("LocalNotificationsService no disponible en esta plataforma."));
    }

    @Override
    public void cancelar(String id) {
        LocalNotificationsService.create().ifPresent(servicio ->
            servicio.getNotifications().removeIf(n -> id.equals(n.getId())));
    }
}
