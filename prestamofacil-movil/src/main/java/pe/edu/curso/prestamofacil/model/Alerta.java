package pe.edu.curso.prestamofacil.model;

import java.time.ZonedDateTime;

/**
 * Composición: una Alerta pertenece a un Préstamo y no tiene sentido fuera de él.
 * Viaja embebida dentro de Prestamo, no es una EntidadSincronizable independiente.
 */
public class Alerta {

    private String idNotificacionPrincipal;
    private ZonedDateTime cuandoPrincipal;
    private String idNotificacionRecordatorio;
    private ZonedDateTime cuandoRecordatorio;
    private boolean activa;

    public Alerta(String idNotificacionPrincipal, ZonedDateTime cuandoPrincipal,
                   String idNotificacionRecordatorio, ZonedDateTime cuandoRecordatorio) {
        this.idNotificacionPrincipal = idNotificacionPrincipal;
        this.cuandoPrincipal = cuandoPrincipal;
        this.idNotificacionRecordatorio = idNotificacionRecordatorio;
        this.cuandoRecordatorio = cuandoRecordatorio;
        this.activa = true;
    }

    public String getIdNotificacionPrincipal() { return idNotificacionPrincipal; }
    public ZonedDateTime getCuandoPrincipal() { return cuandoPrincipal; }
    public String getIdNotificacionRecordatorio() { return idNotificacionRecordatorio; }
    public ZonedDateTime getCuandoRecordatorio() { return cuandoRecordatorio; }
    public boolean isActiva() { return activa; }
    public void desactivar() { this.activa = false; }
}
