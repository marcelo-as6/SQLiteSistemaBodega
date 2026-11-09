package com.example.sqlite;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ejecuta consultas y escrituras SQLite fuera del hilo de interfaz. */
public final class DatabaseExecutor {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "bodega-db-worker");
        thread.setDaemon(true);
        return thread;
    });

    public void execute(Runnable task) {
        executor.execute(task);
    }

    public void close() {
        executor.shutdownNow();
    }
}
