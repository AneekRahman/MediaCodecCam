package test.mediacodec.cam;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import test.mediacodec.cam.filter.AsciiArtFilter;
import test.mediacodec.cam.filter.BasicDeformFilter;
import test.mediacodec.cam.filter.BlueorangeFilter;
import test.mediacodec.cam.filter.CameraFilter;
import test.mediacodec.cam.filter.ChromaticAberrationFilter;
import test.mediacodec.cam.filter.ContrastFilter;
import test.mediacodec.cam.filter.CrackedFilter;
import test.mediacodec.cam.filter.CrosshatchFilter;
import test.mediacodec.cam.filter.EMInterferenceFilter;
import test.mediacodec.cam.filter.EdgeDetectionFilter;
import test.mediacodec.cam.filter.JFAVoronoiFilter;
import test.mediacodec.cam.filter.LegofiedFilter;
import test.mediacodec.cam.filter.LichtensteinEsqueFilter;
import test.mediacodec.cam.filter.MappingFilter;
import test.mediacodec.cam.filter.MoneyFilter;
import test.mediacodec.cam.filter.NoiseWarpFilter;
import test.mediacodec.cam.filter.OriginalFilter;
import test.mediacodec.cam.filter.PixelizeFilter;
import test.mediacodec.cam.filter.PolygonizationFilter;
import test.mediacodec.cam.filter.RefractionFilter;
import test.mediacodec.cam.filter.TileMosaicFilter;
import test.mediacodec.cam.filter.TrianglesMosaicFilter;

import static android.content.Context.WINDOW_SERVICE;

public class CameraRenderer2 implements TextureView.SurfaceTextureListener, Runnable {

    // ----------------- Codec Stuff ------------------

    private CircularEncoder mCircEncoder;
    MainHandler mHandler;

    private static final int VIDEO_WIDTH = 1280;  // dimensions for 720p video
    private static final int VIDEO_HEIGHT = 720;
    private static final int DESIRED_PREVIEW_FPS = 15;
    private int mCameraPreviewThousandFps;

    public boolean mFileSaveInProgress;
    File mOutputFile;

    // ----------------- Codec Stuff End ------------------

