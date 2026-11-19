package android.graphics; public class SurfaceTexture {
 public SurfaceTexture(int t){} public void release(){} public void updateTexImage(){}
 public void getTransformMatrix(float[] m){} public void setOnFrameAvailableListener(OnFrameAvailableListener l){}
 public interface OnFrameAvailableListener {void onFrameAvailable(SurfaceTexture s);}
}