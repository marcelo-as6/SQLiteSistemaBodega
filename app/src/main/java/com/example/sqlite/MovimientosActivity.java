package com.example.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

/** Registro transaccional de entradas y salidas de productos. */
public class MovimientosActivity extends AppCompatActivity {
    private final DatabaseExecutor worker = new DatabaseExecutor();
    private DBHelper helper;
    private EditText sku;
    private EditText quantity;
    private EditText note;
    private Spinner provider;
    private TextView stockInfo;
    private ArrayList<Long> providerIds;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SessionStore userSession=new SessionStore(this);if(!userSession.active()||!(PermissionStore.allowed(this,userSession.id(),"MOVEMENT_VIEW")||PermissionStore.allowed(this,userSession.id(),"MOVEMENT_ENTRY")||PermissionStore.allowed(this,userSession.id(),"MOVEMENT_EXIT"))) { finish(); return; }
        helper = new DBHelper(this);
        setContentView(R.layout.activity_movements);
        sku = findViewById(R.id.etSku);
        quantity = findViewById(R.id.etQuantity);
        note = findViewById(R.id.etMovementNote);
        provider = findViewById(R.id.spinnerProvider);
        stockInfo = findViewById(R.id.tvStockInfo);

        loadProviders();
        findViewById(R.id.btnCheckStock).setOnClickListener(v -> consultStock());
        findViewById(R.id.btnScanMovementBarcode).setOnClickListener(v -> scanProduct());
        findViewById(R.id.btnEntry).setOnClickListener(v -> record("ENTRADA"));
        findViewById(R.id.btnExit).setOnClickListener(v -> record("SALIDA"));
        findViewById(R.id.btnEntry).setVisibility(PermissionStore.allowed(this,userSession.id(),"MOVEMENT_ENTRY")?android.view.View.VISIBLE:android.view.View.GONE);
        findViewById(R.id.btnExit).setVisibility(PermissionStore.allowed(this,userSession.id(),"MOVEMENT_EXIT")?android.view.View.VISIBLE:android.view.View.GONE);

