package pe.edu.curso.prestamofacil.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.edu.curso.prestamofacil.model.Objeto;
import pe.edu.curso.prestamofacil.model.Prestamo;
import pe.edu.curso.prestamofacil.platform.NotificadorEscritorio;
import pe.edu.curso.prestamofacil.repository.*;

import java.io.File;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Usa un AlmacenLocal de prueba respaldado en un directorio temporal (no el
 * almacenamiento privado real de Gluon Attach, que no existe en un JUnit
 * corriendo en JVM plana) y NotificadorEscritorio, nunca un Notificador real.
 */
class PrestamoServiceTest {

    @TempDir
    Path tempDir;

    private PrestamoService prestamoService;
    private ObjetoRepository objetoRepository;
    private ObjetoService objetoService;
    private PersonaService personaService;

    @BeforeEach
    void configurar() {
        AlmacenLocal almacen = new AlmacenLocalDePrueba(tempDir.toFile());
        PersonaRepository personaRepository = new PersonaRepository(almacen);
        objetoRepository = new ObjetoRepository(almacen);
        PrestamoRepository prestamoRepository = new PrestamoRepository(almacen);
        ColaSincronizacion cola = new ColaSincronizacion(almacen);

        AlertaService alertaService = new AlertaService(new NotificadorEscritorio(), personaRepository);
        personaService = new PersonaService(personaRepository, cola);
        objetoService = new ObjetoService(objetoRepository, cola);
        prestamoService = new PrestamoService(prestamoRepository, objetoRepository, cola, alertaService);
    }

    @Test
    void registrarPrestamoMarcaElObjetoComoPrestado() {
        var persona = personaService.registrar("Ana", "999", "ana@correo.com");
        var objeto = objetoService.registrar(null, "Libro de Cálculo", "Edición 5");

        ZonedDateTime ahora = ZonedDateTime.now();
        Prestamo prestamo = prestamoService.registrarPrestamo(
            persona.getUuid(), List.of(objeto.getUuid()), ahora, ahora.plusDays(3), 60);

        assertNotNull(prestamo.getUuid());
        Objeto actualizado = objetoRepository.buscarPorUuid(objeto.getUuid()).orElseThrow();
        assertFalse(actualizado.estaDisponible());
    }

    @Test
    void noPermitePrestarUnObjetoYaPrestado() {
        var persona = personaService.registrar("Ana", "999", "ana@correo.com");
        var objeto = objetoService.registrar(null, "Cargador", "Tipo C");

        ZonedDateTime ahora = ZonedDateTime.now();
        prestamoService.registrarPrestamo(persona.getUuid(), List.of(objeto.getUuid()),
            ahora, ahora.plusDays(1), 0);

        assertThrows(IllegalStateException.class, () ->
            prestamoService.registrarPrestamo(persona.getUuid(), List.of(objeto.getUuid()),
                ahora, ahora.plusDays(2), 0));
    }

    @Test
    void registrarDevolucionLiberaElObjeto() {
        var persona = personaService.registrar("Ana", "999", "ana@correo.com");
        var objeto = objetoService.registrar(null, "Calculadora", "Científica");
        ZonedDateTime ahora = ZonedDateTime.now();
        Prestamo prestamo = prestamoService.registrarPrestamo(
            persona.getUuid(), List.of(objeto.getUuid()), ahora, ahora.plusHours(2), 0);

        prestamoService.registrarDevolucion(prestamo.getUuid());

        Objeto actualizado = objetoRepository.buscarPorUuid(objeto.getUuid()).orElseThrow();
        assertTrue(actualizado.estaDisponible());
        assertTrue(prestamoService.listarActivos().isEmpty());
        assertEquals(1, prestamoService.listarHistorial().size());
    }

    @Test
    void noPermiteEliminarUnObjetoPrestado() {
        var persona = personaService.registrar("Ana", "999", "ana@correo.com");
        var objeto = objetoService.registrar(null, "Taladro", "Inalámbrico");
        ZonedDateTime ahora = ZonedDateTime.now();
        prestamoService.registrarPrestamo(persona.getUuid(), List.of(objeto.getUuid()),
            ahora, ahora.plusDays(1), 0);

        assertThrows(IllegalStateException.class, () -> objetoService.eliminar(objeto.getUuid()));
        assertTrue(objetoRepository.buscarPorUuid(objeto.getUuid()).isPresent());
        assertFalse(objetoRepository.buscarPorUuid(objeto.getUuid()).orElseThrow().isEliminado());
    }

    @Test
    void permiteEliminarUnObjetoDisponible() {
        var objeto = objetoService.registrar(null, "Mochila", "Deportiva");

        objetoService.eliminar(objeto.getUuid());

        assertTrue(objetoRepository.buscarPorUuid(objeto.getUuid()).orElseThrow().isEliminado());
    }

    @Test
    void rechazaFechaDevolucionAnteriorAlPrestamo() {
        ZonedDateTime ahora = ZonedDateTime.now();
        assertThrows(IllegalArgumentException.class, () ->
            new Prestamo("persona-1", ahora, ahora.minusHours(1), 0));
    }

    /** AlmacenLocal de prueba: mismo formato JSON, pero sin depender de Gluon StorageService. */
    static class AlmacenLocalDePrueba implements AlmacenLocal {
        private final AlmacenLocalJson delegadoConDirectorioFijo;

        AlmacenLocalDePrueba(File directorio) {
            this.delegadoConDirectorioFijo = crearConDirectorio(directorio);
        }

        private static AlmacenLocalJson crearConDirectorio(File directorio) {
            System.setProperty("user.home", directorio.getAbsolutePath());
            return new AlmacenLocalJson();
        }

        @Override
        public <T> List<T> leerTodo(String nombreArchivo, Class<T> tipo) {
            return delegadoConDirectorioFijo.leerTodo(nombreArchivo, tipo);
        }

        @Override
        public <T> void escribirTodo(String nombreArchivo, List<T> elementos) {
            delegadoConDirectorioFijo.escribirTodo(nombreArchivo, elementos);
        }
    }
}
