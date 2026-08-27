package pe.edu.curso.prestamofacil.view;

import com.gluonhq.charm.glisten.application.MobileApplication;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.control.Toast;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import pe.edu.curso.prestamofacil.model.PersonaBase;
import pe.edu.curso.prestamofacil.service.PersonaService;

/**
 * RF01/RF02/RF09: alta, edición, baja y búsqueda de personas. Tocar una fila
 * carga esa persona en el formulario para editarla; "Nuevo" limpia la
 * selección para volver a dar de alta. Ver .claude/skills/gluon-glisten-ui/SKILL.md.
 */
public class PersonasView extends View {

    private final PersonaService personaService;

    private final ObservableList<PersonaBase> personas = FXCollections.observableArrayList();
    private final FilteredList<PersonaBase> filtradas = new FilteredList<>(personas, p -> true);
    private final ListView<PersonaBase> lista = new ListView<>(filtradas);

    private final TextField campoBusqueda = new TextField();
    private final TextField campoNombre = new TextField();
    private final TextField campoTelefono = new TextField();
    private final TextField campoCorreo = new TextField();
    private final Label estado = new Label();
    private final Button botonGuardar = new Button("Registrar persona");
    private final Button botonNuevo = new Button("Nuevo");

    private PersonaBase seleccionada;

    public PersonasView(PersonaService personaService) {
        this.personaService = personaService;
        setCenter(construirContenido());
        cargar();
        setOnShowing(e -> cargar());
    }

    @Override
    protected void updateAppBar(AppBar appBar) {
        appBar.setNavIcon(MaterialDesignIcon.MENU.button(
            e -> MobileApplication.getInstance().getDrawer().open()));
        appBar.setTitleText("Personas");
    }

    private BorderPane construirContenido() {
        campoBusqueda.setPromptText("Buscar por nombre...");
        campoBusqueda.textProperty().addListener((obs, antes, texto) -> aplicarFiltro(texto));

        lista.setCellFactory(v -> {
            ListCell<PersonaBase> celda = new ListCell<>() {
                @Override
                protected void updateItem(PersonaBase persona, boolean vacio) {
                    super.updateItem(persona, vacio);
                    if (vacio || persona == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    setGraphic(filaPersona(persona));
                    setText(null);
                }
            };
            // El clic va en la celda completa (no en el HBox interno, que no
            // ocupa todo el ancho de la fila): así funciona sin importar
            // dónde dentro de la fila se toque, salvo el botón eliminar
            // (Button consume su propio MouseEvent y no llega hasta aquí).
            celda.setOnMouseClicked(e -> {
                if (!celda.isEmpty() && celda.getItem() != null) {
                    cargarEnFormulario(celda.getItem());
                }
            });
            return celda;
        });

        campoNombre.setPromptText("Nombre");
        campoTelefono.setPromptText("Teléfono");
        campoCorreo.setPromptText("Correo");
        estado.setStyle("-fx-text-fill: #b00020; -fx-font-size: 11px;");

        botonGuardar.setOnAction(e -> guardar());
        botonNuevo.setOnAction(e -> limpiarFormulario());
        HBox botones = new HBox(8, botonGuardar, botonNuevo);

        VBox formulario = new VBox(6, new Label("Nombre*"), campoNombre,
            new Label("Teléfono"), campoTelefono, new Label("Correo"), campoCorreo,
            botones, estado);
        formulario.setPadding(new Insets(12));
        formulario.setStyle("-fx-background-color: derive(-fx-background, -3%);");

        VBox raiz = new VBox(8, campoBusqueda, lista, formulario);
        raiz.setPadding(new Insets(8));
        VBox.setVgrow(lista, Priority.ALWAYS);

        BorderPane contenedor = new BorderPane();
        contenedor.setCenter(raiz);
        return contenedor;
    }

    private HBox filaPersona(PersonaBase persona) {
        VBox textos = new VBox(2,
            new Label(persona.getNombre()),
            new Label(subtitulo(persona)));
        ((Label) textos.getChildren().get(1)).setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        HBox.setHgrow(textos, Priority.ALWAYS);

        Button eliminar = MaterialDesignIcon.DELETE.button(e -> eliminar(persona));

        HBox fila = new HBox(8, textos, eliminar);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }

    private String subtitulo(PersonaBase persona) {
        String telefono = persona.getTelefono() == null || persona.getTelefono().isBlank()
            ? "" : persona.getTelefono();
        String correo = persona.getCorreo() == null || persona.getCorreo().isBlank()
            ? "" : persona.getCorreo();
        String detalle = String.join(" · ", java.util.stream.Stream.of(telefono, correo)
            .filter(s -> !s.isBlank()).toList());
        return detalle.isBlank() ? persona.descripcionRol() : detalle;
    }

    private void aplicarFiltro(String texto) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        filtradas.setPredicate(p -> q.isBlank() || p.getNombre().toLowerCase().contains(q));
    }

    private void cargarEnFormulario(PersonaBase persona) {
        seleccionada = persona;
        campoNombre.setText(persona.getNombre());
        campoTelefono.setText(persona.getTelefono());
        campoCorreo.setText(persona.getCorreo());
        botonGuardar.setText("Guardar cambios");
        estado.setText("");
    }

    private void limpiarFormulario() {
        seleccionada = null;
        campoNombre.clear();
        campoTelefono.clear();
        campoCorreo.clear();
        botonGuardar.setText("Registrar persona");
        estado.setText("");
    }

    private void guardar() {
        try {
            boolean esNueva = seleccionada == null;
            if (esNueva) {
                personaService.registrar(campoNombre.getText(), campoTelefono.getText(), campoCorreo.getText());
            } else {
                seleccionada.setNombre(campoNombre.getText());
                seleccionada.setTelefono(campoTelefono.getText());
                seleccionada.setCorreo(campoCorreo.getText());
                personaService.actualizar(seleccionada);
            }
            limpiarFormulario();
            cargar();
            if (esNueva) {
                new Toast("Persona registrada").show();
            }
        } catch (IllegalArgumentException ex) {
            estado.setText(ex.getMessage());
        }
    }

    private void eliminar(PersonaBase persona) {
        boolean confirmado = Dialogos.confirmar("Eliminar persona",
            "¿Eliminar a \"" + persona.getNombre() + "\"? Esta acción no se puede deshacer.", "Eliminar");
        if (!confirmado) {
            return;
        }
        personaService.eliminar(persona.getUuid());
        if (persona.equals(seleccionada)) {
            limpiarFormulario();
        }
        cargar();
    }

    private void cargar() {
        personas.setAll(personaService.listar());
    }
}
