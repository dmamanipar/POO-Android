package pe.edu.curso.prestamofacil.service;

import pe.edu.curso.prestamofacil.model.Alerta;
import pe.edu.curso.prestamofacil.model.Prestamo;
import pe.edu.curso.prestamofacil.platform.Notificador;
import pe.edu.curso.prestamofacil.repository.PersonaRepository;
import pe.edu.curso.prestamofacil.repository.PrestamoRepository;

import java.time.ZonedDateTime;

/**
 * Programa, cancela y reconcilia las notificaciones de devolución.
 * Nunca toca Gluon Attach directamente: todo pasa por la interfaz Notificador
 * (ver .claude/skills/gluon-attach-notifications/SKILL.md).
 */
public class AlertaService {

    private final Notificador notificador;
    private final PersonaRepository personaRepository;

    public AlertaService(Notificador notificador, PersonaRepository personaRepository) {
        this.notificador = notificador;
        this.personaRepository = personaRepository;
    }

    /** Se llama al registrar o editar un préstamo. */
    public void programar(Prestamo prestamo) {
        cancelar(prestamo); // limpia cualquier alerta previa antes de reprogramar

        String idPrincipal = idPrincipal(prestamo);
        String textoPrincipal = construirTexto(prestamo);
        notificador.programar(idPrincipal, prestamo.getFechaHoraDevolucionPrevista(),
            "Devolución pendiente", textoPrincipal);

        String idRecordatorio = null;
        ZonedDateTime cuandoRecordatorio = null;
        if (prestamo.getMinutosRecordatorioPrevio() > 0) {
            cuandoRecordatorio = prestamo.getFechaHoraDevolucionPrevista()
                .minusMinutes(prestamo.getMinutosRecordatorioPrevio());
            if (cuandoRecordatorio.isAfter(ZonedDateTime.now())) {
                idRecordatorio = idRecordatorio(prestamo);
                notificador.programar(idRecordatorio, cuandoRecordatorio,
                    "Recordatorio de devolución", textoPrincipal);
            }
        }

        prestamo.setAlerta(new Alerta(idPrincipal, prestamo.getFechaHoraDevolucionPrevista(),
            idRecordatorio, cuandoRecordatorio));
    }

    /** Se llama al registrar la devolución. */
    public void cancelar(Prestamo prestamo) {
        notificador.cancelar(idPrincipal(prestamo));
        notificador.cancelar(idRecordatorio(prestamo));
    }

    /**
     * Se llama una vez al iniciar la app: reprograma alertas de todos los
     * préstamos activos con fecha futura, porque un reinicio del teléfono o
     * una reinstalación no preservan alarmas programadas anteriormente.
     */
    public void reconciliarAlAbrir(PrestamoRepository prestamoRepository) {
        ZonedDateTime ahora = ZonedDateTime.now();
        for (Prestamo p : prestamoRepository.listarActivos()) {
            if (p.getFechaHoraDevolucionPrevista().isAfter(ahora)) {
                programar(p);
                prestamoRepository.guardar(p);
            }
        }
    }

    private String construirTexto(Prestamo prestamo) {
        String nombrePersona = personaRepository.buscarPorUuid(prestamo.getPersonaUuid())
            .map(p -> p.getNombre())
            .orElse("(persona no encontrada)");
        return nombrePersona + " debe devolver el préstamo del "
            + prestamo.getFechaHoraDevolucionPrevista().toLocalDate();
    }

    private String idPrincipal(Prestamo prestamo) {
        return prestamo.getUuid() + "-principal";
    }

    private String idRecordatorio(Prestamo prestamo) {
        return prestamo.getUuid() + "-recordatorio";
    }
}
