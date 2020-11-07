package test.mediacodec.cam.filter;
import android.content.Context;
import android.opengl.GLES20;

import test.mediacodec.cam.MyGLUtils;
import test.mediacodec.cam.R;
import test.mediacodec.cam.RenderBuffer;


public class JFAVoronoiFilter extends CameraFilter {
    private int programImg;
    private int programA;
    private int programB;
    private int programC;

    private RenderBuffer bufA;
    private RenderBuffer bufB;
    private RenderBuffer bufC;

    public JFAVoronoiFilter(Context context) {
        super(context);

        // Build shaders
        programImg = MyGLUtils.buildProgram(context, R.raw.vertext, R.raw.voronoi);
        programA = MyGLUtils.buildProgram(context, R.raw.vertext, R.raw.voronoi_buf_a);
        programB = MyGLUtils.buildProgram(context, R.raw.vertext, R.raw.voronoi_buf_b);
        programC = MyGLUtils.buildProgram(context, R.raw.vertext, R.raw.voronoi_buf_c);
    }

    @Override
    public void onDraw(int cameraTexId, int canvasWidth, int canvasHeight) {
        // TODO move?
        if (bufA == null || bufA.getWidth() != canvasWidth || bufB.getHeight() != canvasHeight) {
            // Create new textures for buffering
            bufA = new RenderBuffer(canvasWidth, canvasHeight, GLES20.GL_TEXTURE4);
            bufB = new RenderBuffer(canvasWidth, canvasHeight, GLES20.GL_TEXTURE5);
            bufC = new RenderBuffer(canvasWidth, canvasHeight, GLES20.GL_TEXTURE6);
        }

        // Render to buf a
        setupShaderInputs(programA,
                new int[]{canvasWidth, canvasHeight},
                new int[]{cameraTexId, bufA.getTexId()},
                new int[][]{new int[]{canvasWidth, canvasHeight}, new int[]{canvasWidth, canvasHeight}});
        bufA.bind();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        bufA.unbind();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);


        // Render to buf b
        setupShaderInputs(programB,
                new int[]{canvasWidth, canvasHeight},
                new int[]{bufB.getTexId(), bufA.getTexId()},
                new int[][]{new int[]{canvasWidth, canvasHeight}, new int[]{canvasWidth, canvasHeight}});
        bufB.bind();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        bufB.unbind();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);


        // Render to buf c
        setupShaderInputs(programC,
                new int[]{canvasWidth, canvasHeight},
                new int[]{bufC.getTexId(), bufB.getTexId()},
                new int[][]{new int[]{canvasWidth, canvasHeight}, new int[]{canvasWidth, canvasHeight}});
        bufC.bind();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        bufC.unbind();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);


        // Render to screen
        setupShaderInputs(programImg,
                new int[]{canvasWidth, canvasHeight},
                new int[]{bufC.getTexId(), bufA.getTexId()},
                new int[][]{new int[]{canvasWidth, canvasHeight}, new int[]{canvasWidth, canvasHeight}});
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }
}
