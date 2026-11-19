package android.content;
public class Intent {
 private final String action; private String pkg;
 private final java.util.Map<String,Object> extras=new java.util.HashMap<String,Object>();
 public Intent(String a){action=a;}
 public String getAction(){return action;}
 public Intent setPackage(String p){pkg=p;return this;}
 public String getPackage(){return pkg;}
 public Intent putExtra(String k,int v){extras.put(k,v);return this;}
 public Intent putExtra(String k,boolean v){extras.put(k,v);return this;}
 public Intent putExtra(String k,String v){extras.put(k,v);return this;}
 public int getIntExtra(String k,int d){Object v=extras.get(k);return v instanceof Integer?(Integer)v:d;}
 public String getStringExtra(String k){return (String)extras.get(k);}
 public boolean getBooleanExtra(String k,boolean d){Object v=extras.get(k);return v instanceof Boolean?(Boolean)v:d;}
 public boolean hasExtra(String k){return extras.containsKey(k);}
}