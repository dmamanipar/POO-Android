package pe.edu.curso.prestamofacil;

import com.gluonhq.charm.glisten.application.MobileApplication;
import com.gluonhq.charm.glisten.control.NavigationDrawer;
import com.gluonhq.charm.glisten.visual.MaterialDesignIcon;
import com.gluonhq.charm.glisten.visual.Swatch;
import javafx.scene.Scene;
import pe.edu.curso.prestamofacil.platform.FabricaPlataforma;
import pe.edu.curso.prestamofacil.repository.AlmacenLocalJson;
import pe.edu.curso.prestamofacil.repository.CategoriaRepository;
import pe.edu.curso.prestamofacil.repository.ColaSincronizacion;
import pe.edu.curso.prestamofacil.repository.ObjetoRepository;
import pe.edu.curso.prestamofacil.repository.PersonaRepository;
import pe.edu.curso.prestamofacil.repository.PrestamoRepository;
import pe.edu.curso.prestamofacil.service.AlertaService;
import pe.edu.curso.prestamofacil.service.CategoriaService;
import pe.edu.curso.prestamofacil.service.ObjetoService;
import pe.edu.curso.prestamofacil.service.PersonaService;
import pe.edu.curso.prestamofacil.service.PrestamoService;
import pe.edu.curso.prestamofacil.service.SincronizacionService;
import pe.edu.curso.prestamofacil.cloud.ClienteHttpSheets;
import pe.edu.curso.prestamofacil.view.ConfiguracionView;
import pe.edu.curso.prestamofacil.view.DashboardView;
import pe.edu.curso.prestamofacil.view.HistorialView;
import pe.edu.curso.prestamofacil.view.NuevoPrestamoView;
import pe.edu.curso.prestamofacil.view.ObjetosView;
import pe.edu.curso.prestamofacil.view.PersonasView;
import pe.edu.curso.prestamofacil.view.PrestamosActivosView;

public class Main extends MobileApplication {

    public static final String DASHBOARD_VIEW = "Dashboard";
    public static final String PERSONAS_VIEW = "Personas";
    public static final String OBJETOS_VIEW = "Objetos";
    public static final String NUEVO_PRESTAMO_VIEW = "NuevoPrestamo";
    public static final String PRESTAMOS_ACTIVOS_VIEW = "PrestamosActivos";
    public static final String HISTORIAL_VIEW = "Historial";
    public static final String CONFIGURACION_VIEW = "Configuracion";

    // Composición manual de dependencias (sin framework de inyección, deliberado
    // para un proyecto académico: se ve explícitamente qué depende de qué).
    private static PersonaRepository personaRepository;
    private static ObjetoRepository objetoRepository;
    private static PrestamoRepository prestamoRepository;
    private static CategoriaRepository categoriaRepository;
    private static ColaSincronizacion cola;
    private static AlertaService alertaService;
    private static PersonaService personaService;
    private static ObjetoService objetoService;
    private static PrestamoService prestamoService;
    private static CategoriaService categoriaService;
    private static SincronizacionService sincronizacionService;

    @Override
    public void init() {
        AlmacenLocalJson almacen = new AlmacenLocalJson();
        personaRepository = new PersonaRepository(almacen);
        objetoRepository = new ObjetoRepository(almacen);
        prestamoRepository = new PrestamoRepository(almacen);
        categoriaRepository = new CategoriaRepository(almacen);
        cola = new ColaSincronizacion(almacen);

        alertaService = new AlertaService(FabricaPlataforma.notificador(), personaRepository);
        personaService = new PersonaService(personaRepository, cola);
        objetoService = new ObjetoService(objetoRepository, cola);
        categoriaService = new CategoriaService(categoriaRepository, cola);
        prestamoService = new PrestamoService(prestamoRepository, objetoRepository, cola, alertaService);
        sincronizacionService = new SincronizacionService(
            new ClienteHttpSheets(), FabricaPlataforma.monitorConectividad(), cola,
            personaRepository, objetoRepository, prestamoRepository, categoriaRepository, alertaService);

        // RF13/RF14 + reconciliación: reprograma alertas de préstamos activos
        // al abrir la app (cubre reinicios/reinstalaciones del teléfono).
        alertaService.reconciliarAlAbrir(prestamoRepository);

        addViewFactory(DASHBOARD_VIEW, () ->
            new DashboardView(prestamoService, objetoService, sincronizacionService));
        addViewFactory(PERSONAS_VIEW, () -> new PersonasView(personaService));
        addViewFactory(OBJETOS_VIEW, () -> new ObjetosView(objetoService, categoriaService));
        addViewFactory(NUEVO_PRESTAMO_VIEW, () ->
            new NuevoPrestamoView(personaService, objetoService, prestamoService));
        addViewFactory(PRESTAMOS_ACTIVOS_VIEW, () ->
            new PrestamosActivosView(prestamoService, personaService, objetoService));
        addViewFactory(HISTORIAL_VIEW, () ->
            new HistorialView(prestamoService, personaService, objetoService));
        addViewFactory(CONFIGURACION_VIEW, () -> new ConfiguracionView(sincronizacionService));

        construirMenu();
    }

    /** RF06/RF01/RF03/RF05/RF10 + configuración: un único punto de navegación entre pantallas. */
    private void construirMenu() {
        NavigationDrawer drawer = getDrawer();
        drawer.setHeader(new NavigationDrawer.Header("PréstamoFácil",
            "Objetos prestados, sin perder de vista nada"));
        drawer.getItems().addAll(
            new NavigationDrawer.ViewItem("Dashboard", MaterialDesignIcon.DASHBOARD.graphic(), DASHBOARD_VIEW),
            new NavigationDrawer.ViewItem("Personas", MaterialDesignIcon.PEOPLE.graphic(), PERSONAS_VIEW),
            new NavigationDrawer.ViewItem("Objetos", MaterialDesignIcon.WIDGETS.graphic(), OBJETOS_VIEW),
            new NavigationDrawer.ViewItem("Nuevo préstamo", MaterialDesignIcon.ADD_BOX.graphic(), NUEVO_PRESTAMO_VIEW),
            new NavigationDrawer.ViewItem("Préstamos activos", MaterialDesignIcon.LIST.graphic(), PRESTAMOS_ACTIVOS_VIEW),
            new NavigationDrawer.ViewItem("Historial", MaterialDesignIcon.HISTORY.graphic(), HISTORIAL_VIEW),
            new NavigationDrawer.ViewItem("Configuración", MaterialDesignIcon.SETTINGS.graphic(), CONFIGURACION_VIEW));
    }

    @Override
    public void postInit(Scene scene) {
        Swatch.BLUE.assignTo(scene);
        switchView(DASHBOARD_VIEW);
    }

    public static PersonaService personaService() { return personaService; }
    public static ObjetoService objetoService() { return objetoService; }
    public static PrestamoService prestamoService() { return prestamoService; }
    public static CategoriaService categoriaService() { return categoriaService; }
    public static SincronizacionService sincronizacionService() { return sincronizacionService; }

    public static void main(String[] args) {
        launch(args);
    }
}
