package com.example.sqlite;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
public class DBHelper extends SQLiteOpenHelper {
 private static final String DB_NAME="BD_BODEGA.db"; private static final int DB_VERSION=9;
 public DBHelper(Context c){super(c,DB_NAME,null,DB_VERSION);}
 @Override public void onConfigure(SQLiteDatabase db){super.onConfigure(db);db.setForeignKeyConstraintsEnabled(true);}
 @Override public void onCreate(SQLiteDatabase db){schema(db);seed(db);permissionSchema(db);seedPermissions(db);locationSchema(db);seedDemoInventory(db);}
 private void schema(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS ROL (id_rol INTEGER PRIMARY KEY AUTOINCREMENT,nombre_rol TEXT NOT NULL UNIQUE,descripcion TEXT NOT NULL DEFAULT '',activo INTEGER NOT NULL DEFAULT 1)");
  db.execSQL("CREATE TABLE IF NOT EXISTS USUARIO (id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT NOT NULL UNIQUE,password_hash TEXT NOT NULL,nombre_completo TEXT NOT NULL,id_rol INTEGER NOT NULL,activo INTEGER NOT NULL DEFAULT 1,FOREIGN KEY(id_rol) REFERENCES ROL(id_rol))");
  db.execSQL("CREATE TABLE IF NOT EXISTS CATEGORIA (id_categoria INTEGER PRIMARY KEY AUTOINCREMENT,nombre_categoria TEXT NOT NULL UNIQUE,descripcion TEXT NOT NULL DEFAULT '',activo INTEGER NOT NULL DEFAULT 1)");
  db.execSQL("CREATE TABLE IF NOT EXISTS PROVEEDOR (id_proveedor INTEGER PRIMARY KEY AUTOINCREMENT,rut_empresa TEXT NOT NULL UNIQUE,razon_social TEXT NOT NULL,contacto TEXT NOT NULL,activo INTEGER NOT NULL DEFAULT 1)");
  db.execSQL("CREATE TABLE IF NOT EXISTS PRODUCTO (id_producto INTEGER PRIMARY KEY AUTOINCREMENT,sku_codigo TEXT NOT NULL UNIQUE,codigo_barras TEXT NOT NULL DEFAULT '',nombre TEXT NOT NULL,descripcion TEXT NOT NULL DEFAULT '',stock_actual INTEGER NOT NULL DEFAULT 0 CHECK(stock_actual>=0),stock_minimo INTEGER NOT NULL DEFAULT 0 CHECK(stock_minimo>=0),unidad_medida TEXT NOT NULL DEFAULT 'unidad',id_categoria INTEGER NOT NULL,activo INTEGER NOT NULL DEFAULT 1,FOREIGN KEY(id_categoria) REFERENCES CATEGORIA(id_categoria))");
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_producto_codigo_barras ON PRODUCTO(codigo_barras) WHERE codigo_barras <> ''");
  db.execSQL("CREATE TABLE IF NOT EXISTS MOVIMIENTO (id_movimiento INTEGER PRIMARY KEY AUTOINCREMENT,tipo TEXT NOT NULL CHECK(tipo IN ('ENTRADA','SALIDA')),cantidad INTEGER NOT NULL CHECK(cantidad>0),fecha TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,id_producto INTEGER NOT NULL,id_usuario INTEGER,id_proveedor INTEGER,nota TEXT NOT NULL DEFAULT '',FOREIGN KEY(id_producto) REFERENCES PRODUCTO(id_producto),FOREIGN KEY(id_usuario) REFERENCES USUARIO(id_usuario),FOREIGN KEY(id_proveedor) REFERENCES PROVEEDOR(id_proveedor))");
 }
 private void seed(SQLiteDatabase db){
  db.execSQL("INSERT OR IGNORE INTO ROL(nombre_rol,descripcion) VALUES('Administrador','Acceso completo'),('Operador','Operación de bodega')");
  long role=scalar(db,"SELECT id_rol FROM ROL WHERE nombre_rol='Administrador'");
  ContentValues u=new ContentValues();u.put("username","admin");u.put("password_hash",hash("Pure123"));u.put("nombre_completo","Administrador");u.put("id_rol",role);
  db.insertWithOnConflict("USUARIO",null,u,SQLiteDatabase.CONFLICT_IGNORE);
  // Rotate only the original demo credential; keep any password an administrator already changed.
  try(Cursor c=db.rawQuery("SELECT id_usuario,password_hash FROM USUARIO WHERE username='admin'",null)){
   if(c.moveToFirst()&&verify("admin123",c.getString(1))){
    ContentValues rotated=new ContentValues();rotated.put("password_hash",hash("Pure123"));
    db.update("USUARIO",rotated,"id_usuario=?",new String[]{String.valueOf(c.getLong(0))});
   }
  }
 }
 /** Adds a small, clearly labeled demo catalog only to a database with no products. */
 private void seedDemoInventory(SQLiteDatabase db){
  if(scalar(db,"SELECT COUNT(*) FROM PRODUCTO")>0)return;
  db.beginTransaction();
  try{
   db.execSQL("INSERT OR IGNORE INTO CATEGORIA(nombre_categoria,descripcion) VALUES('Librería','Útiles y materiales de oficina'),('Aseo','Insumos de limpieza'),('Tecnología','Accesorios y consumibles')");
   db.execSQL("INSERT OR IGNORE INTO PROVEEDOR(rut_empresa,razon_social,contacto) VALUES('76.000.000-0','Proveedor de demostración','Contacto de prueba')");
   long admin=scalar(db,"SELECT id_usuario FROM USUARIO WHERE username='admin'");
   long supplier=scalar(db,"SELECT id_proveedor FROM PROVEEDOR WHERE rut_empresa='76.000.000-0'");
   String[][] items={
    {"LIB-001","Cuaderno universitario","Cuaderno cuadriculado de 100 hojas","24","5","unidad","Librería","12"},
    {"LIB-002","Lápiz grafito HB","Lápiz de grafito para uso general","40","10","unidad","Librería","20"},
    {"LIB-003","Resma de papel carta","Papel blanco tamaño carta, 500 hojas","8","3","paquete","Librería","5"},
    {"ASE-001","Detergente líquido","Envase de 1 litro","6","2","litro","Aseo","3"},
    {"TEC-001","Tóner impresora","Consumible de impresión de demostración","2","1","unidad","Tecnología","1"}
   };
   for(String[] item:items){
    long category=scalar(db,"SELECT id_categoria FROM CATEGORIA WHERE nombre_categoria='"+item[6]+"'");
    ContentValues product=new ContentValues();product.put("sku_codigo",item[0]);product.put("codigo_barras","");product.put("nombre",item[1]);product.put("descripcion",item[2]);product.put("stock_actual",0);product.put("stock_minimo",Integer.parseInt(item[4]));product.put("unidad_medida",item[5]);product.put("id_categoria",category);
    long productId=db.insertOrThrow("PRODUCTO",null,product);
    ContentValues movement=new ContentValues();movement.put("tipo","ENTRADA");movement.put("cantidad",Integer.parseInt(item[7]));movement.put("id_producto",productId);movement.put("id_usuario",admin);movement.put("id_proveedor",supplier);movement.put("nota","Carga inicial de datos de demostración");db.insertOrThrow("MOVIMIENTO",null,movement);
    db.execSQL("UPDATE PRODUCTO SET stock_actual=? WHERE id_producto=?",new Object[]{Integer.parseInt(item[7]),productId});
   }
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
 private void permissionSchema(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS PERMISO(id_permiso INTEGER PRIMARY KEY AUTOINCREMENT,clave TEXT NOT NULL UNIQUE,nombre TEXT NOT NULL,grupo TEXT NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS ROL_PERMISO(id_rol INTEGER NOT NULL,id_permiso INTEGER NOT NULL,permitido INTEGER NOT NULL DEFAULT 0 CHECK(permitido IN (0,1)),PRIMARY KEY(id_rol,id_permiso),FOREIGN KEY(id_rol) REFERENCES ROL(id_rol) ON DELETE CASCADE,FOREIGN KEY(id_permiso) REFERENCES PERMISO(id_permiso) ON DELETE CASCADE)");
  db.execSQL("CREATE TABLE IF NOT EXISTS USUARIO_PERMISO(id_usuario INTEGER NOT NULL,id_permiso INTEGER NOT NULL,permitido INTEGER NOT NULL CHECK(permitido IN (0,1)),PRIMARY KEY(id_usuario,id_permiso),FOREIGN KEY(id_usuario) REFERENCES USUARIO(id_usuario) ON DELETE CASCADE,FOREIGN KEY(id_permiso) REFERENCES PERMISO(id_permiso) ON DELETE CASCADE)");
 }
 private void seedPermissions(SQLiteDatabase db){
  String[][] p={{"PRODUCT_VIEW","Ver productos","Inventario"},{"PRODUCT_CREATE","Crear productos","Inventario"},{"PRODUCT_EDIT","Editar productos","Inventario"},{"PRODUCT_DISABLE","Desactivar productos","Inventario"},{"MOVEMENT_VIEW","Ver movimientos","Movimientos"},{"MOVEMENT_ENTRY","Registrar entradas","Movimientos"},{"MOVEMENT_EXIT","Registrar salidas","Movimientos"},{"REPORT_VIEW","Ver reportes de stock","Reportes"},{"HISTORY_VIEW","Ver historial","Reportes"},{"CATEGORY_VIEW","Ver categorías","Datos maestros"},{"USER_MANAGE","Gestionar usuarios","Administración"},{"PROFILE_MANAGE","Gestionar perfiles y permisos","Administración"},{"MASTER_DATA_MANAGE","Gestionar categorías y proveedores","Administración"},{"LOCATION_VIEW","Ver ubicación del local","Local"},{"LOCATION_MANAGE","Editar ubicación del local","Local"}};
  for(String[] row:p){ContentValues v=new ContentValues();v.put("clave",row[0]);v.put("nombre",row[1]);v.put("grupo",row[2]);db.insertWithOnConflict("PERMISO",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
  try(Cursor roles=db.rawQuery("SELECT id_rol,nombre_rol FROM ROL",null)){while(roles.moveToNext()){long id=roles.getLong(0);boolean admin="Administrador".equalsIgnoreCase(roles.getString(1));for(String[] row:p){boolean allow=admin||("Operador".equalsIgnoreCase(roles.getString(1))&&java.util.Arrays.asList("PRODUCT_VIEW","MOVEMENT_VIEW","MOVEMENT_ENTRY","MOVEMENT_EXIT","REPORT_VIEW","HISTORY_VIEW","CATEGORY_VIEW","LOCATION_VIEW").contains(row[0]));long perm=scalar(db,"SELECT id_permiso FROM PERMISO WHERE clave='"+row[0]+"'");db.execSQL("INSERT OR IGNORE INTO ROL_PERMISO(id_rol,id_permiso,permitido) VALUES(?,?,?)",new Object[]{id,perm,allow?1:0});}}}
 }
 private void locationSchema(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS UBICACION_LOCAL(id_ubicacion INTEGER PRIMARY KEY CHECK(id_ubicacion=1),nombre TEXT NOT NULL DEFAULT 'Mi local',direccion TEXT NOT NULL DEFAULT '',latitud REAL,longitud REAL,actualizado_en TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,CHECK((latitud IS NULL AND longitud IS NULL) OR (latitud BETWEEN -90 AND 90 AND longitud BETWEEN -180 AND 180)))");ContentValues v=new ContentValues();v.put("id_ubicacion",1);v.put("nombre","Instituto Profesional Santo Tomás");v.put("direccion","Benjamín Vicuña Mackenna 520, 1840103 Ovalle, Coquimbo");v.put("latitud",-30.6045891);v.put("longitud",-71.2047616);db.insertWithOnConflict("UBICACION_LOCAL",null,v,SQLiteDatabase.CONFLICT_IGNORE);db.execSQL("UPDATE UBICACION_LOCAL SET nombre=?,direccion=?,latitud=?,longitud=? WHERE id_ubicacion=1 AND nombre='Mi local' AND direccion='' AND latitud IS NULL AND longitud IS NULL",new Object[]{"Instituto Profesional Santo Tomás","Benjamín Vicuña Mackenna 520, 1840103 Ovalle, Coquimbo",-30.6045891,-71.2047616});}
 @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
  if(oldVersion<3&&!hasColumn(db,"PRODUCTO","unidad_medida"))
   if(tableExists(db,"PRODUCTO"))db.execSQL("ALTER TABLE PRODUCTO ADD COLUMN unidad_medida TEXT NOT NULL DEFAULT 'unidad'");
  if(oldVersion<4&&!hasColumn(db,"PRODUCTO","codigo_barras"))
   if(tableExists(db,"PRODUCTO"))db.execSQL("ALTER TABLE PRODUCTO ADD COLUMN codigo_barras TEXT NOT NULL DEFAULT ''");
  schema(db);seed(db);permissionSchema(db);seedPermissions(db);locationSchema(db);seedDemoInventory(db);
 }
 private boolean tableExists(SQLiteDatabase db,String table){
  try(Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{table})){
   return c.moveToFirst();
  }
 }
 private boolean hasColumn(SQLiteDatabase db,String table,String column){
  try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)){
   while(c.moveToNext())if(column.equalsIgnoreCase(c.getString(c.getColumnIndexOrThrow("name"))))return true;
  }
  return false;
 }
 public static long scalar(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):-1;}}
 public static String hash(String value){try{byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);byte[] key=derive(value,salt);return Base64.encodeToString(salt,Base64.NO_WRAP)+":"+Base64.encodeToString(key,Base64.NO_WRAP);}catch(Exception e){throw new IllegalStateException(e);}}
 public static boolean verify(String value,String stored){try{String[] p=stored.split(":");return p.length==2&&MessageDigest.isEqual(Base64.decode(p[1],Base64.NO_WRAP),derive(value,Base64.decode(p[0],Base64.NO_WRAP)));}catch(Exception e){return false;}}
 private static byte[] derive(String value,byte[] salt)throws Exception{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(value.toCharArray(),salt,120000,256)).getEncoded();}
}

