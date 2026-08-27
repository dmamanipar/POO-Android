package pe.edu.curso.prestamofacil.model;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Base de todo objeto de dominio que se sincroniza con Google Sheets.
 * uuid se genera UNA sola vez al crear el objeto y nunca se regenera al editar.
 */
public abstract class EntidadSincronizable {

    private String uuid;
    private ZonedDateTime fechaModificacion;
    private boolean sincronizado;
    private boolean eliminado;

    protected EntidadSincronizable() {
        this.uuid = UUID.randomUUID().toString();
        this.fechaModificacion = ZonedDateTime.now();
        this.sincronizado = false;
        this.eliminado = false;
    }

    protected EntidadSincronizable(String uuid, ZonedDateTime fechaModificacion,
                                    boolean sincronizado, boolean eliminado) {
        this.uuid = uuid;
        this.fechaModificacion = fechaModificacion;
        this.sincronizado = sincronizado;
        this.eliminado = eliminado;
    }

    public String getUuid() { return uuid; }
    public ZonedDateTime getFechaModificacion() { return fechaModificacion; }
    public boolean isSincronizado() { return sincronizado; }
    public boolean isEliminado() { return eliminado; }

    public void marcarModificado() {
        this.fechaModificacion = ZonedDateTime.now();
        this.sincronizado = false;
    }

    public void marcarSincronizado() { this.sincronizado = true; }

    public void marcarEliminado() {
        this.eliminado = true;
        marcarModificado();
    }

    public boolean esMasRecienteQue(EntidadSincronizable otra) {
        if (otra == null) return true;
        return this.fechaModificacion.isAfter(otra.getFechaModificacion());
    }
}
