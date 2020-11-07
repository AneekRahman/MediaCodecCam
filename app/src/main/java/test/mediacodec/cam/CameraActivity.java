package test.mediacodec.cam;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

public class CameraActivity extends AppCompatActivity {

    // Variables declarations

    TextureView mPreviewTextureView;
    FrameLayout mPreviewHolder;
    Button captureBtn, saveBtn;

    CameraRenderer2 mCameraRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Variables initialization

        mPreviewHolder = (FrameLayout) findViewById(R.id.texture_view_holder);
        captureBtn = (Button) findViewById(R.id.capture_btn);
        saveBtn = (Button) findViewById(R.id.save_btn);

        mCameraRenderer = new CameraRenderer2(this);
        mPreviewTextureView = new TextureView(this);
        mPreviewHolder.addView(mPreviewTextureView);
        mPreviewTextureView.setSurfaceTextureListener(mCameraRenderer);
        mCameraRenderer.setSelectedFilter(9);

        captureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCameraRenderer.callCaptureVideo();
            }
        });
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCameraRenderer.callSaveVideo();
            }
        });

    }


    private boolean capture() {

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MediaCodecPics");
        if (!mediaStorageDir.exists()) {
            mediaStorageDir.mkdir();
        }
        String imageFilePath = mediaStorageDir.getPath() + File.separator + "PIC.jpg" ;
        File imageFile = new File(imageFilePath);
        if (imageFile.exists()) {
            imageFile.delete();
        }

        // create bitmap screen capture
        Bitmap bitmap = mPreviewTextureView.getBitmap();
        OutputStream outputStream = null;

        try {
            outputStream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            outputStream.flush();
            outputStream.close();
            Toast.makeText(getApplicationContext(), "SAVED", Toast.LENGTH_LONG).show();

            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(imageFile);
            mediaScanIntent.setData(contentUri);
            sendBroadcast(mediaScanIntent);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

                                // Codec Stuff



}
