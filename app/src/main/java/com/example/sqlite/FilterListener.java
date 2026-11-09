package com.example.sqlite;

import android.view.View;
import android.widget.AdapterView;

/** Listener mínimo para filtros desplegables. */
final class FilterListener implements AdapterView.OnItemSelectedListener {
    interface Action { void run(); }
    private final Action action;
    FilterListener(Action action) { this.action = action; }
    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { action.run(); }
    @Override public void onNothingSelected(AdapterView<?> parent) { }
}
