package android.content;
public abstract class Context {
 public static final int MODE_PRIVATE=0;
 public Context getApplicationContext(){return this;}
 public abstract void sendBroadcast(Intent i);
 public Intent registerReceiver(BroadcastReceiver r, IntentFilter f){return null;}
 public void unregisterReceiver(BroadcastReceiver r){}
 public SharedPreferences getSharedPreferences(String name,int mode){throw new UnsupportedOperationException();}
}