package pe.edu.curso.prestamofacil.model;

import java.time.ZonedDateTime;

public abstract class PersonaBase extends EntidadSincronizable {

    private String nombre;
    private String telefono;
    private String correo;

    protected PersonaBase(String nombre, String telefono, String correo) {
        super();
        setNombre(nombre);
        this.telefono = telefono;
        this.correo = correo;
    }

    protected PersonaBase(String uuid, ZonedDateTime fechaModificacion, boolean sincronizado,
                           boolean eliminado, String nombre, String telefono, String correo) {
        super(uuid, fechaModificacion, sincronizado, eliminado);
        this.nombre = nombre;
        this.telefono = telefono;
        this.correo = correo;
    }

    public String getNombre() { return nombre; }

    public void setNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la persona no puede estar vacío.");
        }
        this.nombre = nombre.trim();
        marcarModificado();
    }

    public String getTelefono() { return telefono; }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
        marcarModificado();
    }

    public String getCorreo() { return correo; }

    public void setCorreo(String correo) {
        this.correo = correo;
        marcarModificado();
    }

    public abstract String descripcionRol();
}
