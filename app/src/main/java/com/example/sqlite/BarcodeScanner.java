package com.example.sqlite;

import android.app.Activity;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;

/** Lector de códigos lineales usado para identificar productos por su código de barras. */
public final class BarcodeScanner {
    public interface Listener {
        void onScanned(String value);
        void onError();
    }

    private BarcodeScanner() { }

    public static void scan(Activity activity, Listener listener) {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_CODABAR,
                        Barcode.FORMAT_ITF)
                .enableAutoZoom()
                .build();

        try {
            GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(activity, options);
            scanner.startScan()
                    .addOnSuccessListener(barcode -> {
                        String value = barcode.getRawValue();
                        if (value != null && !value.trim().isEmpty()) listener.onScanned(value.trim());
                        else listener.onError();
                    })
                    .addOnFailureListener(error -> listener.onError());
        } catch (RuntimeException e) {
            listener.onError();
        }
    }
}
