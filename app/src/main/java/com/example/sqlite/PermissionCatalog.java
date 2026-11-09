package com.example.sqlite;

import java.util.Arrays;
import java.util.List;

public final class PermissionCatalog {
    public static final class Item {
        public final String key, name, group;
        Item(String key, String name, String group) { this.key=key; this.name=name; this.group=group; }
    }
    public static final List<Item> ALL = Arrays.asList(
        new Item("PRODUCT_VIEW","Ver productos","Inventario"), new Item("PRODUCT_CREATE","Crear productos","Inventario"),
        new Item("PRODUCT_EDIT","Editar productos","Inventario"), new Item("PRODUCT_DISABLE","Desactivar productos","Inventario"),
        new Item("MOVEMENT_VIEW","Ver movimientos","Movimientos"), new Item("MOVEMENT_ENTRY","Registrar entradas","Movimientos"),
        new Item("MOVEMENT_EXIT","Registrar salidas","Movimientos"), new Item("REPORT_VIEW","Ver reportes de stock","Reportes"),
        new Item("HISTORY_VIEW","Ver historial","Reportes"), new Item("CATEGORY_VIEW","Ver categorías","Datos maestros"),
        new Item("USER_MANAGE","Gestionar usuarios","Administración"), new Item("PROFILE_MANAGE","Gestionar perfiles y permisos","Administración"),
        new Item("MASTER_DATA_MANAGE","Gestionar categorías y proveedores","Administración"),
        new Item("LOCATION_VIEW","Ver ubicación del local","Local"),
        new Item("LOCATION_MANAGE","Editar ubicación del local","Local")
    );
    private PermissionCatalog() {}
}
