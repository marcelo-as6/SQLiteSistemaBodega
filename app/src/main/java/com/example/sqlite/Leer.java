package com.example.sqlite;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

/** Catálogo de productos, búsqueda manual y consulta mediante código de barras. */
public class Leer extends AppCompatActivity {
    public static final String EXTRA_PRODUCT_SKU = "com.example.sqlite.PRODUCT_SKU";

    private final DatabaseExecutor worker = new DatabaseExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pendingProductLoad = this::loadProducts;
    private DBHelper helper;
    private EditText search;
    private Spinner category;
    private ListView list;
    private TextView empty;
    private ArrayList<Long> categoryIds;
    private final ArrayList<Long> productIds = new ArrayList<>();
    private final ArrayList<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private int productLoadRequest;
    private boolean categoryListenerInstalled;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SessionStore userSession=new SessionStore(this);
        if (!userSession.active()||!PermissionStore.allowed(this,userSession.id(),"PRODUCT_VIEW")) { finish(); return; }
        helper = new DBHelper(this);
        setContentView(R.layout.activity_inventory);
        search = findViewById(R.id.etSearch);
        category = findViewById(R.id.spinnerCategory);
        list = findViewById(R.id.listProducts);
        empty = findViewById(R.id.tvEmpty);
        adapter = new ArrayAdapter<>(this, R.layout.item_list_row, rows);
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        findViewById(R.id.btnAddProduct).setOnClickListener(v -> productForm(null));
        findViewById(R.id.btnAddProduct).setVisibility(PermissionStore.allowed(this,userSession.id(),"PRODUCT_CREATE")?View.VISIBLE:View.GONE);
        findViewById(R.id.btnScanBarcode).setOnClickListener(v -> scanForProduct());
        list.setOnItemClickListener((parent, view, position, id) -> actions(productIds.get(position)));
        categoryIds = new ArrayList<>();
        categoryIds.add(-1L);
        loadCategories();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(pendingProductLoad);
                handler.postDelayed(pendingProductLoad, 180L);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void loadCategories() {
        worker.execute(() -> {
            ArrayList<Long> ids = new ArrayList<>();
            ArrayList<String> names = new ArrayList<>();
            ids.add(-1L);
            names.add("Todas las categorías");
            SQLiteDatabase db = null;
            try {
                db = helper.getReadableDatabase();
                try (Cursor c = db.rawQuery(
                        "SELECT id_categoria,nombre_categoria FROM CATEGORIA " +
                                "WHERE activo=1 ORDER BY nombre_categoria COLLATE NOCASE", null)) {
                    while (c.moveToNext()) { ids.add(c.getLong(0)); names.add(c.getString(1)); }
                }
            } catch (Exception e) {
                runOnUiThread(() -> toast("No se pudieron cargar las categorías."));
            } finally { if (db != null) db.close(); }

            runOnUiThread(() -> {
                if (isFinishing()) return;
                categoryIds = ids;
                category.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item, names));
                if (!categoryListenerInstalled) {
                    categoryListenerInstalled = true;
                    category.setOnItemSelectedListener(new FilterListener(this::loadProducts));
                }
                loadProducts();
            });
        });
    }

    private void loadProducts() {
        if (adapter == null || categoryIds == null || categoryIds.isEmpty()) return;
        long categoryId = categoryIds.get(Math.max(0, category.getSelectedItemPosition()));
        String term = search.getText().toString().trim();
        String query = "%" + term + "%";
        int request = ++productLoadRequest;

        worker.execute(() -> {
            ArrayList<Long> ids = new ArrayList<>();
            ArrayList<String> loadedRows = new ArrayList<>();
            String failure = null;
            SQLiteDatabase db = null;
            try {
                db = helper.getReadableDatabase();
                String sql = "SELECT P.id_producto,P.sku_codigo,P.codigo_barras,P.nombre," +
                        "P.stock_actual,P.stock_minimo,C.nombre_categoria,P.unidad_medida " +
                        "FROM PRODUCTO P JOIN CATEGORIA C ON C.id_categoria=P.id_categoria " +
                        "WHERE P.activo=1 AND (P.nombre LIKE ? OR P.sku_codigo LIKE ? OR P.codigo_barras LIKE ?)" +
                        (categoryId > 0 ? " AND P.id_categoria=?" : "") +
                        " ORDER BY P.nombre COLLATE NOCASE";
                String[] args = categoryId > 0
                        ? new String[]{query, query, query, String.valueOf(categoryId)}
                        : new String[]{query, query, query};
                try (Cursor c = db.rawQuery(sql, args)) {
                    while (c.moveToNext()) {
                        ids.add(c.getLong(0));
                        String barcode = c.getString(2);
                        String lowStock = c.getInt(4) <= c.getInt(5) ? " · STOCK BAJO" : "";
                        loadedRows.add(c.getString(3) + " · SKU " + c.getString(1) +
                                (barcode == null || barcode.isEmpty() ? "" : "\nCódigo de barras: " + barcode) +
                                "\nStock actual: " + c.getInt(4) + " " + c.getString(7) +
                                " · Disponible: " + c.getInt(4) + " " + c.getString(7) +
                                "\nMínimo: " + c.getInt(5) + " · " + c.getString(6) + lowStock);
                    }
                }
            } catch (Exception e) {
                failure = "No se pudo consultar el catálogo.";
            } finally { if (db != null) db.close(); }

            final String error = failure;
            runOnUiThread(() -> {
                if (isFinishing() || request != productLoadRequest) return;
                productIds.clear();
                productIds.addAll(ids);
                rows.clear();
                if (error == null) rows.addAll(loadedRows);
                adapter.notifyDataSetChanged();
                if (error != null) empty.setText(error);
                else empty.setText(term.isEmpty() ? "No hay productos." : "No hay productos que coincidan con la búsqueda.");
            });
        });
    }

    private void scanForProduct() {
        BarcodeScanner.scan(this, new BarcodeScanner.Listener() {
            @Override public void onScanned(String value) {
                if (category != null && category.getSelectedItemPosition() > 0) category.setSelection(0);
                search.setText(value);
            }
            @Override public void onError() { toast("No se pudo leer el código. Puedes buscar por nombre o SKU."); }
        });
    }

    private EditText field(String hint, boolean number) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        if (number) editText.setInputType(2);
        return editText;
    }

    private void productForm(Long id) {
        SessionStore s=new SessionStore(this);if(!PermissionStore.allowed(this,s.id(),id==null?"PRODUCT_CREATE":"PRODUCT_EDIT")){toast("No tienes permiso para modificar productos.");return;}
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(padding, 0, padding, 0);
        EditText sku = field("Código SKU único", false);
        EditText barcode = field("Código de barras (opcional)", false);
        EditText name = field("Nombre del producto", false);
        EditText description = field("Descripción", false);
        EditText stock = field("Stock inicial", true);
        EditText minimum = field("Stock mínimo", true);
        EditText unit = field("Unidad de medida (ej. unidad, kg, litro)", false);
        Spinner categorySpinner = new Spinner(this);
        box.addView(sku);
        box.addView(barcode);
        box.addView(name);
        box.addView(description);
        box.addView(unit);
        box.addView(stock);
        box.addView(minimum);
        box.addView(categorySpinner);

        ArrayList<Long> categoryOptions = new ArrayList<>();
        ArrayList<String> categoryNames = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT id_categoria,nombre_categoria FROM CATEGORIA WHERE activo=1 " +
                        "ORDER BY nombre_categoria COLLATE NOCASE", null)) {
            while (c.moveToNext()) { categoryOptions.add(c.getLong(0)); categoryNames.add(c.getString(1)); }
        } finally { db.close(); }
        categorySpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, categoryNames));

        if (id != null) {
            stock.setVisibility(View.GONE);
            db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT sku_codigo,codigo_barras,nombre,descripcion,stock_minimo," +
                            "id_categoria,unidad_medida FROM PRODUCTO WHERE id_producto=?",
                    new String[]{String.valueOf(id)})) {
                if (c.moveToFirst()) {
                    sku.setText(c.getString(0));
                    name.setText(c.getString(2));
                    barcode.setText(c.getString(1));
                    description.setText(c.getString(3));
                    minimum.setText(String.valueOf(c.getInt(4)));
                    unit.setText(c.getString(6));
                    for (int i = 0; i < categoryOptions.size(); i++)
                        if (categoryOptions.get(i) == c.getLong(5)) categorySpinner.setSelection(i);
                }
            } finally { db.close(); }
        }

        new AlertDialog.Builder(this)
                .setTitle(id == null ? "Registrar producto" : "Editar producto")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialog, which) -> saveProduct(
                        id, sku.getText().toString().trim(), barcode.getText().toString().trim(),
                        name.getText().toString().trim(), description.getText().toString().trim(),
                        stock.getText().toString().trim(), minimum.getText().toString().trim(),
                        unit.getText().toString().trim(), categoryOptions,
                        categorySpinner.getSelectedItemPosition()))
                .show();
    }

    private void saveProduct(Long id, String sku, String barcode, String name, String description,
                             String initialStock, String minimumStock, String unit,
                             ArrayList<Long> categories, int selectedCategory) {
        SessionStore s=new SessionStore(this);if(!PermissionStore.allowed(this,s.id(),id==null?"PRODUCT_CREATE":"PRODUCT_EDIT")){toast("No tienes permiso para modificar productos.");return;}
        int stockValue;
        int minimumValue;
        try {
            stockValue = id == null ? Integer.parseInt(initialStock) : 0;
            minimumValue = minimumStock.isEmpty() ? 0 : Integer.parseInt(minimumStock);
            if (name.isEmpty() || unit.isEmpty() || categories.isEmpty() ||
                    selectedCategory < 0 || stockValue < 0 || minimumValue < 0 || sku.isEmpty())
                throw new IllegalArgumentException();
        } catch (Exception e) {
            toast("Completa los campos obligatorios y usa cantidades enteras no negativas.");
            return;
        }

        worker.execute(() -> {
            String failure = null;
            SQLiteDatabase db = null;
            try {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put("codigo_barras", barcode);
                values.put("sku_codigo", sku);
                values.put("nombre", name);
                values.put("descripcion", description);
                values.put("unidad_medida", unit);
                values.put("stock_minimo", minimumValue);
                values.put("id_categoria", categories.get(selectedCategory));
                db = helper.getWritableDatabase();
                if (id == null) {
                    values.put("stock_actual", stockValue);
                    db.insertOrThrow("PRODUCTO", null, values);
                } else {
                    db.update("PRODUCTO", values, "id_producto=?", new String[]{String.valueOf(id)});
                }
            } catch (android.database.sqlite.SQLiteConstraintException e) {
                failure = "El SKU o el código de barras ya pertenece a otro producto.";
            } catch (Exception e) {
                failure = "No se pudo guardar el producto. Revisa los datos ingresados.";
            } finally { if (db != null) db.close(); }

            String error = failure;
            runOnUiThread(() -> {
                if (error == null) toast("Producto guardado correctamente.");
                else toast(error);
                if (error == null) loadProducts();
            });
        });
    }

    private void actions(long id) {
        SessionStore s=new SessionStore(this);ArrayList<String> opts=new ArrayList<>();ArrayList<Integer> acts=new ArrayList<>();if(PermissionStore.allowed(this,s.id(),"PRODUCT_EDIT")){opts.add("Editar");acts.add(0);}if(PermissionStore.allowed(this,s.id(),"MOVEMENT_ENTRY")||PermissionStore.allowed(this,s.id(),"MOVEMENT_EXIT")){opts.add("Registrar entrada o salida");acts.add(1);}if(PermissionStore.allowed(this,s.id(),"PRODUCT_DISABLE")){opts.add("Deshabilitar");acts.add(2);}if(opts.isEmpty()){toast("No tienes acciones disponibles para este producto.");return;}
        new AlertDialog.Builder(this)
                .setTitle("Producto")
                .setItems(opts.toArray(new String[0]), (dialog, which) -> {
                    int action=acts.get(which);if (action == 0) productForm(id);
                    else if (action == 1) openMovement(id);
                    else confirmDisable(id);
                }).show();
    }

    private void openMovement(long id) {
        worker.execute(() -> {
            String sku = null;
            SQLiteDatabase db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery("SELECT sku_codigo FROM PRODUCTO WHERE id_producto=?",
                    new String[]{String.valueOf(id)})) {
                if (c.moveToFirst()) sku = c.getString(0);
            } finally { db.close(); }
            String productSku = sku;
            runOnUiThread(() -> {
                if (productSku == null) toast("No se encontró el producto.");
                else {
                    Intent intent = new Intent(this, MovimientosActivity.class);
                    intent.putExtra(EXTRA_PRODUCT_SKU, productSku);
                    startActivity(intent);
                }
            });
        });
    }

    private void confirmDisable(long id) {
        SessionStore s=new SessionStore(this);if(!PermissionStore.allowed(this,s.id(),"PRODUCT_DISABLE")){toast("No tienes permiso para desactivar productos.");return;}
        new AlertDialog.Builder(this)
                .setMessage("Se ocultará del catálogo y se conservará el historial. ¿Continuar?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Deshabilitar", (dialog, which) -> worker.execute(() -> {
                    SQLiteDatabase db = helper.getWritableDatabase();
                    try {
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put("activo", 0);
                        db.update("PRODUCTO", values, "id_producto=?", new String[]{String.valueOf(id)});
                    } finally { db.close(); }
                    runOnUiThread(() -> { toast("Producto deshabilitado."); loadProducts(); });
                })).show();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    @Override protected void onResume() {
        super.onResume();
        if (adapter != null && categoryIds != null && categoryListenerInstalled) loadProducts();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(pendingProductLoad);
        worker.close();
        if (helper != null) helper.close();
        super.onDestroy();
    }
}
