package android.util; public class Log {
 public static final java.util.List<String> messages=new java.util.ArrayList<String>();
 public static int i(String t,String m){messages.add(t+": "+m);return 0;}
 public static int w(String t,String m){return i(t,m);}
 public static int w(String t,String m,Throwable e){return i(t,m+" "+e);}
 public static int e(String t,String m){return i(t,m);}
 public static int e(String t,String m,Throwable e){return i(t,m+" "+e);}
}