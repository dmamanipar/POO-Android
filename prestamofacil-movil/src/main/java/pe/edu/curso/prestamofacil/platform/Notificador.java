package pe.edu.curso.prestamofacil.platform;

import java.time.ZonedDateTime;

/**
 * Abstracción de notificaciones locales. NotificadorMovil (Gluon Attach) y
 * NotificadorEscritorio son intercambiables sin que AlertaService lo sepa (polimorfismo).
 */
public interface Notificador {

    /** id debe ser estable (derivado del uuid del préstamo) para poder cancelarla luego. */
    void programar(String id, ZonedDateTime cuando, String titulo, String texto);

    void cancelar(String id);
}
