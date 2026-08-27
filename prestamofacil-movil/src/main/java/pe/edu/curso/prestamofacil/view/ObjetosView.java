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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import pe.edu.curso.prestamofacil.model.Categoria;
import pe.edu.curso.prestamofacil.model.Objeto;
import pe.edu.curso.prestamofacil.service.CategoriaService;
import pe.edu.curso.prestamofacil.service.ObjetoService;

/**
 * RF03/RF04/RF09: alta, edición, baja y búsqueda de objetos prestables.
 * Cada objeto requiere una categoría (agregación, docx §15.5); si todavía no
 * hay ninguna, el propio formulario permite crear una al vuelo.
 */
public class ObjetosView extends View {

    private final ObjetoService objetoService;
    private final CategoriaService categoriaService;

    private final ObservableList<Objeto> objetos = FXCollections.observableArrayList();
    private final FilteredList<Objeto> filtrados = new FilteredList<>(objetos, o -> true);
    private final ListView<Objeto> lista = new ListView<>(filtrados);

    private final ObservableList<Categoria> categorias = FXCollections.observableArrayList();
    private final ComboBox<Categoria> comboCategoria = new ComboBox<>(categorias);
    private final TextField campoNuevaCategoria = new TextField();

    private final TextField campoBusqueda = new TextField();
    private final TextField campoNombre = new TextField();
    private final TextField campoDescripcion = new TextField();
    private final Label estado = new Label();
    private final Button botonGuardar = new Button("Registrar objeto");
    private final Button botonNuevo = new Button("Nuevo");

    private Objeto seleccionado;

    public ObjetosView(ObjetoService objetoService, CategoriaService categoriaService) {
        this.objetoService = objetoService;
        this.categoriaService = categoriaService;
        setCenter(construirContenido());
        cargarCategorias();
        cargarObjetos();
        setOnShowing(e -> {
            cargarCategorias();
            cargarObjetos();
        });
    }

    @Override
    protected void updateAppBar(AppBar appBar) {
        appBar.setNavIcon(MaterialDesignIcon.MENU.button(
            e -> MobileApplication.getInstance().getDrawer().open()));
        appBar.setTitleText("Objetos");
    }

