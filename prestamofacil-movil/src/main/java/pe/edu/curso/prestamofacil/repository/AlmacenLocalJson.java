package pe.edu.curso.prestamofacil.repository;

import com.gluonhq.attach.storage.StorageService;
import com.google.gson.reflect.TypeToken;
import pe.edu.curso.prestamofacil.util.Json;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Un archivo JSON por entidad, guardado en el almacenamiento privado del
 * dispositivo (o en el home del usuario cuando se ejecuta en escritorio fuera
 * de Gluon). Cada operación de escritura es atómica a nivel de archivo
 * (escribe a un temporal y renombra) para no corromper datos si la app se
 * cierra a mitad de una escritura.
 */
public class AlmacenLocalJson implements AlmacenLocal {

    private final File directorio;
    private final ReentrantLock cerrojo = new ReentrantLock();

    public AlmacenLocalJson() {
        Optional<File> raiz = StorageService.create()
            .flatMap(StorageService::getPrivateStorage);
        this.directorio = raiz.orElseGet(() ->
            new File(System.getProperty("user.home"), ".prestamofacil"));
        if (!directorio.exists()) {
            directorio.mkdirs();
        }
    }

    @Override
    public <T> List<T> leerTodo(String nombreArchivo, Class<T> tipo) {
        cerrojo.lock();
        try {
            File archivo = new File(directorio, nombreArchivo);
            if (!archivo.exists()) {
                return new ArrayList<>();
            }
            Type tipoLista = TypeToken.getParameterized(List.class, tipo).getType();
            try (Reader r = Files.newBufferedReader(archivo.toPath(), StandardCharsets.UTF_8)) {
                List<T> resultado = Json.gson().fromJson(r, tipoLista);
                return resultado != null ? resultado : new ArrayList<>();
            } catch (IOException e) {
                throw new RuntimeException("No se pudo leer " + nombreArchivo, e);
            }
        } finally {
            cerrojo.unlock();
        }
    }

    @Override
    public <T> void escribirTodo(String nombreArchivo, List<T> elementos) {
        cerrojo.lock();
        try {
            File destino = new File(directorio, nombreArchivo);
            File temporal = new File(directorio, nombreArchivo + ".tmp");
            try (Writer w = Files.newBufferedWriter(temporal.toPath(), StandardCharsets.UTF_8)) {
                Json.gson().toJson(elementos, w);
            }
            Files.move(temporal.toPath(), destino.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo escribir " + nombreArchivo, e);
        } finally {
            cerrojo.unlock();
        }
    }
}
