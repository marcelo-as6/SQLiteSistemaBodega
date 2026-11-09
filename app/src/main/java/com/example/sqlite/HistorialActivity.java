package com.example.sqlite;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

/** Historial persistente de entradas y salidas, filtrable por producto y usuario. */
public class HistorialActivity extends AppCompatActivity {
    private final DatabaseExecutor worker = new DatabaseExecutor();
    private final ArrayList<Choice> products = new ArrayList<>();
    private final ArrayList<Choice> users = new ArrayList<>();
    private final ArrayList<String> rows = new ArrayList<>();
    private DBHelper helper;
    private Spinner productFilter;
    private Spinner userFilter;
    private Spinner typeFilter;
    private TextView summary;
    private TextView empty;
    private ArrayAdapter<String> historyAdapter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SessionStore s=new SessionStore(this);if (!s.active()||!PermissionStore.allowed(this,s.id(),"HISTORY_VIEW")) { finish(); return; }
        helper = new DBHelper(this);
        setContentView(R.layout.activity_history);
        productFilter = findViewById(R.id.spinnerHistoryProduct);
        userFilter = findViewById(R.id.spinnerHistoryUser);
        typeFilter = findViewById(R.id.spinnerHistoryType);
        summary = findViewById(R.id.tvHistorySummary);
        empty = findViewById(R.id.tvHistoryEmpty);
        ListView list = findViewById(R.id.listHistory);
        historyAdapter = new ArrayAdapter<>(this, R.layout.item_list_row, rows);
        list.setAdapter(historyAdapter);
        list.setEmptyView(empty);
        findViewById(R.id.btnApplyHistoryFilters).setOnClickListener(v -> loadHistory());
        loadFilterChoices();
    }

    private void loadFilterChoices() {
        worker.execute(() -> {
            ArrayList<Choice> loadedProducts = new ArrayList<>();
            ArrayList<Choice> loadedUsers = new ArrayList<>();
            try {
                SQLiteDatabase db = helper.getReadableDatabase();
                try (Cursor c = db.rawQuery(
                        "SELECT id_producto,sku_codigo,nombre,activo FROM PRODUCTO " +
                                "ORDER BY nombre COLLATE NOCASE", null)) {
                    while (c.moveToNext()) loadedProducts.add(new Choice(c.getLong(0),
                            c.getString(2) + " · SKU " + c.getString(1) +
                                    (c.getInt(3) == 1 ? "" : " · Inactivo")));
                }
                try (Cursor c = db.rawQuery(
                        "SELECT id_usuario,username,nombre_completo,activo FROM USUARIO " +
                                "ORDER BY nombre_completo COLLATE NOCASE", null)) {
                    while (c.moveToNext()) loadedUsers.add(new Choice(c.getLong(0),
                            c.getString(2) + " · @" + c.getString(1) +
                                    (c.getInt(3) == 1 ? "" : " · Inactivo")));
                } finally { db.close(); }
            } catch (Exception e) {
                runOnUiThread(() -> summary.setText("No se pudieron cargar los filtros del historial."));
                return;
            }

            runOnUiThread(() -> {
                products.clear();
                products.add(new Choice(-1, "Todos los productos"));
                products.addAll(loadedProducts);
                users.clear();
                users.add(new Choice(-1, "Todos los usuarios"));
                users.addAll(loadedUsers);
                productFilter.setAdapter(adapterFor(products));
                userFilter.setAdapter(adapterFor(users));
                ArrayList<String> types = new ArrayList<>();
                types.add("Todos los movimientos");
                types.add("Entradas");
                types.add("Salidas");
                typeFilter.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item, types));
                productFilter.setOnItemSelectedListener(new FilterListener(this::loadHistory));
                userFilter.setOnItemSelectedListener(new FilterListener(this::loadHistory));
                typeFilter.setOnItemSelectedListener(new FilterListener(this::loadHistory));
                loadHistory();
            });
        });
    }

    private ArrayAdapter<String> adapterFor(ArrayList<Choice> choices) {
        ArrayList<String> labels = new ArrayList<>();
        for (Choice choice : choices) labels.add(choice.label);
        return new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
    }

    private void loadHistory() {
        if (products.isEmpty() || users.isEmpty()) return;
        long productId = products.get(Math.max(0, productFilter.getSelectedItemPosition())).id;
        long userId = users.get(Math.max(0, userFilter.getSelectedItemPosition())).id;
        int typePosition = typeFilter.getSelectedItemPosition();

        worker.execute(() -> {
            ArrayList<String> result = new ArrayList<>();
            int entries = 0;
            int exits = 0;
            String failure = null;
            SQLiteDatabase db = null;
            try {
                db = helper.getReadableDatabase();
                StringBuilder sql = new StringBuilder(
                        "SELECT m.fecha,m.tipo,m.cantidad,p.sku_codigo,p.nombre," +
                                "u.nombre_completo,u.username,m.nota,pr.razon_social " +
                                "FROM MOVIMIENTO m JOIN PRODUCTO p ON p.id_producto=m.id_producto " +
                                "LEFT JOIN USUARIO u ON u.id_usuario=m.id_usuario " +
                                "LEFT JOIN PROVEEDOR pr ON pr.id_proveedor=m.id_proveedor WHERE 1=1");
                ArrayList<String> args = new ArrayList<>();
                if (productId > 0) { sql.append(" AND m.id_producto=?"); args.add(String.valueOf(productId)); }
                if (userId > 0) { sql.append(" AND m.id_usuario=?"); args.add(String.valueOf(userId)); }
                if (typePosition == 1) sql.append(" AND m.tipo='ENTRADA'");
                else if (typePosition == 2) sql.append(" AND m.tipo='SALIDA'");
                sql.append(" ORDER BY m.fecha DESC,m.id_movimiento DESC");

                try (Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
                    while (c.moveToNext()) {
                        boolean incoming = "ENTRADA".equals(c.getString(1));
                        if (incoming) entries++; else exits++;
                        String userName = c.getString(5);
                        if (userName == null || userName.isEmpty()) userName = "Usuario no disponible";
                        String note = c.getString(7);
                        String supplier = c.getString(8);
                        result.add((incoming ? "ENTRADA" : "SALIDA") + " · " + c.getInt(2) +
                                " unidades\nProducto: " + c.getString(4) + " · SKU " + c.getString(3) +
                                "\nUsuario: " + userName +
                                "\nFecha: " + c.getString(0) +
                                (note == null || note.trim().isEmpty() ? "" : "\nMotivo / nota: " + note) +
                                (supplier == null || supplier.trim().isEmpty() ? "" : "\nProveedor: " + supplier));
                    }
                }
            } catch (Exception e) {
                failure = "No se pudo consultar el historial.";
            } finally { if (db != null) db.close(); }

            final String error = failure;
            final int entryCount = entries;
            final int exitCount = exits;
            runOnUiThread(() -> {
                rows.clear();
                rows.addAll(result);
                historyAdapter.notifyDataSetChanged();
                if (error != null) summary.setText(error);
                else summary.setText("Movimientos: " + result.size() +
                        " · Entradas: " + entryCount + " · Salidas: " + exitCount);
                empty.setText(result.isEmpty() ? "No hay movimientos para estos filtros." : "");
            });
        });
    }

    private static final class Choice {
        final long id;
        final String label;
        Choice(long id, String label) { this.id = id; this.label = label; }
    }

    @Override protected void onDestroy() {
        worker.close();
        if (helper != null) helper.close();
        super.onDestroy();
    }
}
