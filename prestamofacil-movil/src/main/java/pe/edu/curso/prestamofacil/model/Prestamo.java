package pe.edu.curso.prestamofacil.model;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Prestamo extends EntidadSincronizable {

    private String personaUuid;
    private ZonedDateTime fechaPrestamo;
    private ZonedDateTime fechaHoraDevolucionPrevista;
    private ZonedDateTime fechaDevolucionReal; // null mientras no se devuelva
    private int minutosRecordatorioPrevio;      // 0 = sin recordatorio previo
    private final List<DetallePrestamo> detalles = new ArrayList<>();
    private Alerta alerta; // puede ser null si aún no se programó

    public Prestamo(String personaUuid, ZonedDateTime fechaPrestamo,
                     ZonedDateTime fechaHoraDevolucionPrevista, int minutosRecordatorioPrevio) {
        super();
        if (fechaHoraDevolucionPrevista.isBefore(fechaPrestamo)) {
            throw new IllegalArgumentException(
                "La fecha/hora de devolución no puede ser anterior a la del préstamo.");
        }
        this.personaUuid = personaUuid;
        this.fechaPrestamo = fechaPrestamo;
        this.fechaHoraDevolucionPrevista = fechaHoraDevolucionPrevista;
        this.minutosRecordatorioPrevio = minutosRecordatorioPrevio;
    }

    public Prestamo(String uuid, ZonedDateTime fechaModificacion, boolean sincronizado, boolean eliminado,
                     String personaUuid, ZonedDateTime fechaPrestamo, ZonedDateTime fechaHoraDevolucionPrevista,
                     ZonedDateTime fechaDevolucionReal, int minutosRecordatorioPrevio) {
        super(uuid, fechaModificacion, sincronizado, eliminado);
        this.personaUuid = personaUuid;
        this.fechaPrestamo = fechaPrestamo;
        this.fechaHoraDevolucionPrevista = fechaHoraDevolucionPrevista;
        this.fechaDevolucionReal = fechaDevolucionReal;
        this.minutosRecordatorioPrevio = minutosRecordatorioPrevio;
    }

    public String getPersonaUuid() { return personaUuid; }
    public ZonedDateTime getFechaPrestamo() { return fechaPrestamo; }
    public ZonedDateTime getFechaHoraDevolucionPrevista() { return fechaHoraDevolucionPrevista; }

    public void reprogramarDevolucion(ZonedDateTime nuevaFechaHora) {
        this.fechaHoraDevolucionPrevista = nuevaFechaHora;
        marcarModificado();
    }

    public ZonedDateTime getFechaDevolucionReal() { return fechaDevolucionReal; }
    public boolean estaDevuelto() { return fechaDevolucionReal != null; }

    public void registrarDevolucion(ZonedDateTime cuando) {
        this.fechaDevolucionReal = cuando;
        if (alerta != null) {
            alerta.desactivar();
        }
        marcarModificado();
    }

    public int getMinutosRecordatorioPrevio() { return minutosRecordatorioPrevio; }
    public List<DetallePrestamo> getDetalles() { return detalles; }

    public void agregarDetalle(DetallePrestamo detalle) {
        detalles.add(detalle);
        marcarModificado();
    }

    public Alerta getAlerta() { return alerta; }
    public void setAlerta(Alerta alerta) { this.alerta = alerta; }
}
