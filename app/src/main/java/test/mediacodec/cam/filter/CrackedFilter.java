package test.mediacodec.cam.filter;
import android.content.Context;
import android.opengl.GLES20;

import test.mediacodec.cam.MyGLUtils;
import test.mediacodec.cam.R;

public class CrackedFilter extends CameraFilter {
    private int program;

    public CrackedFilter(Context context) {
        super(context);

        // Build shaders
        program = MyGLUtils.buildProgram(context, R.raw.vertext, R.raw.cracked);
    }

    @Override
    public void onDraw(int cameraTexId, int canvasWidth, int canvasHeight) {
        setupShaderInputs(program,
                new int[]{canvasWidth, canvasHeight},
                new int[]{cameraTexId},
                new int[][]{});
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }
}