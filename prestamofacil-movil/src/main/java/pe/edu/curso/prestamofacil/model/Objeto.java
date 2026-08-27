package pe.edu.curso.prestamofacil.model;

import java.time.ZonedDateTime;

/** Agregación: un Objeto pertenece a una Categoria por referencia (uuid), no por composición. */
public class Objeto extends EntidadSincronizable {

    public enum EstadoObjeto { DISPONIBLE, PRESTADO }

    private String categoriaUuid;
    private String nombre;
    private String descripcion;
    private EstadoObjeto estado;

    public Objeto(String categoriaUuid, String nombre, String descripcion) {
        super();
        this.categoriaUuid = categoriaUuid;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.estado = EstadoObjeto.DISPONIBLE;
    }

    public Objeto(String uuid, ZonedDateTime fechaModificacion, boolean sincronizado, boolean eliminado,
                  String categoriaUuid, String nombre, String descripcion, EstadoObjeto estado) {
        super(uuid, fechaModificacion, sincronizado, eliminado);
        this.categoriaUuid = categoriaUuid;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.estado = estado;
    }

    public String getCategoriaUuid() { return categoriaUuid; }
    public void setCategoriaUuid(String categoriaUuid) { this.categoriaUuid = categoriaUuid; marcarModificado(); }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; marcarModificado(); }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; marcarModificado(); }
    public EstadoObjeto getEstado() { return estado; }

    public void marcarPrestado() { this.estado = EstadoObjeto.PRESTADO; marcarModificado(); }
    public void marcarDisponible() { this.estado = EstadoObjeto.DISPONIBLE; marcarModificado(); }
    public boolean estaDisponible() { return estado == EstadoObjeto.DISPONIBLE; }
}
