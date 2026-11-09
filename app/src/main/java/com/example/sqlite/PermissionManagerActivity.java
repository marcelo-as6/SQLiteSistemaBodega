package com.example.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class PermissionManagerActivity extends AppCompatActivity {
    DBHelper helper; SessionStore session;
    @Override protected void onCreate(Bundle b){super.onCreate(b);session=new SessionStore(this);if(!session.active()||!PermissionStore.allowed(this,session.id(),"PROFILE_MANAGE")){finish();return;}helper=new DBHelper(this);bindLayout();}
    private void bindLayout() {
        setContentView(R.layout.activity_permission_manager);
        findViewById(R.id.btnEditProfiles).setOnClickListener(v -> bulkRoles());
        findViewById(R.id.btnEditUserPermissions).setOnClickListener(v -> editUser());
        findViewById(R.id.btnBackToAdmin).setOnClickListener(v -> finish());
    }
    private static class Row{long id;String name;Row(long i,String n){id=i;name=n;}}
    private ArrayList<Row> rows(String sql){ArrayList<Row> out=new ArrayList<>();SQLiteDatabase db=helper.getReadableDatabase();try(Cursor c=db.rawQuery(sql,null)){while(c.moveToNext())out.add(new Row(c.getLong(0),c.getString(1)));}finally{db.close();}return out;}
    private void bulkRoles(){ArrayList<Row> all=rows("SELECT id_rol,nombre_rol FROM ROL WHERE activo=1 ORDER BY nombre_rol");if(all.isEmpty())return;String[] labels=new String[all.size()];boolean[] selected=new boolean[all.size()];for(int i=0;i<all.size();i++)labels[i]=all.get(i).name;new AlertDialog.Builder(this).setTitle("Selecciona perfiles").setMultiChoiceItems(labels,selected,(d,w,c)->selected[w]=c).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->{ArrayList<Long> chosen=new ArrayList<>();for(int i=0;i<selected.length;i++)if(selected[i])chosen.add(all.get(i).id);if(chosen.isEmpty()){toast("Selecciona al menos un perfil.");return;}bulkPermissions(chosen);}).show();}
    private void bulkPermissions(ArrayList<Long> roles){String[] labels=new String[PermissionCatalog.ALL.size()];boolean[] checked=new boolean[labels.length];for(int i=0;i<labels.length;i++)labels[i]=PermissionCatalog.ALL.get(i).group+" · "+PermissionCatalog.ALL.get(i).name;new AlertDialog.Builder(this).setTitle("Permisos a modificar").setMultiChoiceItems(labels,checked,(d,w,c)->checked[w]=c).setNegativeButton("Cancelar",null).setNeutralButton("Quitar seleccionados",(d,w)->applyRoles(roles,checked,false)).setPositiveButton("Conceder seleccionados",(d,w)->applyRoles(roles,checked,true)).show();}
    private void applyRoles(ArrayList<Long> roles,boolean[] choices,boolean allow){if(session.role().equalsIgnoreCase("Administrador")&&roles.contains(roleIdForUser(session.id()))&&!allow){int profile=keyIndex("PROFILE_MANAGE");if(profile>=0&&choices[profile]){toast("No puedes quitarte a ti mismo la gestión de permisos.");return;}}SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{for(long role:roles)for(int i=0;i<choices.length;i++)if(choices[i])PermissionStore.setRolePermission(db,role,i,allow);db.setTransactionSuccessful();}finally{db.endTransaction();db.close();}toast("Perfiles actualizados.");}
    private void editUser(){ArrayList<Row> all=rows("SELECT id_usuario,nombre_completo||' · @'||username FROM USUARIO WHERE activo=1 ORDER BY nombre_completo");if(all.isEmpty())return;String[] labels=new String[all.size()];for(int i=0;i<labels.length;i++)labels[i]=all.get(i).name;new AlertDialog.Builder(this).setTitle("Selecciona usuario").setItems(labels,(d,w)->showUserPerms(all.get(w))).setNegativeButton("Cancelar",null).show();}
    private void showUserPerms(Row user){SQLiteDatabase db=helper.getReadableDatabase();boolean[] values;try{values=PermissionStore.effectiveForUser(db,user.id);}finally{db.close();}String[] labels=new String[values.length];for(int i=0;i<labels.length;i++)labels[i]=PermissionCatalog.ALL.get(i).group+" · "+PermissionCatalog.ALL.get(i).name;LinearLayout box=new LinearLayout(this);box.setOrientation(1);TextView help=new TextView(this);help.setText("Los cambios se guardan como permisos individuales y prevalecen sobre el perfil.");help.setPadding(28,18,24,8);box.addView(help);CheckBox[] checks=new CheckBox[values.length];for(int i=0;i<values.length;i++){checks[i]=new CheckBox(this);checks[i].setText(labels[i]);checks[i].setChecked(values[i]);checks[i].setPadding(20,2,20,2);box.addView(checks[i]);}ScrollView scroll=new ScrollView(this);scroll.addView(box);new AlertDialog.Builder(this).setTitle(user.name).setView(scroll).setNegativeButton("Cancelar",null).setNeutralButton("Volver al perfil",(d,w)->{SQLiteDatabase x=helper.getWritableDatabase();x.delete("USUARIO_PERMISO","id_usuario=?",new String[]{String.valueOf(user.id)});x.close();toast("El usuario vuelve a heredar el perfil.");}).setPositiveButton("Guardar ajustes",(d,w)->{if(user.id==session.id()&&!checks[keyIndex("PROFILE_MANAGE")].isChecked()){toast("No puedes quitarte a ti mismo la gestión de permisos.");return;}boolean[] v=new boolean[checks.length];for(int i=0;i<v.length;i++)v[i]=checks[i].isChecked();SQLiteDatabase x=helper.getWritableDatabase();x.beginTransaction();try{PermissionStore.saveUserOverrides(x,user.id,v);x.setTransactionSuccessful();}finally{x.endTransaction();x.close();}toast("Permisos individuales guardados.");}).show();}
    private int keyIndex(String key){for(int i=0;i<PermissionCatalog.ALL.size();i++)if(PermissionCatalog.ALL.get(i).key.equals(key))return i;return -1;}
    private long roleIdForUser(long id){SQLiteDatabase d=helper.getReadableDatabase();try(Cursor c=d.rawQuery("SELECT id_rol FROM USUARIO WHERE id_usuario=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getLong(0):-1;}finally{d.close();}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){if(helper!=null)helper.close();super.onDestroy();}
}



