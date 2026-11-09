package com.example.sqlite;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Guarda la ubicación del local en SQLite y la consulta en OpenStreetMap sin API key. */
public class LocationActivity extends AppCompatActivity {
    public static final String EXTRA_PUBLIC_VIEW = "com.example.sqlite.PUBLIC_LOCATION_VIEW";
    private DBHelper helper;
    private EditText name, address, latitude, longitude;
    private TextView updated;
    private boolean canEdit;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SessionStore session = new SessionStore(this);
        boolean publicView = getIntent().getBooleanExtra(EXTRA_PUBLIC_VIEW, false);
        canEdit = session.active() && PermissionStore.allowed(this, session.id(), "LOCATION_MANAGE");
        if ((!session.active() && !publicView) || (session.active() && !canEdit && !PermissionStore.allowed(this, session.id(), "LOCATION_VIEW"))) { finish(); return; }
        helper = new DBHelper(this);
        setContentView(R.layout.activity_location);
        name = findViewById(R.id.etLocationName);
        address = findViewById(R.id.etLocationAddress);
        latitude = findViewById(R.id.etLocationLatitude);
        longitude = findViewById(R.id.etLocationLongitude);
        updated = findViewById(R.id.tvLocationUpdated);
        MaterialButton save = findViewById(R.id.btnSaveLocation);
        save.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        name.setEnabled(canEdit); address.setEnabled(canEdit); latitude.setEnabled(canEdit); longitude.setEnabled(canEdit);
        findViewById(R.id.btnLocationBack).setOnClickListener(v -> finish());
        save.setOnClickListener(v -> saveLocation());
        findViewById(R.id.btnOpenMap).setOnClickListener(v -> openMap());
        loadLocation();
    }

    private void loadLocation() {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT nombre,direccion,latitud,longitud,actualizado_en FROM UBICACION_LOCAL WHERE id_ubicacion=1", null)) {
            if (c.moveToFirst()) {
                name.setText(c.getString(0)); address.setText(c.getString(1));
                latitude.setText(c.isNull(2) ? "" : String.valueOf(c.getDouble(2)));
                longitude.setText(c.isNull(3) ? "" : String.valueOf(c.getDouble(3)));
                updated.setText("Última actualización: " + c.getString(4));
            }
        } finally { db.close(); }
    }

    private void saveLocation() {
        String title = name.getText().toString().trim();
        String street = address.getText().toString().trim();
        String lat = latitude.getText().toString().trim().replace(',', '.');
        String lon = longitude.getText().toString().trim().replace(',', '.');
        if (title.isEmpty() || (lat.isEmpty() != lon.isEmpty())) { toast("Ingresa el nombre y completa ambas coordenadas, o déjalas vacías."); return; }
        Double latValue = null, lonValue = null;
        if (!lat.isEmpty()) {
            try { latValue = Double.parseDouble(lat); lonValue = Double.parseDouble(lon); }
            catch (NumberFormatException e) { toast("Las coordenadas deben ser números decimales."); return; }
            if (latValue < -90 || latValue > 90 || lonValue < -180 || lonValue > 180) { toast("Latitud: −90 a 90. Longitud: −180 a 180."); return; }
        }
        ContentValues values = new ContentValues(); values.put("nombre", title); values.put("direccion", street);
        if (latValue == null) { values.putNull("latitud"); values.putNull("longitud"); }
        else { values.put("latitud", latValue); values.put("longitud", lonValue); }
        values.put("actualizado_en", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        SQLiteDatabase db = helper.getWritableDatabase();
        try { db.update("UBICACION_LOCAL", values, "id_ubicacion=1", null); }
        finally { db.close(); }
        toast("Ubicación guardada en SQLite."); loadLocation();
    }

    private void openMap() {
        String lat = latitude.getText().toString().trim().replace(',', '.');
        String lon = longitude.getText().toString().trim().replace(',', '.');
        if (lat.isEmpty() && lon.isEmpty()) {
            String query = address.getText().toString().trim();
            if (query.isEmpty()) { toast("Ingresa una dirección o las coordenadas para abrir el mapa."); return; }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/search?query=" + Uri.encode(query))));
            return;
        }
        try {
            double la = Double.parseDouble(lat), lo = Double.parseDouble(lon);
            if (la < -90 || la > 90 || lo < -180 || lo > 180) throw new NumberFormatException();
            String url = String.format(Locale.US, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f", la, lo, la, lo);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) { toast("Guarda primero una latitud y longitud válidas."); }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { if (helper != null) helper.close(); super.onDestroy(); }
}
