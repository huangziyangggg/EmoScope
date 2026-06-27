package com.example.emoscope;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns CameraX and MediaPipe wiring. The activity receives only UI-ready events.
 */
public class CameraEmotionController {

    public interface Callback {
        void onLightState(int iconRes, String description, int luminance);
        void onFaceBlendshapes(List<List<Category>> blendshapes, long timestampMs);
        void onNoFace();
        void onCameraError(String message);
        /** 实验性 rPPG 心率更新（每3秒触发一次） */
        void onRppgUpdate(RppgAnalyzer.RppgResult result);
    }

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView viewFinder;
    private final ExecutorService backgroundExecutor;
    private final Callback callback;
    private final AtomicBoolean faceDetectionInFlight = new AtomicBoolean(false);

    private ProcessCameraProvider cameraProvider;
    private FaceLandmarker faceLandmarker;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private long lastFaceDetectionMs = 0;
    private final SignalUpdateGate lightUpdateGate = new SignalUpdateGate(450, 10);

    // ── 实验性 rPPG ──
    private final RppgAnalyzer rppgAnalyzer = new RppgAnalyzer();

    public CameraEmotionController(Context context, LifecycleOwner lifecycleOwner,
                                   PreviewView viewFinder,
                                   ExecutorService backgroundExecutor,
                                   Callback callback) {
        this.context = context.getApplicationContext();
        this.lifecycleOwner = lifecycleOwner;
        this.viewFinder = viewFinder;
        this.backgroundExecutor = backgroundExecutor;
        this.callback = callback;
    }

    public void setupVisualEngine() {
        try {
            BaseOptions base = BaseOptions.builder()
                    .setModelAssetPath(Constants.FACELANDMARKER_MODEL).build();
            FaceLandmarker.FaceLandmarkerOptions options =
                    FaceLandmarker.FaceLandmarkerOptions.builder()
                            .setBaseOptions(base)
                            .setRunningMode(RunningMode.LIVE_STREAM)
                            .setResultListener(this::onFaceAnalyzed)
                            .setNumFaces(1)
                            .setOutputFaceBlendshapes(true)
                            .build();
            faceLandmarker = FaceLandmarker.createFromOptions(context, options);
        } catch (Throwable t) {
            Log.e(Constants.TAG, "FaceLandmarker init failed", t);
            callback.onCameraError("面部分析模型初始化失败，相机仍可打开但暂不能识别表情");
        }
    }

    public void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            try {
                if (cameraProvider != null) cameraProvider.unbindAll();
                cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analysis.setAnalyzer(backgroundExecutor, this::analyzeFrame);

                cameraProvider.bindToLifecycle(lifecycleOwner,
                        new CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                        preview, analysis);
            } catch (Exception e) {
                Log.e(Constants.TAG, "Camera bind failed", e);
                callback.onCameraError("相机启动失败，请检查权限或确认没有其他应用正在使用相机");
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void flipCamera() {
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
        rppgAnalyzer.reset();
        startCamera();
    }

    public void release() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (faceLandmarker != null) faceLandmarker.close();
        rppgAnalyzer.reset();
    }

