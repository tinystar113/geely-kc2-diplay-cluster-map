package com.shilapi.xcertplay.hud;
public class BydHudRouteState {
 public boolean routeActive; public long remainingMeters=-1,remainingSeconds=-1,distanceMeters=-1;
 public String currentRoad=""; public int activeIndex=-1; public Long emptyListSinceNs;
 public java.util.Map<Integer,Object> maneuvers=new java.util.HashMap<Integer,Object>();
 private Object maneuver;
 public Object accept(int message,byte[] payload){
  int mode=payload[0];
  if(mode==3){clear();return "CLEAR";}
  routeActive=mode!=2 && mode!=5;
  maneuver=(mode==2 || mode==4 || mode==6)?new Maneuver(mode==6):null;
  if(mode==6){
   currentRoad="Test Current Road"; activeIndex=0;
   remainingMeters=3300; remainingSeconds=660; distanceMeters=114;
   maneuvers.put(0,maneuver);
  }
  return "UPDATE";
 }
 public Object currentApple(){return maneuver;}
 public void clear(){
  routeActive=false; maneuver=null; currentRoad=""; activeIndex=-1;
  remainingMeters=-1; remainingSeconds=-1; distanceMeters=-1; maneuvers.clear();
 }
 public static class Maneuver {
  private final boolean full;
  public Maneuver(boolean full){this.full=full;}
  public int getType(){return full?2:3;}
  public String getAfterRoad(){return full?"Test Next Road":"";}
  public long getDistanceMeters(){return full?114:-1;}
  public long getRemainingMeters(){return full?3300:-1;}
  public long getRemainingSeconds(){return full?660:-1;}
 }
}