    private VBox construirContenido() {
        campoBusqueda.setPromptText("Buscar por nombre...");
        campoBusqueda.textProperty().addListener((obs, antes, texto) -> aplicarFiltro(texto));

        lista.setCellFactory(v -> {
            ListCell<Objeto> celda = new ListCell<>() {
                @Override
                protected void updateItem(Objeto objeto, boolean vacio) {
                    super.updateItem(objeto, vacio);
                    if (vacio || objeto == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    setGraphic(filaObjeto(objeto));
                    setText(null);
                }
            };
            // Clic en la celda completa, no en el HBox interno (ver PersonasView).
            celda.setOnMouseClicked(e -> {
                if (!celda.isEmpty() && celda.getItem() != null) {
                    cargarEnFormulario(celda.getItem());
                }
            });
            return celda;
        });

        comboCategoria.setPromptText("Categoría");
        comboCategoria.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Categoria c) { return c == null ? "" : c.getNombre(); }
            @Override public Categoria fromString(String s) { return null; }
        });
        campoNuevaCategoria.setPromptText("...o crea una categoría nueva aquí");
        Button botonNuevaCategoria = new Button("+");
        botonNuevaCategoria.setOnAction(e -> crearCategoria());
        HBox filaCategoria = new HBox(6, comboCategoria, campoNuevaCategoria, botonNuevaCategoria);
        HBox.setHgrow(comboCategoria, Priority.ALWAYS);
        HBox.setHgrow(campoNuevaCategoria, Priority.ALWAYS);

        campoNombre.setPromptText("Nombre");
        campoDescripcion.setPromptText("Descripción");
        estado.setStyle("-fx-text-fill: #b00020; -fx-font-size: 11px;");

        botonGuardar.setOnAction(e -> guardar());
        botonNuevo.setOnAction(e -> limpiarFormulario());
        HBox botones = new HBox(8, botonGuardar, botonNuevo);

        VBox formulario = new VBox(6, new Label("Categoría*"), filaCategoria,
            new Label("Nombre*"), campoNombre, new Label("Descripción"), campoDescripcion,
            botones, estado);
        formulario.setPadding(new Insets(12));
        formulario.setStyle("-fx-background-color: derive(-fx-background, -3%);");

        VBox raiz = new VBox(8, campoBusqueda, lista, formulario);
        raiz.setPadding(new Insets(8));
        VBox.setVgrow(lista, Priority.ALWAYS);
        return raiz;
    }

    private HBox filaObjeto(Objeto objeto) {
        String nombreCategoria = categorias.stream()
            .filter(c -> c.getUuid().equals(objeto.getCategoriaUuid()))
            .findFirst().map(Categoria::getNombre).orElse("(sin categoría)");
        String estadoTexto = objeto.estaDisponible() ? "Disponible" : "Prestado";

        VBox textos = new VBox(2,
            new Label(objeto.getNombre()),
            new Label(nombreCategoria + " · " + estadoTexto));
        ((Label) textos.getChildren().get(1)).setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        HBox.setHgrow(textos, Priority.ALWAYS);

        Button eliminar = MaterialDesignIcon.DELETE.button(e -> eliminar(objeto));

        HBox fila = new HBox(8, textos, eliminar);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }

    private void aplicarFiltro(String texto) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        filtrados.setPredicate(o -> q.isBlank() || o.getNombre().toLowerCase().contains(q));
    }

    private void crearCategoria() {
        String nombre = campoNuevaCategoria.getText();
        if (nombre == null || nombre.isBlank()) {
            estado.setText("Escribe un nombre para la nueva categoría.");
            return;
        }
        Categoria creada = categoriaService.registrar(nombre.trim(), "");
        campoNuevaCategoria.clear();
        cargarCategorias();
        comboCategoria.setValue(creada);
        estado.setText("");
        new Toast("Categoría \"" + creada.getNombre() + "\" creada").show();
    }

    private void cargarEnFormulario(Objeto objeto) {
        seleccionado = objeto;
        campoNombre.setText(objeto.getNombre());
        campoDescripcion.setText(objeto.getDescripcion());
        categorias.stream().filter(c -> c.getUuid().equals(objeto.getCategoriaUuid()))
            .findFirst().ifPresent(comboCategoria::setValue);
        botonGuardar.setText("Guardar cambios");
        estado.setText("");
    }

    private void limpiarFormulario() {
        seleccionado = null;
        campoNombre.clear();
        campoDescripcion.clear();
        comboCategoria.setValue(null);
        botonGuardar.setText("Registrar objeto");
        estado.setText("");
    }

    private void guardar() {
        Categoria categoria = comboCategoria.getValue();
        if (categoria == null) {
            estado.setText("Elige o crea una categoría.");
            return;
        }
        try {
            boolean esNuevo = seleccionado == null;
            if (esNuevo) {
                objetoService.registrar(categoria.getUuid(), campoNombre.getText(), campoDescripcion.getText());
            } else {
                seleccionado.setCategoriaUuid(categoria.getUuid());
                seleccionado.setNombre(campoNombre.getText());
                seleccionado.setDescripcion(campoDescripcion.getText());
                objetoService.actualizar(seleccionado);
            }
            limpiarFormulario();
            cargarObjetos();
            if (esNuevo) {
                new Toast("Objeto registrado").show();
            }
        } catch (IllegalArgumentException ex) {
            estado.setText(ex.getMessage());
        }
    }

    /** RNF06: un objeto prestado no se puede eliminar; se lo bloquea en el servicio, aquí solo se muestra el motivo. */
    private void eliminar(Objeto objeto) {
        boolean confirmado = Dialogos.confirmar("Eliminar objeto",
            "¿Eliminar \"" + objeto.getNombre() + "\"? Esta acción no se puede deshacer.", "Eliminar");
        if (!confirmado) {
            return;
        }
        try {
            objetoService.eliminar(objeto.getUuid());
            if (objeto.equals(seleccionado)) {
                limpiarFormulario();
            }
            cargarObjetos();
        } catch (IllegalStateException ex) {
            estado.setText(ex.getMessage());
        }
    }

    private void cargarCategorias() {
        categorias.setAll(categoriaService.listar());
    }

    private void cargarObjetos() {
        objetos.setAll(objetoService.listarTodos());
        lista.refresh();
    }
}
