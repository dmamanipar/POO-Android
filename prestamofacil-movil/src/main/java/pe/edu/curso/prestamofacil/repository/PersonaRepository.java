package pe.edu.curso.prestamofacil.repository;

import pe.edu.curso.prestamofacil.model.PersonaBase;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PersonaRepository {

    private static final String ARCHIVO = "personas.json";

    private final AlmacenLocal almacen;

    public PersonaRepository(AlmacenLocal almacen) {
        this.almacen = almacen;
    }

    public List<PersonaBase> listarActivas() {
        return leerTodas().stream().filter(p -> !p.isEliminado()).collect(Collectors.toList());
    }

    public Optional<PersonaBase> buscarPorUuid(String uuid) {
        return leerTodas().stream().filter(p -> p.getUuid().equals(uuid)).findFirst();
    }

    public void guardar(PersonaBase persona) {
        List<PersonaBase> todas = leerTodas();
        todas.removeIf(p -> p.getUuid().equals(persona.getUuid()));
        todas.add(persona);
        almacen.escribirTodo(ARCHIVO, todas);
    }

    public List<PersonaBase> leerTodas() {
        return almacen.leerTodo(ARCHIVO, PersonaBase.class);
    }
}