        String selectedSku = getIntent().getStringExtra(Leer.EXTRA_PRODUCT_SKU);
        if (selectedSku != null && !selectedSku.trim().isEmpty()) {
            sku.setText(selectedSku);
            consultStock();
        }
    }

    private void loadProviders() {
        providerIds = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        providerIds.add(-1L);
        labels.add("Sin proveedor");
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT id_proveedor,razon_social FROM PROVEEDOR " +
                        "WHERE activo=1 ORDER BY razon_social COLLATE NOCASE", null)) {
            while (c.moveToNext()) {
                providerIds.add(c.getLong(0));
                labels.add(c.getString(1));
            }
        } finally { db.close(); }
        provider.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
    }

    private void scanProduct() {
        BarcodeScanner.scan(this, new BarcodeScanner.Listener() {
            @Override public void onScanned(String value) {
                sku.setText(value);
                consultStock();
            }
            @Override public void onError() {
                toast("No se pudo leer el código. Ingresa el SKU manualmente.");
            }
        });
    }

    // RF-17: consulta el stock actual y disponible por SKU o código de barras.
    private void consultStock() {
        String code = sku.getText().toString().trim();
        if (code.isEmpty()) {
            toast("Ingresa el SKU o escanea el código de barras.");
            return;
        }
        worker.execute(() -> {
            String message;
            SQLiteDatabase db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT nombre,sku_codigo,stock_actual,unidad_medida FROM PRODUCTO " +
                            "WHERE (sku_codigo=? OR codigo_barras=?) AND activo=1 LIMIT 1",
                    new String[]{code, code})) {
                if (c.moveToFirst()) {
                    int current = c.getInt(2);
                    message = "Producto: " + c.getString(0) + " · SKU " + c.getString(1) +
                            "\nStock actual: " + current + " " + c.getString(3) +
                            "\nStock disponible: " + current + " " + c.getString(3);
                } else message = "No se encontró un producto activo con ese código.";
            } finally { db.close(); }
            String result = message;
            runOnUiThread(() -> stockInfo.setText(result));
        });
    }

    // RF-15, RF-16 y RF-18: el movimiento y el cambio de stock se guardan en una transacción.
    private void record(String type) {
        SessionStore accessSession=new SessionStore(this);if(!PermissionStore.allowed(this,accessSession.id(),type.equals("ENTRADA")?"MOVEMENT_ENTRY":"MOVEMENT_EXIT")){toast("No tienes permiso para registrar este movimiento.");return;}
        String code = sku.getText().toString().trim();
        if (code.isEmpty()) {
            toast("Ingresa el SKU o escanea el código de barras.");
            return;
        }

        final int count;
        try {
            String raw = quantity.getText().toString().trim();
            if (raw.isEmpty()) throw new NumberFormatException();
            count = Integer.parseInt(raw);
            if (count <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            toast("La cantidad debe ser un número entero mayor que cero.");
            return;
        }

        String movementNote = note.getText().toString().trim();
        if (type.equals("SALIDA") && movementNote.isEmpty()) {
            toast("Indica el motivo de la salida.");
            return;
        }
        int selectedProvider = provider.getSelectedItemPosition();
        long supplierId = providerIds != null && selectedProvider >= 0 && selectedProvider < providerIds.size()
                ? providerIds.get(selectedProvider) : -1L;
        long userId = new SessionStore(this).id();

        findViewById(R.id.btnEntry).setEnabled(false);
        findViewById(R.id.btnExit).setEnabled(false);
        worker.execute(() -> {
            String error = null;
            int newStock = -1;
            String productName = null;
            String unit = null;
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                long productId;
                int currentStock;
                try (Cursor c = db.rawQuery(
                        "SELECT id_producto,nombre,stock_actual,unidad_medida FROM PRODUCTO " +
                                "WHERE (sku_codigo=? OR codigo_barras=?) AND activo=1 LIMIT 1",
                        new String[]{code, code})) {
                    if (!c.moveToFirst()) throw new IllegalStateException("No existe un producto activo con ese código.");
                    productId = c.getLong(0);
                    productName = c.getString(1);
                    currentStock = c.getInt(2);
                    unit = c.getString(3);
                }

                if (type.equals("SALIDA") && count > currentStock)
                    throw new IllegalStateException("Stock insuficiente. Disponible: " + currentStock);

                String condition = type.equals("SALIDA")
                        ? "id_producto=? AND stock_actual>=?" : "id_producto=?";
                android.database.sqlite.SQLiteStatement update = db.compileStatement(
                        "UPDATE PRODUCTO SET stock_actual=stock_actual " +
                                (type.equals("ENTRADA") ? "+" : "-") +
                                " ? WHERE " + condition);
                update.bindLong(1, count);
                update.bindLong(2, productId);
                if (type.equals("SALIDA")) update.bindLong(3, count);
                int changed = update.executeUpdateDelete();
                update.close();
                if (changed != 1) throw new IllegalStateException(
                        "No se pudo actualizar el stock. Verifica la cantidad disponible.");

                try (Cursor c = db.rawQuery("SELECT stock_actual FROM PRODUCTO WHERE id_producto=?",
                        new String[]{String.valueOf(productId)})) {
                    if (c.moveToFirst()) newStock = c.getInt(0);
                }

                ContentValues movement = new ContentValues();
                movement.put("tipo", type);
                movement.put("cantidad", count);
                movement.put("id_producto", productId);
                if (userId > 0) movement.put("id_usuario", userId);
                if (type.equals("ENTRADA") && supplierId > 0) movement.put("id_proveedor", supplierId);
                movement.put("nota", movementNote);
                db.insertOrThrow("MOVIMIENTO", null, movement);
                db.setTransactionSuccessful();
            } catch (IllegalStateException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = "No se pudo guardar el movimiento. El stock quedó sin cambios.";
            } finally {
                db.endTransaction();
                db.close();
            }

            final String resultError = error;
            final int resultStock = newStock;
            final String resultProduct = productName;
            final String resultUnit = unit;
            runOnUiThread(() -> {
                findViewById(R.id.btnEntry).setEnabled(true);
                findViewById(R.id.btnExit).setEnabled(true);
                if (resultError == null) {
                    quantity.setText("");
                    note.setText("");
                    stockInfo.setText("Producto: " + resultProduct +
                            "\nMovimiento: " + type +
                            "\nStock actual: " + resultStock + " " + resultUnit +
                            "\nStock disponible: " + resultStock + " " + resultUnit);
                    toast(type + " registrada correctamente. Stock nuevo: " + resultStock);
                } else toast(resultError);
            });
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        worker.close();
        if (helper != null) helper.close();
        super.onDestroy();
    }
}
