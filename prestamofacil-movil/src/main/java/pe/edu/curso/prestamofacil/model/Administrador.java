package pe.edu.curso.prestamofacil.model;

import java.time.ZonedDateTime;

public class Administrador extends PersonaBase {

    public Administrador(String nombre, String telefono, String correo) {
        super(nombre, telefono, correo);
    }

    public Administrador(String uuid, ZonedDateTime fechaModificacion, boolean sincronizado,
                          boolean eliminado, String nombre, String telefono, String correo) {
        super(uuid, fechaModificacion, sincronizado, eliminado, nombre, telefono, correo);
    }

    @Override
    public String descripcionRol() { return "Administrador"; }
}
