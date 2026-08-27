package pe.edu.curso.prestamofacil.service;

import pe.edu.curso.prestamofacil.model.PersonaBase;
import pe.edu.curso.prestamofacil.model.TipoOperacion;
import pe.edu.curso.prestamofacil.model.UsuarioPrestatario;
import pe.edu.curso.prestamofacil.repository.ColaSincronizacion;
import pe.edu.curso.prestamofacil.repository.PersonaRepository;

import java.util.List;
import java.util.Optional;

public class PersonaService {

    private final PersonaRepository repositorio;
    private final ColaSincronizacion cola;

    public PersonaService(PersonaRepository repositorio, ColaSincronizacion cola) {
        this.repositorio = repositorio;
        this.cola = cola;
    }

    public PersonaBase registrar(String nombre, String telefono, String correo) {
        PersonaBase persona = new UsuarioPrestatario(nombre, telefono, correo);
        repositorio.guardar(persona);
        cola.encolar("PERSONA", persona.getUuid(), TipoOperacion.CREAR);
        return persona;
    }

    public void actualizar(PersonaBase persona) {
        repositorio.guardar(persona);
        cola.encolar("PERSONA", persona.getUuid(), TipoOperacion.ACTUALIZAR);
    }

    public void eliminar(String uuid) {
        repositorio.buscarPorUuid(uuid).ifPresent(p -> {
            p.marcarEliminado();
            repositorio.guardar(p);
            cola.encolar("PERSONA", uuid, TipoOperacion.ELIMINAR);
        });
    }

    public List<PersonaBase> listar() {
        return repositorio.listarActivas();
    }

    public Optional<PersonaBase> buscarPorUuid(String uuid) {
        return repositorio.buscarPorUuid(uuid);
    }
}
