/*
 * Copyright 2014 Google Inc. All Rights Reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sveder.cardboardpassthrough;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import com.google.vrtoolkit.cardboard.*;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.graphics.RectF;


/**
 * A Cardboard sample application.
 */
public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer, OnFrameAvailableListener, Camera.FaceDetectionListener {

    private static final String TAG = "MainActivity";
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;
    private Camera camera;
    int mVertexShader, mFragmentShader, mInvertedFragmentShader, mInvertedProgram, mTempProg, mBlackWhite;
    boolean mInvertedToggleFlag = true;

    int startTime = 50 * 1000;
    int laughTime = startTime;
    int screamTime = startTime + 5 * 1000;
    int flickerTotal = 31;
    int flickerCount = 0;
    private int mProgram;

    boolean scareFaces = false;

    private MediaPlayer mPlayer = null;
    private Handler mHandler = null;
    private Runnable invertRun = new Runnable() {
        @Override
        public void run() {
            if (flickerCount == 0) {
                Log.d("lol","Scream plz?");
                mPlayer = MediaPlayer.create(MainActivity.this, R.raw.scream);
                mPlayer.start();

                mOverlayView.startBlood();
            }

            if (flickerCount > flickerTotal){
                scareFaces = true;
                mProgram = mBlackWhiteProgram;
                return;
            }
            flickerCount += 1;
            if(mInvertedToggleFlag){
                mTempProg = mProgram;
                mProgram = mInvertedProgram;
                mInvertedProgram = mTempProg;
                mInvertedToggleFlag = false;
            }else{
                mTempProg = mInvertedProgram;
                mInvertedProgram = mProgram;
                mProgram = mTempProg;
                mInvertedToggleFlag = true;
            }

            mHandler.removeCallbacks(invertRun);
            mHandler.postDelayed(invertRun, 50);
        }
    };

    private Runnable doLaugh = new Runnable() {
        @Override
        public void run() {
            if (mInvertedToggleFlag) {
                mPlayer = MediaPlayer.create(MainActivity.this, R.raw.evil_laugh);
                mPlayer.start();
                SystemClock.sleep(750);

                vibrator.vibrate(750);
            }
        }
    };

    private final String vertexShaderCode =
	        "attribute vec4 position;" +
	        "attribute vec2 inputTextureCoordinate;" +
	        "varying vec2 textureCoordinate;" +
	        "void main()" +
	        "{"+
	            "gl_Position = position;"+
	            "textureCoordinate = inputTextureCoordinate;" +
	        "}";

	    private final String invertedFragmentShaderCode =
	        "#extension GL_OES_EGL_image_external : require\n"+
	        "precision mediump float;" +
	        "varying vec2 textureCoordinate;                            \n" +
	        "uniform samplerExternalOES s_texture;               \n" +
	        "void main(void) {" +
	        "  gl_FragColor = 1.0 - texture2D( s_texture, textureCoordinate );\n" +
	        "}";

        private final String fragmentShaderCode =
            "#extension GL_OES_EGL_image_external : require\n"+
                    "precision mediump float;" +
                    "varying vec2 textureCoordinate;                            \n" +
                    "uniform samplerExternalOES s_texture;               \n" +
                    "uniform float inTime;               \n" +
                    "void main(void) {" +
                    "  gl_FragColor = texture2D( s_texture, textureCoordinate );\n" +
                    "  gl_FragColor.r = inTime/10.0;\n" +
                    "}";

      private final String blackWhiteShader =
        "#extension GL_OES_EGL_image_external : require\n"+
                    "precision mediump float;" +
                    "varying vec2 textureCoordinate;                            \n" +
                    "uniform samplerExternalOES s_texture;               \n" +
                    "void main(void) {" +
                    "  vec4 Color = texture2D( s_texture, textureCoordinate );\n" +
                    "  gl_FragColor = vec4(vec3(Color.r + Color.g + Color.b) / 3.0, Color.a);\n" +
                    "}";

        private FloatBuffer vertexBuffer, textureVerticesBuffer, vertexBuffer2;
        private ShortBuffer drawListBuffer, buf2;
        private int mPositionHandle, mPositionHandle2;
        private int mColorHandle;
        private int mTextureCoordHandle;


    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 2;
    static float squareVertices[] = { // in counterclockwise order:
    	-1.0f, -1.0f,   // 0.left - mid
    	 1.0f, -1.0f,   // 1. right - mid
    	-1.0f, 1.0f,   // 2. left - top
    	 1.0f, 1.0f,   // 3. right - top

    };
    
    

    
    private short drawOrder[] =  {0, 2, 1, 1, 2, 3 }; // order to draw vertices

    static float textureVertices[] = {
	 0.0f, 1.0f,  // A. left-bottom
	   1.0f, 1.0f,  // B. right-bottom
	   0.0f, 0.0f,  // C. left-top
	   1.0f, 0.0f   // D. right-top  
      };

    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private int texture;

    private CardboardOverlayView mOverlayView;

	private CardboardView cardboardView;
	private SurfaceTexture surface;
	private float[] mView;
	private float[] mCamera;

    private Vibrator vibrator;
    private int mBlackWhiteProgram;

    public void startCamera(int texture)
    {
        surface = new SurfaceTexture(texture);
        surface.setOnFrameAvailableListener(this);


        camera = Camera.open();
        camera.setFaceDetectionListener(this);


        try
        {
            camera.setPreviewTexture(surface);
            camera.startPreview();
        }
        catch (IOException ioe)
        {
            Log.w("MainActivity","CAM LAUNCH FAILED");
        }
        camera.startFaceDetection();

    }
	
    static private int createTexture()
    {
        int[] texture = new int[1];

        GLES20.glGenTextures(1,texture, 0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
             GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_LINEAR);        
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
             GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
     GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
             GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
     GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
             GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return texture[0];
    }

	
    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader
     * @param type The type of shader we will be creating.
     * @return
     */
    private int loadGLShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     * @param func
     */
    private static void checkGLError(String func) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, func + ": --glError " + error);
            throw new RuntimeException(func + ": glError " + error);
        }
    }

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.common_ui);
        cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        mCamera = new float[16];
        mView = new float[16];

        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);
        mOverlayView.show3DRect(0,0);

        vibrator = (Vibrator) this.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
    }

    /**
     * Creates the buffers we use to store information about the 3D world. OpenGL doesn't use Java
     * arrays, but rather needs data in a format it can understand. Hence we use ByteBuffers.
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well
        
        ByteBuffer bb = ByteBuffer.allocateDirect(squareVertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareVertices);
        vertexBuffer.position(0);
        

        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);
        

        ByteBuffer bb2 = ByteBuffer.allocateDirect(textureVertices.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureVerticesBuffer = bb2.asFloatBuffer();
        textureVerticesBuffer.put(textureVertices);
        textureVerticesBuffer.position(0);

        mVertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        mFragmentShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        mInvertedFragmentShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, invertedFragmentShaderCode);
        mBlackWhite = loadGLShader(GLES20.GL_FRAGMENT_SHADER, blackWhiteShader);

        mProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(mProgram, mVertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mProgram, mFragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(mProgram);

        mInvertedProgram = GLES20.glCreateProgram();
        // create empty OpenGL ES Program
        GLES20.glAttachShader(mInvertedProgram, mVertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mInvertedProgram, mInvertedFragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(mInvertedProgram);

        mBlackWhiteProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mBlackWhiteProgram, mVertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mBlackWhiteProgram, mBlackWhite); // add the fragment shader to program
        GLES20.glLinkProgram(mBlackWhiteProgram);

        
        texture = createTexture();
        startCamera(texture);

    }
    /**
     * Prepares OpenGL ES before we draw a frame.
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {

    	float[] mtx = new float[16];
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        surface.updateTexImage();
        surface.getTransformMatrix(mtx);

    }
	
    @Override
	public void onFrameAvailable(SurfaceTexture arg0) {
		this.cardboardView.requestRender();

    }

    /**
     * Draws a frame for an eye. The transformation for that eye (from the camera) is passed in as
     * a parameter.
     * @param transform The transformations to apply to render this eye.
     */
    @Override
    public void onDrawEye(EyeTransform transform) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        
        GLES20.glUseProgram(mProgram);

        GLES20.glActiveTexture(GL_TEXTURE_EXTERNAL_OES);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "position");
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
        		false,vertexStride, vertexBuffer);

        int inTime = GLES20.glGetUniformLocation(mProgram, "inTime");
        GLES20.glEnable(inTime);
        GLES20.glUniform1f(inTime, SystemClock.currentThreadTimeMillis() / 1000);
        Log.d("lol", "TIME: " + SystemClock.currentThreadTimeMillis() / 1000.0);
        mTextureCoordHandle = GLES20.glGetAttribLocation(mProgram, "inputTextureCoordinate");
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);
        GLES20.glVertexAttribPointer(mTextureCoordHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
        		false,vertexStride, textureVerticesBuffer);

        mColorHandle = GLES20.glGetAttribLocation(mProgram, "s_texture");

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length,
        					  GLES20.GL_UNSIGNED_SHORT, drawListBuffer);


        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordHandle);
        
        Matrix.multiplyMM(mView, 0, transform.getEyeView(), 0, mCamera, 0);

        if(mHandler == null){
            mHandler = new Handler(Looper.getMainLooper());
            mHandler.postDelayed(invertRun, screamTime);
            mHandler.postDelayed(doLaugh, laughTime);
        }
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    /**
     * Increment the score, hide the object, and give feedback if the user pulls the magnet while
     * looking at the object. Otherwise, remind the user what to do.
     */
    @Override
    public void onCardboardTrigger() {
    }


    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
        if (!scareFaces) {
            return;
        }
        RectF rectf = new RectF();

        if (faces.length != 0)
        {
            flickerCount = 0;
            flickerTotal = 40;
            invertRun.run();
            return;
        }

        for (Camera.Face face :faces)
        {
            int centerX = face.rect.centerX();
            int centerY = face.rect.centerY();

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            //Width needs to be divided by 2 because the overlay is for both eyes:
            prepareMatrix(matrix, false, mOverlayView.getWidth() / 2 , mOverlayView.getHeight());
            rectf.set(face.rect);
            matrix.mapRect(rectf);

            mOverlayView.maskFace((int) rectf.centerX(), (int) rectf.centerY());
            Log.d("lol", "Found face! x = " + rectf.centerX() + ", y = " + rectf.centerY());
        }
    }

    public static void prepareMatrix(android.graphics.Matrix matrix, boolean mirror,
                                     int viewWidth, int viewHeight) {
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);

    }
}
