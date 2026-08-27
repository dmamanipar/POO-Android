package pe.edu.curso.prestamofacil.model;

import java.time.ZonedDateTime;

public class Categoria extends EntidadSincronizable {

    private String nombre;
    private String descripcion;

    public Categoria(String nombre, String descripcion) {
        super();
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public Categoria(String uuid, ZonedDateTime fechaModificacion, boolean sincronizado,
                      boolean eliminado, String nombre, String descripcion) {
        super(uuid, fechaModificacion, sincronizado, eliminado);
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; marcarModificado(); }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; marcarModificado(); }
}
