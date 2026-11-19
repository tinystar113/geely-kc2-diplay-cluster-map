package com.diplay.spike;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.widget.TextView;

import com.shilapi.xcertplay.media.GeelyClusterMap;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Task 3 spike (not shipped): decode an H.264 sample with MediaCodec into GeelyClusterMap's
 * SurfaceTexture and show it on the H52 cluster.
 *
 * Timeline (log tag DiPlay-Spike):
 *   0-20 s   route active, decoder fed at 15 fps (looping 20-frame sample)  -> video on cluster
 *  20-40 s   route active, decoder idle                                    -> keep-alive holds last frame
 *  40-50 s   route inactive                                                -> cluster back to normal
 *  50 s      GeelyClusterMap.stop, finish
 */
public class SpikeActivity extends Activity {
    private static final String TAG = "DiPlay-Spike";
    private static final byte[] START = {0, 0, 0, 1};

    private volatile Surface clusterSurface;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        status = new TextView(this);
        status.setTextSize(28);
        status.setText("DiPlay cluster spike running...");
        setContentView(status);
        boolean available = GeelyClusterMap.available(this);
        Log.i(TAG, "available=" + available);
        if (!available) { status.setText("not available"); return; }
        GeelyClusterMap.start(this);
        new Thread(new Runnable() { @Override public void run() { runSpike(); } }, "spike").start();
    }

    /** Called by GeelyClusterMap via reflection, like CarPlayHostActivity.onClusterSurface. */
    private void onClusterSurface(Surface surface) {
        Log.i(TAG, "onClusterSurface " + (surface != null));
        clusterSurface = surface;
    }

    private void runSpike() {
        try {
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (clusterSurface == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
            if (clusterSurface == null) { Log.e(TAG, "RESULT no cluster surface"); return; }

            byte[] avcc = read("video-smoke.avcc");
            List<byte[]> frames = readFrames("video-smoke.frames");
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", 320, 240);
            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps(avcc)));
            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps(avcc)));
            MediaCodec codec = MediaCodec.createDecoderByType("video/avc");
            Log.i(TAG, "decoder " + codec.getName());
            codec.configure(format, clusterSurface, null, 0);
            codec.start();

            GeelyClusterMap.onNavStatus(8);
            phase("A decode 20s");
            int rendered = decode(codec, frames, 20000);
            Log.i(TAG, "RESULT decoded+rendered=" + rendered + " in 20s (" + (rendered / 20) + " fps)");

            phase("B keep-alive 20s (decoder idle)");
            SystemClock.sleep(20000);

            GeelyClusterMap.onNavStatus(9);
            phase("C route inactive 10s");
            SystemClock.sleep(10000);

            codec.stop();
            codec.release();
            phase("D stop");
            runOnUiThread(new Runnable() { @Override public void run() {
                GeelyClusterMap.stop(SpikeActivity.this);
                status.setText("spike done");
                Log.i(TAG, "RESULT done");
            }});
        } catch (Exception error) {
            Log.e(TAG, "RESULT spike failed", error);
        }
    }

    private int decode(MediaCodec codec, List<byte[]> frames, long durationMs) {
        ByteBuffer[] inputs = codec.getInputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long start = SystemClock.uptimeMillis();
        long ptsUs = 0;
        int index = 0, rendered = 0;
        while (SystemClock.uptimeMillis() - start < durationMs) {
            int in = codec.dequeueInputBuffer(10000);
            if (in >= 0) {
                byte[] annexB = toAnnexB(frames.get(index % frames.size()));
                inputs[in].clear();
                inputs[in].put(annexB);
                codec.queueInputBuffer(in, 0, annexB.length, ptsUs, 0);
                ptsUs += 66666;
                index++;
            }
            int out;
            while ((out = codec.dequeueOutputBuffer(info, 0)) >= 0 || out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                    || out == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (out >= 0) { codec.releaseOutputBuffer(out, true); rendered++; }
                else if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) Log.i(TAG, "format " + codec.getOutputFormat());
            }
            SystemClock.sleep(66);
        }
        return rendered;
    }

    private void phase(String name) { Log.i(TAG, "PHASE " + name); }

    // ---- sample parsing: avcC record + length-prefixed frames -> Annex B ----

    private byte[] read(String asset) throws Exception {
        InputStream in = getAssets().open(asset);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally { in.close(); }
    }

    private List<byte[]> readFrames(String asset) throws Exception {
        List<byte[]> list = new ArrayList<byte[]>();
        DataInputStream in = new DataInputStream(getAssets().open(asset));
        try {
            while (in.available() > 0) {
                byte[] f = new byte[in.readInt()];
                in.readFully(f);
                list.add(f);
            }
        } finally { in.close(); }
        return list;
    }

    private static int u16(byte[] b, int o) { return ((b[o] & 0xff) << 8) | (b[o + 1] & 0xff); }

    private static byte[] withStart(byte[] src, int offset, int length) {
        byte[] out = new byte[4 + length];
        System.arraycopy(START, 0, out, 0, 4);
        System.arraycopy(src, offset, out, 4, length);
        return out;
    }

    private static byte[] sps(byte[] avcc) { return withStart(avcc, 8, u16(avcc, 6)); }

    private static byte[] pps(byte[] avcc) {
        int spsLen = u16(avcc, 6);
        int ppsLenAt = 8 + spsLen + 1;
        return withStart(avcc, ppsLenAt + 2, u16(avcc, ppsLenAt));
    }

    private static byte[] toAnnexB(byte[] frame) {
        byte[] out = frame.clone();                     // 4-byte lengths -> 00 00 00 01, same size
        int o = 0;
        while (o + 4 <= out.length) {
            int n = ((out[o] & 0xff) << 24) | ((out[o + 1] & 0xff) << 16) | ((out[o + 2] & 0xff) << 8) | (out[o + 3] & 0xff);
            out[o] = 0; out[o + 1] = 0; out[o + 2] = 0; out[o + 3] = 1;
            o += 4 + n;
        }
        return out;
    }
}
