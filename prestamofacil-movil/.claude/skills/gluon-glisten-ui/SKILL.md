---
name: gluon-glisten-ui
description: Reference patterns for building screens with Gluon Glisten (View, MobileApplication, AppBar, NavigationDrawer, CharmListView, Dialog, layer transitions). Use when creating or editing any class under view/ or wiring navigation between Glisten views.
---

# Gluon Glisten UI patterns

## App entry point
`Main.java` extends `com.gluonhq.charm.glisten.application.MobileApplication`. Views are registered by name (a `String` constant) via `addViewFactory(NAME, supplier)`. Navigate with `switchView(NAME)`, which returns the `View` so you can attach a transition:

```java
public class Main extends MobileApplication {
    public static final String DASHBOARD_VIEW = "Dashboard";
    public static final String NUEVO_PRESTAMO_VIEW = "NuevoPrestamo";

    @Override
    public void init() {
        addViewFactory(DASHBOARD_VIEW, DashboardView::new);
        addViewFactory(NUEVO_PRESTAMO_VIEW, NuevoPrestamoView::new);
    }

    @Override
    public void postInit(Scene scene) {
        Swatch.BLUE.assignTo(scene);
        switchView(DASHBOARD_VIEW);
    }
}
```

## Navigation: one NavigationDrawer for the whole app
Registered once in `Main`, not per-view. Each screen is a `NavigationDrawer.ViewItem(title, graphic, viewName)` — selecting one calls `switchView(viewName)` automatically:

```java
NavigationDrawer drawer = getDrawer(); // MobileApplication.getDrawer()
drawer.setHeader(new NavigationDrawer.Header("PréstamoFácil", "..."));
drawer.getItems().addAll(
    new NavigationDrawer.ViewItem("Dashboard", MaterialDesignIcon.DASHBOARD.graphic(), DASHBOARD_VIEW),
    new NavigationDrawer.ViewItem("Personas", MaterialDesignIcon.PEOPLE.graphic(), PERSONAS_VIEW));
```

Every view's `updateAppBar` opens that same drawer from a `MENU` icon in the nav-icon slot (`appBar.setNavIcon(MaterialDesignIcon.MENU.button(e -> MobileApplication.getInstance().getDrawer().open()))`) — don't give individual views their own back-arrow navigation on top of this; the drawer is the only navigation model in this app, every screen is a sibling top-level destination reachable from every other screen.

## A view class
Each view extends `View`, builds its content in the constructor, and configures its `AppBar` in `updateAppBar` (called every time the view becomes active — don't set the app bar once in the constructor, it won't stick). `View` has no constructor that takes the registered name — it's only `View()`/`View(Node)`; the name is purely the `String` key used with `addViewFactory`/`switchView`:

```java
public class DashboardView extends View {
    public DashboardView() {
        setCenter(buildContent());
    }

    @Override
    protected void updateAppBar(AppBar appBar) {
        appBar.setNavIcon(MaterialDesignIcon.MENU.button(e -> MobileApplication.getInstance().getDrawer().open()));
        appBar.setTitleText("PréstamoFácil");
        appBar.getActionItems().add(MaterialDesignIcon.SYNC.button(e -> onSyncPressed()));
    }
}
```

## Views are cached — refresh on every show, not just in the constructor
`addViewFactory`'s supplier runs once per view name; Gluon caches the resulting `View` instance (`CachedFactory`) and reuses it on every later `switchView`/drawer navigation. If a view loads its data only in the constructor, returning to it later shows whatever was true the first time it was ever built — this bit every list/summary view in this project (Dashboard kept showing "0 disponibles" after objects were registered elsewhere) until each one's data-loading call was duplicated into `setOnShowing(...)`:

```java
public DashboardView(...) {
    setCenter(construirContenido());
    refrescar();
    setOnShowing(e -> refrescar()); // re-run every time the view becomes visible again
}
```

## Lists
This project uses a plain `javafx.scene.control.ListView<T>` with a custom `cellFactory` (not `CharmListView`, which hasn't actually been tried here — verify its API before introducing it). Two things that matter with a custom cell graphic:
- Bind the backing list to an `ObservableList` (optionally wrapped in a `FilteredList` for search-as-you-type) and mutate it on the FX thread only.
- If a row should react to being tapped (e.g. "tap to edit"), attach `setOnMouseClicked` to the `ListCell` itself, **not** to the inner `HBox`/`VBox` you put in `setGraphic(...)`. The inner container only occupies its own preferred width, not the full row — clicks in the rest of the row still highlight it (`ListView`'s default selection), but silently do nothing if the handler is on the inner node. A per-row action `Button` (e.g. delete) still works fine placed inside that inner container, since `Button` consumes the click before it would bubble to the cell.

```java
lista.setCellFactory(v -> {
    ListCell<Prestamo> celda = new ListCell<>() {
        @Override protected void updateItem(Prestamo item, boolean vacio) {
            super.updateItem(item, vacio);
            setGraphic(vacio || item == null ? null : construirFila(item));
            setText(null);
        }
    };
    celda.setOnMouseClicked(e -> { if (!celda.isEmpty()) cargarEnFormulario(celda.getItem()); });
    return celda;
});
```

## Background work
Never call a service (which may touch the local JSON store or the network) directly from an event handler on the FX thread. Wrap it:

```java
Task<Void> task = new Task<>() {
    protected Void call() {
        sincronizacionService.sincronizar();
        return null;
    }
};
task.setOnSucceeded(e -> Platform.runLater(this::refreshDashboard));
new Thread(task).start();
```

## Date + time input
Glisten doesn't ship a time picker; pair JavaFX's `DatePicker` with two `ChoiceBox<Integer>` (hour 0–23, minute in 5-minute steps) or use Gluon's `PickerBase`-derived components if the project already depends on `charm-glisten-afterburner`. Always combine into a `ZonedDateTime` using `ZoneId.systemDefault()` before handing it to the service layer — never pass a bare `LocalDate`/`LocalTime` pair past the controller boundary.

## Dialogs
Use `com.gluonhq.charm.glisten.control.Dialog` for the "préstamos vencidos hoy" popup shown on launch, not a JavaFX `Alert` — `Alert` doesn't follow Glisten's mobile styling.
