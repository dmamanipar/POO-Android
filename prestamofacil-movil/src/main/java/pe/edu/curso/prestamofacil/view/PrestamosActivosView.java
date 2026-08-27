package pe.edu.curso.prestamofacil.view;

import com.gluonhq.charm.glisten.application.MobileApplication;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.mvc.View;
import com.gluonhq.charm.glisten.visual.MaterialDesignIcon;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import pe.edu.curso.prestamofacil.model.EstadoPrestamo;
import pe.edu.curso.prestamofacil.model.Objeto;
import pe.edu.curso.prestamofacil.model.PersonaBase;
import pe.edu.curso.prestamofacil.model.Prestamo;
import pe.edu.curso.prestamofacil.service.CalculadorEstadoPrestamo;
import pe.edu.curso.prestamofacil.service.ObjetoService;
import pe.edu.curso.prestamofacil.service.PersonaService;
import pe.edu.curso.prestamofacil.service.PrestamoService;

import java.time.ZonedDateTime;

/**
 * RF06/RF07/RF08/RF09: préstamos activos con semáforo, devolución y búsqueda
 * por persona, objeto o estado.
 */
public class PrestamosActivosView extends View {

    private final PrestamoService prestamoService;
    private final PersonaService personaService;
    private final ObjetoService objetoService;
    private final CalculadorEstadoPrestamo calculador = new CalculadorEstadoPrestamo();

    private final ObservableList<Prestamo> prestamos = FXCollections.observableArrayList();
    private final FilteredList<Prestamo> filtrados = new FilteredList<>(prestamos, p -> true);
    private final ListView<Prestamo> lista = new ListView<>(filtrados);
    private final TextField campoBusqueda = new TextField();

    public PrestamosActivosView(PrestamoService prestamoService, PersonaService personaService,
                                 ObjetoService objetoService) {
        this.prestamoService = prestamoService;
        this.personaService = personaService;
        this.objetoService = objetoService;
        setCenter(construirContenido());
        cargar();
        setOnShowing(e -> cargar());
    }

    @Override
    protected void updateAppBar(AppBar appBar) {
        appBar.setNavIcon(MaterialDesignIcon.MENU.button(
            e -> MobileApplication.getInstance().getDrawer().open()));
        appBar.setTitleText("Préstamos activos");
        appBar.getActionItems().add(MaterialDesignIcon.REFRESH.button(e -> cargar()));
    }

    private VBox construirContenido() {
        campoBusqueda.setPromptText("Buscar por persona, objeto o estado...");
        campoBusqueda.textProperty().addListener((obs, antes, texto) -> aplicarFiltro(texto));

        lista.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Prestamo prestamo, boolean vacio) {
                super.updateItem(prestamo, vacio);
                if (vacio || prestamo == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                setGraphic(filaPrestamo(prestamo));
                setText(null);
            }
        });

        VBox raiz = new VBox(8, campoBusqueda, lista);
        raiz.setPadding(new Insets(8));
        VBox.setVgrow(lista, Priority.ALWAYS);
        return raiz;
    }

    private HBox filaPrestamo(Prestamo prestamo) {
        EstadoPrestamo estado = calculador.calcular(prestamo, ZonedDateTime.now());
        Label semaforo = new Label(semaforo(estado));

        VBox textos = new VBox(2,
            new Label(nombrePersona(prestamo) + " — " + nombresObjetos(prestamo)),
            new Label("Vence " + prestamo.getFechaHoraDevolucionPrevista().toLocalDate() + " "
                + prestamo.getFechaHoraDevolucionPrevista().toLocalTime()));
        ((Label) textos.getChildren().get(1)).setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        HBox.setHgrow(textos, Priority.ALWAYS);

        Button devolver = new Button("Devolver");
        devolver.setOnAction(e -> {
            prestamoService.registrarDevolucion(prestamo.getUuid());
            cargar();
        });

        HBox fila = new HBox(10, semaforo, textos, devolver);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }

    private String semaforo(EstadoPrestamo estado) {
        return switch (estado) {
            case VIGENTE -> "🟢";
            case PROXIMO_A_VENCER -> "🟡";
            case ATRASADO -> "🔴";
            case DEVUELTO -> "⚪";
        };
    }

    private String nombrePersona(Prestamo prestamo) {
        return personaService.buscarPorUuid(prestamo.getPersonaUuid())
            .map(PersonaBase::getNombre).orElse("(persona no encontrada)");
    }

    private String nombresObjetos(Prestamo prestamo) {
        return prestamo.getDetalles().stream()
            .map(d -> objetoService.buscarPorUuid(d.getObjetoUuid()).map(Objeto::getNombre).orElse("(objeto)"))
            .reduce((a, b) -> a + ", " + b).orElse("(sin objetos)");
    }

    private void aplicarFiltro(String texto) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        filtrados.setPredicate(p -> {
            if (q.isBlank()) {
                return true;
            }
            EstadoPrestamo estado = calculador.calcular(p, ZonedDateTime.now());
            return nombrePersona(p).toLowerCase().contains(q)
                || nombresObjetos(p).toLowerCase().contains(q)
                || estado.name().toLowerCase().contains(q);
        });
    }

    private void cargar() {
        prestamos.setAll(prestamoService.listarActivos());
        lista.refresh();
    }
}
