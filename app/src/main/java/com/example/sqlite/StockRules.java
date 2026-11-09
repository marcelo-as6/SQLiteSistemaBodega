package com.example.sqlite;

/** Reglas comunes para clasificar existencias en informes. */
public final class StockRules {
    public static final int OUT_OF_STOCK = 0;
    public static final int CRITICAL = 1;
    public static final int NORMAL = 2;

    private StockRules() { }

    public static int status(int stock, int minimum) {
        if (stock <= 0) return OUT_OF_STOCK;
        return stock <= minimum ? CRITICAL : NORMAL;
    }

    public static String label(int status) {
        if (status == OUT_OF_STOCK) return "Sin stock";
        if (status == CRITICAL) return "Stock crítico";
        return "Stock normal";
    }
}