    // Camera orientation variables
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }
    ///////////////////////////////////////////////////////////////////////////////////

    private final String TAG = "Camera-Renderer::::::";


    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int DRAW_INTERVAL = 1000 / 30;

    Context mContext;

    Camera mCamera;
    int mCameraId;
    SurfaceTexture mSurfaceTexture, mCameraSurfaceTexture;
    int mCameraTextureId;
    int mHeight;
    int mWidth;

    private EGLDisplay eglDisplay;
    private EGLSurface eglSurface;
    private EGLContext eglContext;
    private EGL10 egl10;

    Thread mRenderThread;

    int mSelectedFilterId = 0;
    CameraFilter mSelectedCameraFilter;
    private SparseArray<CameraFilter> mCameraFilterList = new SparseArray<>();



    // Instance of CameraRenderer
    public CameraRenderer2(Context context) {
        this.mContext = context;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {

        if (mRenderThread != null && mRenderThread.isAlive()) {
            mRenderThread.interrupt();
        }
        mRenderThread = new Thread(this);

        mSurfaceTexture = surfaceTexture;
        mHeight = -height;
        mWidth = -width;

        if(initCamera()){
            Toast.makeText(mContext, "Initialized Camera!", Toast.LENGTH_LONG).show();
        }else{
            Toast.makeText(mContext, "Initialization of camera failed!", Toast.LENGTH_LONG).show();
        }

        mRenderThread.start();

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

        mHeight = -height;
        mWidth = -width;

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {

        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
        }
        if (mRenderThread != null && mRenderThread.isAlive()) {
            mRenderThread.interrupt();
        }
        if (mCircEncoder != null) {
            mCircEncoder.shutdown();
            mCircEncoder = null;
        }

        CameraFilter.release();

        return true;

    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {/* EMPTY */}

    @Override
    public void run() {
        initGL(mSurfaceTexture);

        // Setup camera filters map
        mCameraFilterList.append(0, new OriginalFilter(mContext));
        mCameraFilterList.append(1, new EdgeDetectionFilter(mContext));
        mCameraFilterList.append(2, new PixelizeFilter(mContext));
        mCameraFilterList.append(3, new ContrastFilter(mContext));
        mCameraFilterList.append(4, new EMInterferenceFilter(mContext));
        mCameraFilterList.append(5, new TrianglesMosaicFilter(mContext));
        mCameraFilterList.append(6, new LegofiedFilter(mContext));
        mCameraFilterList.append(7, new TileMosaicFilter(mContext));
        mCameraFilterList.append(8, new BlueorangeFilter(mContext));
        mCameraFilterList.append(9, new ChromaticAberrationFilter(mContext));
        mCameraFilterList.append(10, new BasicDeformFilter(mContext));
        mCameraFilterList.append(11, new NoiseWarpFilter(mContext));
        mCameraFilterList.append(12, new RefractionFilter(mContext));
        mCameraFilterList.append(13, new MappingFilter(mContext));
        mCameraFilterList.append(14, new CrosshatchFilter(mContext));
        mCameraFilterList.append(15, new LichtensteinEsqueFilter(mContext));
        mCameraFilterList.append(16, new AsciiArtFilter(mContext));
        mCameraFilterList.append(17, new MoneyFilter(mContext));
        mCameraFilterList.append(18, new CrackedFilter(mContext));
        mCameraFilterList.append(19, new PolygonizationFilter(mContext));
        mCameraFilterList.append(20, new JFAVoronoiFilter(mContext));
        setSelectedFilter(mSelectedFilterId);

        // Create texture for camera preview
        mCameraTextureId = MyGLUtils.genTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        mCameraSurfaceTexture = new SurfaceTexture(mCameraTextureId);

        // Start camera preview
        try {
            mCamera.setPreviewTexture(mCameraSurfaceTexture);
            mCamera.startPreview();
        } catch (IOException ioe) {
            // Something bad happened
        }

        // Render loop
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (mWidth < 0 && mWidth < 0) GLES20.glViewport(0, 0, mWidth = -mWidth, mHeight = -mHeight);

                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                // Update the camera preview texture
                synchronized (this) {
                    mCameraSurfaceTexture.updateTexImage();
                }

                // Draw camera preview
                mSelectedCameraFilter.draw(mCameraTextureId, mWidth, mHeight);

                // Flush
                GLES20.glFlush();
                egl10.eglSwapBuffers(eglDisplay, eglSurface);

                Thread.sleep(DRAW_INTERVAL);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        mCameraSurfaceTexture.release();
        GLES20.glDeleteTextures(1, new int[]{mCameraTextureId}, 0);

    }

                                 // Camera related defined methods

    // Camera initialization
    public boolean initCamera(){

        int frontCamTempId = getFrontFacingCameraID();
        if(frontCamTempId > 4){
            mCameraId = frontCamTempId;
        }else{
            mCameraId = getBackFacingCameraID();
        }
        if(mCameraId < 0) return false; // Initialization failed

        mCamera = Camera.open(mCameraId);
        setCameraOrientation();

        return true; // Initialization complete

    }
    // Get Camera orientation
    public void setCameraOrientation(){
/*
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(manager.getCameraIdList()[mCameraId]);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        int mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int finalRotation = 0;
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wm.getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                finalRotation = DEFAULT_ORIENTATIONS.get(rotation);
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                finalRotation = INVERSE_ORIENTATIONS.get(rotation);
                break;
        }
*/


        Display display = ((WindowManager)mContext.getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

        if(display.getRotation() == Surface.ROTATION_0) {
            mCamera.setDisplayOrientation(90);
        } else if(display.getRotation() == Surface.ROTATION_270) {
            mCamera.setDisplayOrientation(180);
        } else {
        }


    }
    // Get front camera id
    private int getFrontFacingCameraID() {
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }
    // Get back camera id
    private int getBackFacingCameraID() {
        int cameraId = -1;
        //Search for the back facing camera
        //get the number of cameras
        int numberOfCameras = Camera.getNumberOfCameras();
        //for every camera check
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

                            // OpenGL methods

    private void initGL(SurfaceTexture texture) {
        egl10 = (EGL10) EGLContext.getEGL();

        eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }

        int[] version = new int[2];
        if (!egl10.eglInitialize(eglDisplay, version)) {
            throw new RuntimeException("eglInitialize failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }

        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = {
                EGL10.EGL_RENDERABLE_TYPE,
                EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE
        };

        EGLConfig eglConfig = null;
        if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
            throw new IllegalArgumentException("eglChooseConfig failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        } else if (configsCount[0] > 0) {
            eglConfig = configs[0];
        }
        if (eglConfig == null) {
            throw new RuntimeException("eglConfig not initialized");
        }

        int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
        eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
        eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, texture, null);

        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
            int error = egl10.eglGetError();
            if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                Log.e(TAG, "eglCreateWindowSurface returned EGL10.EGL_BAD_NATIVE_WINDOW");
                return;
            }
            throw new RuntimeException("eglCreateWindowSurface failed " +
                    android.opengl.GLUtils.getEGLErrorString(error));
        }

        if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("eglMakeCurrent failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }
    }

    public void setSelectedFilter(@NonNull int id) {
        mSelectedFilterId = id;
        mSelectedCameraFilter = mCameraFilterList.get(id);
        if (mSelectedCameraFilter != null)
            mSelectedCameraFilter.onAttach();
    }

                                    // Codec Stuff

    private static class MainHandler extends Handler implements CircularEncoder.Callback {
        private final String TAG = "MainHandler:::::";

        public static final int MSG_BLINK_TEXT = 0;
        public static final int MSG_FRAME_AVAILABLE = 1;
        public static final int MSG_FILE_SAVE_COMPLETE = 2;
        public static final int MSG_BUFFER_STATUS = 3;

        private WeakReference<CameraRenderer2> mWeakRenderer2;

        public MainHandler(CameraRenderer2 renderer2) {

            mWeakRenderer2 = new WeakReference<CameraRenderer2>(renderer2);

        }

        // CircularEncoder.Callback, called on encoder thread
        @Override
        public void fileSaveComplete(int status) {
            sendMessage(obtainMessage(MSG_FILE_SAVE_COMPLETE, status, 0, null));
        }

        // CircularEncoder.Callback, called on encoder thread
        @Override
        public void bufferStatus(long totalTimeMsec) {
            sendMessage(obtainMessage(MSG_BUFFER_STATUS, (int) (totalTimeMsec >> 32), (int) totalTimeMsec));
        }


        @Override
        public void handleMessage(Message msg) {
            CameraRenderer2 renderer2 = mWeakRenderer2.get();
            if (renderer2 == null) {
                Log.d(TAG, "Got message for dead renderer2");
                return;
            }
            // TODO Implement ui response
            switch (msg.what) {
                case MSG_BLINK_TEXT: {

                    break;
                }
                case MSG_FRAME_AVAILABLE: {

                    break;
                }
                case MSG_FILE_SAVE_COMPLETE: {

                    if (!renderer2.getFileSaveProgressBool()) {
                        throw new RuntimeException("WEIRD: got fileSaveCmplete when not in progress");
                    }
                    renderer2.setFileSaveComplete();
                    break;
                }
                case MSG_BUFFER_STATUS: {

                    break;
                }
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }
    }

    public void callCaptureVideo(){

        // Set video save location
        setOutputFile();
        // Handler decalration
        mHandler = new MainHandler(this);
        // Encoder declaration
        Camera.Parameters parms = mCamera.getParameters();
        // Try to set the frame rate to a constant value.
        mCameraPreviewThousandFps = CameraUtils.chooseFixedPreviewFps(parms, DESIRED_PREVIEW_FPS * 1000);
        try {
            mCircEncoder = new CircularEncoder(mWidth, mHeight, 6000000, mCameraPreviewThousandFps / 1000, 7, mHandler);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCircEncoder.frameAvailableSoon();

    }

    public void callSaveVideo(){

        if (mFileSaveInProgress) {
            Log.w(TAG, "HEY: file save is already in progress");
            return;
        }

        mFileSaveInProgress = true;

        mCircEncoder.saveVideo(mOutputFile);


    }

    public void setFileSaveComplete(){

        mFileSaveInProgress = false;

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(mOutputFile);
        mediaScanIntent.setData(contentUri);
        mContext.sendBroadcast(mediaScanIntent);

    }
    public boolean getFileSaveProgressBool(){

        return mFileSaveInProgress;

    }

    private void setOutputFile(){
        // TODO Make output file decalration better
        File mOutputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MediaCodecFINAL");
        if(!mOutputDir.exists()) mOutputDir.mkdir();
        String outputPath = mOutputDir.getPath() + File.separator + "Video" + ".mp4";
        mOutputFile = new File(outputPath);
        if(!mOutputFile.exists()){
            mOutputFile.delete();
            mOutputFile = new File(outputPath);
        }

    }

}
