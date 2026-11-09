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

/** Informe de productos activos con filtros por categoría, producto y disponibilidad. */
public class StockReportActivity extends AppCompatActivity {
    private final DatabaseExecutor worker = new DatabaseExecutor();
    private final ArrayList<Choice> categories = new ArrayList<>();
    private final ArrayList<Choice> products = new ArrayList<>();
    private final ArrayList<String> rows = new ArrayList<>();
    private DBHelper helper;
    private Spinner categoryFilter;
    private Spinner productFilter;
    private Spinner statusFilter;
    private TextView summary;
    private TextView empty;
    private ArrayAdapter<String> reportAdapter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SessionStore s=new SessionStore(this);if (!s.active()||!PermissionStore.allowed(this,s.id(),"REPORT_VIEW")) { finish(); return; }
        helper = new DBHelper(this);
        setContentView(R.layout.activity_stock_report);
        categoryFilter = findViewById(R.id.spinnerReportCategory);
        productFilter = findViewById(R.id.spinnerReportProduct);
        statusFilter = findViewById(R.id.spinnerReportStatus);
        summary = findViewById(R.id.tvReportSummary);
        empty = findViewById(R.id.tvReportEmpty);
        ListView list = findViewById(R.id.listStockReport);
        reportAdapter = new ArrayAdapter<>(this, R.layout.item_list_row, rows);
        list.setAdapter(reportAdapter);
        list.setEmptyView(empty);

        findViewById(R.id.btnGenerateReport).setOnClickListener(v -> generateReport());
        loadFilterChoices();
    }

    private void loadFilterChoices() {
        worker.execute(() -> {
            ArrayList<Choice> loadedCategories = new ArrayList<>();
            ArrayList<Choice> loadedProducts = new ArrayList<>();
            try {
                SQLiteDatabase db = helper.getReadableDatabase();
                try (Cursor c = db.rawQuery(
                        "SELECT id_categoria,nombre_categoria FROM CATEGORIA WHERE activo=1 " +
                                "ORDER BY nombre_categoria COLLATE NOCASE", null)) {
                    while (c.moveToNext()) loadedCategories.add(new Choice(c.getLong(0), c.getString(1)));
                }
                try (Cursor c = db.rawQuery(
                        "SELECT id_producto,sku_codigo,nombre FROM PRODUCTO WHERE activo=1 " +
                                "ORDER BY nombre COLLATE NOCASE", null)) {
                    while (c.moveToNext()) loadedProducts.add(new Choice(c.getLong(0),
                            c.getString(2) + " · SKU " + c.getString(1)));
                } finally { db.close(); }
            } catch (Exception e) {
                runOnUiThread(() -> summary.setText("No se pudieron cargar los filtros del informe."));
                return;
            }

            runOnUiThread(() -> {
                categories.clear();
                categories.add(new Choice(-1, "Todas las categorías"));
                categories.addAll(loadedCategories);
                products.clear();
                products.add(new Choice(-1, "Todos los productos"));
                products.addAll(loadedProducts);
                categoryFilter.setAdapter(adapterFor(categories));
                productFilter.setAdapter(adapterFor(products));
                ArrayList<String> statuses = new ArrayList<>();
                statuses.add("Todos los estados");
                statuses.add(StockRules.label(StockRules.OUT_OF_STOCK));
                statuses.add(StockRules.label(StockRules.CRITICAL));
                statuses.add(StockRules.label(StockRules.NORMAL));
                statusFilter.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item, statuses));
                categoryFilter.setOnItemSelectedListener(new FilterListener(this::generateReport));
                productFilter.setOnItemSelectedListener(new FilterListener(this::generateReport));
                statusFilter.setOnItemSelectedListener(new FilterListener(this::generateReport));
                generateReport();
            });
        });
    }

    private ArrayAdapter<String> adapterFor(ArrayList<Choice> choices) {
        ArrayList<String> labels = new ArrayList<>();
        for (Choice choice : choices) labels.add(choice.label);
        return new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
    }

    private void generateReport() {
        if (categories.isEmpty() || products.isEmpty()) return;
        long categoryId = categories.get(Math.max(0, categoryFilter.getSelectedItemPosition())).id;
        long productId = products.get(Math.max(0, productFilter.getSelectedItemPosition())).id;
        int status = statusFilter.getSelectedItemPosition() - 1;

        worker.execute(() -> {
            ArrayList<String> result = new ArrayList<>();
            int criticalCount = 0;
            int emptyCount = 0;
            int normalCount = 0;
            String failure = null;
            SQLiteDatabase db = null;
            try {
                db = helper.getReadableDatabase();
                StringBuilder sql = new StringBuilder(
                        "SELECT p.sku_codigo,p.codigo_barras,p.nombre,p.stock_actual," +
                                "p.stock_minimo,p.unidad_medida,c.nombre_categoria " +
                                "FROM PRODUCTO p JOIN CATEGORIA c ON c.id_categoria=p.id_categoria " +
                                "WHERE p.activo=1");
                ArrayList<String> args = new ArrayList<>();
                if (categoryId > 0) { sql.append(" AND p.id_categoria=?"); args.add(String.valueOf(categoryId)); }
                if (productId > 0) { sql.append(" AND p.id_producto=?"); args.add(String.valueOf(productId)); }
                if (status == StockRules.OUT_OF_STOCK) sql.append(" AND p.stock_actual<=0");
                else if (status == StockRules.CRITICAL) sql.append(" AND p.stock_actual>0 AND p.stock_actual<=p.stock_minimo");
                else if (status == StockRules.NORMAL) sql.append(" AND p.stock_actual>p.stock_minimo");
                sql.append(" ORDER BY p.nombre COLLATE NOCASE");

                try (Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
                    while (c.moveToNext()) {
                        int stock = c.getInt(3);
                        int minimum = c.getInt(4);
                        int itemStatus = StockRules.status(stock, minimum);
                        if (itemStatus == StockRules.OUT_OF_STOCK) emptyCount++;
                        else if (itemStatus == StockRules.CRITICAL) criticalCount++;
                        else normalCount++;

                        String barcode = c.getString(1);
                        result.add(c.getString(2) + " · SKU " + c.getString(0) +
                                (barcode == null || barcode.isEmpty() ? "" : " · Barras " + barcode) +
                                "\nCategoría: " + c.getString(6) +
                                "\nStock: " + stock + " " + c.getString(5) +
                                " · Mínimo: " + minimum + " · " + StockRules.label(itemStatus));
                    }
                }
            } catch (Exception e) {
                failure = "No se pudo generar el informe. Revisa la base de datos.";
            } finally { if (db != null) db.close(); }

            final String error = failure;
            final int critical = criticalCount;
            final int outOfStock = emptyCount;
            final int normal = normalCount;
            runOnUiThread(() -> {
                rows.clear();
                rows.addAll(result);
                reportAdapter.notifyDataSetChanged();
                if (error != null) summary.setText(error);
                else summary.setText("Productos: " + result.size() +
                        " · Críticos: " + critical + " · Sin stock: " + outOfStock +
                        " · Normales: " + normal);
                empty.setText(result.isEmpty() ? "No hay productos para estos filtros." : "");
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
