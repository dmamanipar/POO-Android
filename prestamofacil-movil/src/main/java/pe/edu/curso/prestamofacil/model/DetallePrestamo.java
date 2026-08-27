package pe.edu.curso.prestamofacil.model;

/** Composición: parte de Prestamo; describe un objeto incluido en el préstamo. */
public class DetallePrestamo {

    private String objetoUuid;
    private String observacion;

    public DetallePrestamo(String objetoUuid, String observacion) {
        this.objetoUuid = objetoUuid;
        this.observacion = observacion;
    }

    public String getObjetoUuid() { return objetoUuid; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
