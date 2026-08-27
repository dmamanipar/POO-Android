---
name: gluon-attach-notifications
description: How PréstamoFácil schedules and cancels local device notifications via Gluon Attach's LocalNotificationsService, and the desktop fallback. Use when working on AlertaService, NotificadorMovil, NotificadorEscritorio, or debugging a notification that doesn't fire or doesn't cancel.
---

# Local notifications (Gluon Attach)

## Dependency
`com.gluonhq.attach:local-notifications:<version>` matching the Attach BOM version in `pom.xml`. Verify the exact version against the current Gluon Attach documentation before pinning it — do not assume a version from memory, the Attach API has had breaking changes across majors.

## The interface every screen/service depends on
```java
public interface Notificador {
    void programar(String id, ZonedDateTime cuando, String titulo, String texto);
    void cancelar(String id);
}
```
`id` must be stable and derived from the préstamo's `uuid` (e.g. the uuid string itself, or a stable hash) so a later `cancelar` call can find it — never an auto-incrementing counter that resets across sessions.

## Mobile implementation sketch
```java
public class NotificadorMovil implements Notificador {
    public void programar(String id, ZonedDateTime cuando, String titulo, String texto) {
        Notification n = Notification.builder()
            .title(titulo)
            .text(texto)
            .id(id.hashCode())
            .dateTime(cuando)
            .build();
        com.gluonhq.attach.notifications.NotificationsService.create()
            .ifPresent(s -> s.notify(n));
    }
    public void cancelar(String id) {
        com.gluonhq.attach.notifications.NotificationsService.create()
            .ifPresent(s -> s.unregisterNotification(/* the same Notification instance or id */ id.hashCode()));
    }
}
```
Check the current Attach docs for the exact method names on `NotificationsService` for your pinned version — `notify`/`register`/`unregisterNotification` naming has varied.

## Desktop fallback
`NotificadorEscritorio` keeps a `Map<String, ScheduledFuture<?>>` on a single-thread `ScheduledExecutorService`, computes the delay to `cuando`, and on fire shows a Glisten `Dialog` (via `Platform.runLater`) instead of a system notification. `cancelar` looks up the id and calls `future.cancel(false)`. This exists purely so the team can develop and demo the alert logic on a laptop without an Android device.

## Reconciliation on app start
`AlertaService.reconciliarAlAbrir()` must iterate every `Prestamo` with `estado != DEVUELTO` and a future due date, and call `programar` again. This is required because:
- A fresh install has no memory of previously scheduled alarms.
- Some Android versions clear exact alarms on reboot.
Do this once, early in `postInit`, off the FX thread if the préstamo list is large.

## Permissions (Android 13+)
`POST_NOTIFICATIONS` is a runtime permission. Request it the first time the user saves a préstamo, not at app launch (launch-time blanket permission requests are poor UX and more likely to be denied). If denied, `NotificadorMovil.programar` should fail silently and the app should still show due/overdue loans inside the Dashboard — the in-app view must never depend on the OS notification having fired.
