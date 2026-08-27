package pe.edu.curso.prestamofacil.view;

import com.gluonhq.charm.glisten.application.MobileApplication;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.mvc.View;
import com.gluonhq.charm.glisten.visual.MaterialDesignIcon;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import pe.edu.curso.prestamofacil.model.Objeto;
import pe.edu.curso.prestamofacil.model.PersonaBase;
import pe.edu.curso.prestamofacil.model.Prestamo;
import pe.edu.curso.prestamofacil.service.ObjetoService;
import pe.edu.curso.prestamofacil.service.PersonaService;
import pe.edu.curso.prestamofacil.service.PrestamoService;

import java.time.format.DateTimeFormatter;

/** RF10/RF09: historial de préstamos ya devueltos, con búsqueda por persona u objeto. */
public class HistorialView extends View {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PersonaService personaService;
    private final ObjetoService objetoService;

    private final ObservableList<Prestamo> historial = FXCollections.observableArrayList();
    private final FilteredList<Prestamo> filtrados = new FilteredList<>(historial, p -> true);
    private final ListView<Prestamo> lista = new ListView<>(filtrados);
    private final TextField campoBusqueda = new TextField();

    public HistorialView(PrestamoService prestamoService, PersonaService personaService,
                          ObjetoService objetoService) {
        this.personaService = personaService;
        this.objetoService = objetoService;
        setCenter(construirContenido());
        historial.setAll(prestamoService.listarHistorial());
        setOnShowing(e -> historial.setAll(prestamoService.listarHistorial()));
    }

    @Override
    protected void updateAppBar(AppBar appBar) {
        appBar.setNavIcon(MaterialDesignIcon.MENU.button(
            e -> MobileApplication.getInstance().getDrawer().open()));
        appBar.setTitleText("Historial");
    }

    private VBox construirContenido() {
        campoBusqueda.setPromptText("Buscar por persona u objeto...");
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
                VBox textos = new VBox(2,
                    new Label(nombrePersona(prestamo) + " — " + nombresObjetos(prestamo)),
                    new Label("Devuelto el " + prestamo.getFechaDevolucionReal().toLocalDate() + " "
                        + prestamo.getFechaDevolucionReal().toLocalTime().format(HORA)));
                ((Label) textos.getChildren().get(1)).setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
                setGraphic(textos);
                setText(null);
            }
        });

        VBox raiz = new VBox(8, campoBusqueda, lista);
        raiz.setPadding(new Insets(8));
        VBox.setVgrow(lista, Priority.ALWAYS);
        return raiz;
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
        filtrados.setPredicate(p -> q.isBlank()
            || nombrePersona(p).toLowerCase().contains(q)
            || nombresObjetos(p).toLowerCase().contains(q));
    }
}
