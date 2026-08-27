package pe.edu.curso.prestamofacil.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.edu.curso.prestamofacil.cloud.RepositorioRemotoSimulado;
import pe.edu.curso.prestamofacil.model.PersonaBase;
import pe.edu.curso.prestamofacil.model.Prestamo;
import pe.edu.curso.prestamofacil.platform.MonitorConectividad;
import pe.edu.curso.prestamofacil.platform.NotificadorEscritorio;
import pe.edu.curso.prestamofacil.repository.*;
import pe.edu.curso.prestamofacil.util.Configuracion;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SincronizacionServiceTest {

    @TempDir
    Path tempDir;

    private RepositorioRemotoSimulado remoto;
    private ColaSincronizacion cola;
    private SincronizacionService sincronizacionService;
    private PersonaService personaService;
    private PrestamoService prestamoService;
    private PersonaRepository personaRepository;
    private PrestamoRepository prestamoRepository;

    @BeforeEach
    void configurar() {
        System.setProperty("user.home", tempDir.toFile().getAbsolutePath());
        AlmacenLocal almacen = new AlmacenLocalJson();

        personaRepository = new PersonaRepository(almacen);
        ObjetoRepository objetoRepository = new ObjetoRepository(almacen);
        prestamoRepository = new PrestamoRepository(almacen);
        CategoriaRepository categoriaRepository = new CategoriaRepository(almacen);
        cola = new ColaSincronizacion(almacen);

        AlertaService alertaService = new AlertaService(new NotificadorEscritorio(), personaRepository);
        personaService = new PersonaService(personaRepository, cola);
        ObjetoService objetoService = new ObjetoService(objetoRepository, cola);
        prestamoService = new PrestamoService(prestamoRepository, objetoRepository, cola, alertaService);

        remoto = new RepositorioRemotoSimulado();
        MonitorConectividad siempreConectado = new MonitorConectividad() {
            public boolean hayConexion() { return true; }
            public void alRecuperarConexion(Runnable callback) { }
        };

        sincronizacionService = new SincronizacionService(remoto, siempreConectado, cola,
            personaRepository, objetoRepository, prestamoRepository, categoriaRepository, alertaService);

        Configuracion config = Configuracion.obtener();
        config.setUrlWebApp("https://script.google.com/macros/s/fake/exec");
        config.setToken("token-de-prueba");
        config.setUltimaSincronizacionExitosa(null);
        config.guardar();
    }

    @Test
    void pushVaciaLaColaCuandoElServidorAceptaTodo() {
        personaService.registrar("Ana", "999", "ana@correo.com");
        assertFalse(cola.estaVacia());

        var resultado = sincronizacionService.sincronizar();

        assertTrue(resultado.seEjecuto);
        assertTrue(cola.estaVacia(), "La cola debe vaciarse tras un push exitoso");
    }

    @Test
    void colaNoSeVaciaSiElEnvioFalla() {
        personaService.registrar("Luis", "988", "luis@correo.com");
        remoto.simularFalloDeRed();

        sincronizacionService.sincronizar();

        assertFalse(cola.estaVacia(), "Un fallo de red no debe perder la operación de la cola");
    }

    @Test
    void noSincronizaSinConexion() {
        MonitorConectividad sinConexion = new MonitorConectividad() {
            public boolean hayConexion() { return false; }
            public void alRecuperarConexion(Runnable callback) { }
        };
        SincronizacionService servicioOffline = new SincronizacionService(remoto, sinConexion, cola,
            null, null, null, null, null);

        var resultado = servicioOffline.sincronizar();
        assertFalse(resultado.seEjecuto);
    }

    @Test
    void pullAplicaUnaEdicionRemotaMasRecienteQueLaLocal() {
        var persona = personaService.registrar("Luis", "111", "luis@correo.com");
        sincronizacionService.sincronizar(); // push inicial: la fila queda en "remoto"

        // Simula que alguien editó la fila directamente en la hoja: mismo
        // uuid, fecha_modificacion posterior a la del push anterior.
        remoto.enviarCambios("PERSONA", List.of(filaPersona(persona.getUuid(), "Luis Editado",
            "222", "luis2@correo.com", ZonedDateTime.now().plusMinutes(5))));

        sincronizacionService.sincronizar(); // pull

        PersonaBase actualizada = personaService.buscarPorUuid(persona.getUuid()).orElseThrow();
        assertEquals("Luis Editado", actualizada.getNombre());
        assertEquals("222", actualizada.getTelefono());
    }

    @Test
    void pullIgnoraUnaFilaRemotaMasAntiguaQueLaLocal() {
        var persona = personaService.registrar("Marta", "333", "marta@correo.com");
        ZonedDateTime fechaLocal = personaService.buscarPorUuid(persona.getUuid()).orElseThrow().getFechaModificacion();
        sincronizacionService.sincronizar();

        // Fila remota con fecha_modificacion ANTERIOR a la copia local actual.
        remoto.enviarCambios("PERSONA", List.of(filaPersona(persona.getUuid(), "Marta Vieja",
            "000", "vieja@correo.com", fechaLocal.minusDays(1))));

        sincronizacionService.sincronizar();

        PersonaBase actual = personaService.buscarPorUuid(persona.getUuid()).orElseThrow();
        assertEquals("Marta", actual.getNombre(), "Una fila remota más antigua no debe pisar la copia local");
    }

    @Test
    void pullActualizaFechaDeDevolucionDeUnPrestamoSinLanzarExcepcion() {
        var persona = personaService.registrar("Sofía", "444", "sofia@correo.com");
        ZonedDateTime ahora = ZonedDateTime.now();
        Prestamo prestamo = prestamoService.registrarPrestamo(persona.getUuid(), List.of("objeto-fantasma"),
            ahora, ahora.plusDays(1), 0);
        sincronizacionService.sincronizar();

        ZonedDateTime nuevaFecha = ahora.plusDays(3);
        Map<String, Object> filaPrestamo = new HashMap<>();
        filaPrestamo.put("uuid", prestamo.getUuid());
        filaPrestamo.put("persona_uuid", persona.getUuid());
        filaPrestamo.put("fecha_prestamo", ahora.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        filaPrestamo.put("fecha_hora_devolucion_prevista", nuevaFecha.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        filaPrestamo.put("fecha_devolucion_real", null);
        filaPrestamo.put("minutos_recordatorio_previo", 60);
        filaPrestamo.put("eliminado", false);
        filaPrestamo.put("fecha_modificacion",
            ZonedDateTime.now().plusMinutes(5).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        remoto.enviarCambios("PRESTAMO", List.of(filaPrestamo));

        assertDoesNotThrow(() -> sincronizacionService.sincronizar());

        Prestamo actualizado = prestamoRepository.buscarPorUuid(prestamo.getUuid()).orElseThrow();
        assertEquals(nuevaFecha.toLocalDate(), actualizado.getFechaHoraDevolucionPrevista().toLocalDate());
    }

    private static Map<String, Object> filaPersona(String uuid, String nombre, String telefono,
                                                     String correo, ZonedDateTime fechaModificacion) {
        Map<String, Object> fila = new HashMap<>();
        fila.put("uuid", uuid);
        fila.put("nombre", nombre);
        fila.put("telefono", telefono);
        fila.put("correo", correo);
        fila.put("tipo", "PRESTATARIO");
        fila.put("eliminado", false);
        fila.put("fecha_modificacion", fechaModificacion.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return fila;
    }
}