    private void analyzeFrame(androidx.camera.core.ImageProxy proxy) {
        try {
            ByteBuffer buffer = proxy.getPlanes()[0].getBuffer();
            int avgLuminance = averageLuminance(buffer);

            // ── 实验性 rPPG：每帧采样面部中央ROI绿色通道 ──
            long now = System.currentTimeMillis();
            float greenMean = sampleFaceRoiGreen(buffer, proxy.getWidth(), proxy.getHeight());
            rppgAnalyzer.addSample(greenMean, now);
            if (rppgAnalyzer.shouldProcess()) {
                RppgAnalyzer.RppgResult rppgResult = rppgAnalyzer.process();
                callback.onRppgUpdate(rppgResult);
            }

            if (lightUpdateGate.shouldUpdate(now, avgLuminance)) {
                callback.onLightState(lightIcon(avgLuminance), lightDescription(avgLuminance), avgLuminance);
            }

            boolean canRunFaceDetection = faceLandmarker != null
                    && now - lastFaceDetectionMs >= Constants.FACE_DETECTION_INTERVAL_MS
                    && faceDetectionInFlight.compareAndSet(false, true);
            if (canRunFaceDetection) {
                lastFaceDetectionMs = now;
                Bitmap bitmap = proxy.toBitmap();
                if (bitmap != null) {
                    try {
                        faceLandmarker.detectAsync(
                                new BitmapImageBuilder(bitmap).build(),
                                proxy.getImageInfo().getTimestamp() / 1000000);
                    } catch (Throwable t) {
                        faceDetectionInFlight.set(false);
                        Log.e(Constants.TAG, "FaceLandmarker detectAsync failed", t);
                    }
                } else {
                    faceDetectionInFlight.set(false);
                }
            }
        } catch (Throwable t) {
            faceDetectionInFlight.set(false);
            Log.e(Constants.TAG, "Camera analysis failed", t);
        } finally {
            proxy.close();
        }
    }

    /**
     * 从RGBA帧的中央上部ROI（额头区域）采样绿色通道均值，用于实验性rPPG心率检测。
     * ROI: 水平居中30%宽度 × 垂直上方25%高度。
     */
    private float sampleFaceRoiGreen(ByteBuffer buffer, int width, int height) {
        try {
            // ROI 定义：中央上部（额头通常在此区域）
            int roiX = width * 35 / 100;
            int roiY = height * 10 / 100;
            int roiW = width * 30 / 100;
            int roiH = height * 25 / 100;

            int sampleStep = 3; // 每隔3个像素采样
            long total = 0;
            int count = 0;

            int bufferStart = buffer.position();
            for (int y = roiY; y < roiY + roiH && y < height; y += sampleStep) {
                for (int x = roiX; x < roiX + roiW && x < width; x += sampleStep) {
                    int pixelOffset = bufferStart + (y * width + x) * 4;
                    if (pixelOffset + 2 < buffer.limit()) {
                        // RGBA: offset+1 = Green
                        total += (buffer.get(pixelOffset + 1) & 0xFF);
                        count++;
                    }
                }
            }

            return count > 0 ? (float) total / count : 128f;
        } catch (Exception e) {
            return 128f;
        }
    }

    private int averageLuminance(ByteBuffer buffer) {
        int sampleStep = Constants.LUMINANCE_SAMPLE_STEP;
        long total = 0;
        int byteCount = buffer.remaining();
        int bufferStart = buffer.position();
        int samples = 0;
        for (int i = 0; i < byteCount; i += sampleStep) {
            total += (buffer.get(bufferStart + i) & 0xFF);
            samples++;
        }
        return samples > 0 ? (int) (total / samples) : 128;
    }

    private int lightIcon(int avgLuminance) {
        if (avgLuminance < Constants.LUMINANCE_LOW) return R.drawable.ic_light_moon;
        if (avgLuminance > Constants.LUMINANCE_HIGH) return R.drawable.ic_light_sun;
        return R.drawable.ic_light_cloud;
    }

    private String lightDescription(int avgLuminance) {
        if (avgLuminance < Constants.LUMINANCE_LOW) return "环境偏暗";
        if (avgLuminance > Constants.LUMINANCE_HIGH) return "光线较亮";
        return "光照适中";
    }

    private void onFaceAnalyzed(FaceLandmarkerResult result, MPImage inputImage) {
        faceDetectionInFlight.set(false);
        if (!result.faceBlendshapes().isPresent() || result.faceBlendshapes().get().isEmpty()) {
            callback.onNoFace();
            return;
        }
        callback.onFaceBlendshapes(result.faceBlendshapes().get(), System.currentTimeMillis());
    }
}
