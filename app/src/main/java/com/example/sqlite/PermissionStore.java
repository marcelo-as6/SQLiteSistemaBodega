package com.example.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public final class PermissionStore {
    private PermissionStore() {}
    public static boolean allowed(Context c,long user,String key){
        DBHelper h=new DBHelper(c);SQLiteDatabase db=h.getReadableDatabase();
        try(Cursor cur=db.rawQuery("SELECT COALESCE(UP.permitido,RP.permitido,0) FROM USUARIO U LEFT JOIN USUARIO_PERMISO UP ON UP.id_usuario=U.id_usuario AND UP.id_permiso=(SELECT id_permiso FROM PERMISO WHERE clave=?) LEFT JOIN ROL_PERMISO RP ON RP.id_rol=U.id_rol AND RP.id_permiso=(SELECT id_permiso FROM PERMISO WHERE clave=?) WHERE U.id_usuario=? AND U.activo=1",new String[]{key,key,String.valueOf(user)})){return cur.moveToFirst()&&cur.getInt(0)==1;}finally{db.close();h.close();}
    }
    public static long permissionId(SQLiteDatabase db,String key){try(Cursor c=db.rawQuery("SELECT id_permiso FROM PERMISO WHERE clave=?",new String[]{key})){return c.moveToFirst()?c.getLong(0):-1;}}
    public static void saveUserOverrides(SQLiteDatabase db,long user,boolean[] values){for(int i=0;i<PermissionCatalog.ALL.size();i++){long perm=permissionId(db,PermissionCatalog.ALL.get(i).key);ContentValues v=new ContentValues();v.put("id_usuario",user);v.put("id_permiso",perm);v.put("permitido",values[i]?1:0);db.insertWithOnConflict("USUARIO_PERMISO",null,v,SQLiteDatabase.CONFLICT_REPLACE);}}
    public static boolean[] effectiveForUser(SQLiteDatabase db,long user){boolean[] out=new boolean[PermissionCatalog.ALL.size()];for(int i=0;i<out.length;i++){String key=PermissionCatalog.ALL.get(i).key;try(Cursor c=db.rawQuery("SELECT COALESCE(UP.permitido,RP.permitido,0) FROM USUARIO U LEFT JOIN USUARIO_PERMISO UP ON UP.id_usuario=U.id_usuario AND UP.id_permiso=(SELECT id_permiso FROM PERMISO WHERE clave=?) LEFT JOIN ROL_PERMISO RP ON RP.id_rol=U.id_rol AND RP.id_permiso=(SELECT id_permiso FROM PERMISO WHERE clave=?) WHERE U.id_usuario=?",new String[]{key,key,String.valueOf(user)})){out[i]=c.moveToFirst()&&c.getInt(0)==1;}}return out;}
    public static boolean[] roleValues(SQLiteDatabase db,long role){boolean[] out=new boolean[PermissionCatalog.ALL.size()];for(int i=0;i<out.length;i++){long p=permissionId(db,PermissionCatalog.ALL.get(i).key);try(Cursor c=db.rawQuery("SELECT permitido FROM ROL_PERMISO WHERE id_rol=? AND id_permiso=?",new String[]{String.valueOf(role),String.valueOf(p)})){out[i]=c.moveToFirst()&&c.getInt(0)==1;}}return out;}
    public static void setRolePermission(SQLiteDatabase db,long role,int index,boolean value){long p=permissionId(db,PermissionCatalog.ALL.get(index).key);ContentValues v=new ContentValues();v.put("id_rol",role);v.put("id_permiso",p);v.put("permitido",value?1:0);db.insertWithOnConflict("ROL_PERMISO",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
}
