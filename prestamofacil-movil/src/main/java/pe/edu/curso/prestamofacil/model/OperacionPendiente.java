package pe.edu.curso.prestamofacil.model;

import java.time.ZonedDateTime;

/** Elemento de la cola de sincronización: agregación dentro de ColaSincronizacion. */
public class OperacionPendiente {

    private final String entidad;      // "PERSONA", "OBJETO", "PRESTAMO", "CATEGORIA"
    private final String uuidEntidad;
    private final TipoOperacion tipo;
    private final ZonedDateTime fecha;

    public OperacionPendiente(String entidad, String uuidEntidad, TipoOperacion tipo, ZonedDateTime fecha) {
        this.entidad = entidad;
        this.uuidEntidad = uuidEntidad;
        this.tipo = tipo;
        this.fecha = fecha;
    }

    public String getEntidad() { return entidad; }
    public String getUuidEntidad() { return uuidEntidad; }
    public TipoOperacion getTipo() { return tipo; }
    public ZonedDateTime getFecha() { return fecha; }
}
