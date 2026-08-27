package pe.edu.curso.prestamofacil.platform;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Implementación de escritorio: no hay notificaciones del sistema operativo,
 * así que se simulan con un temporizador y un diálogo. Sirve para desarrollar
 * y probar la lógica de alertas sin un teléfono Android.
 */
public class NotificadorEscritorio implements Notificador {

    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notificador-escritorio");
            t.setDaemon(true);
            return t;
        });

    private final Map<String, ScheduledFuture<?>> programadas = new ConcurrentHashMap<>();

    @Override
    public void programar(String id, ZonedDateTime cuando, String titulo, String texto) {
        cancelar(id); // evita duplicar si ya existía una programación con el mismo id
        long segundos = Duration.between(ZonedDateTime.now(), cuando).getSeconds();
        if (segundos < 0) {
            segundos = 0; // ya venció: notificar de inmediato
        }
        ScheduledFuture<?> futuro = executor.schedule(
            () -> Platform.runLater(() -> mostrarDialogo(titulo, texto)),
            segundos, TimeUnit.SECONDS);
        programadas.put(id, futuro);
    }

    @Override
    public void cancelar(String id) {
        ScheduledFuture<?> futuro = programadas.remove(id);
        if (futuro != null) {
            futuro.cancel(false);
        }
    }

    private void mostrarDialogo(String titulo, String texto) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle("PréstamoFácil");
        alerta.setHeaderText(titulo);
        alerta.setContentText(texto);
        alerta.show();
    }
}
