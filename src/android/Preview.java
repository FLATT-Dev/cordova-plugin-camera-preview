package com.cordovaplugincamerapreview;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.RelativeLayout;
import org.apache.cordova.LOG;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

class Preview extends RelativeLayout implements SurfaceHolder.Callback {
    private final String TAG = "Preview";

    CustomSurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    Camera.Size mPreviewSize;
    List<Camera.Size> mSupportedPreviewSizes;
    Camera mCamera;
    int cameraId;
    int displayOrientation;
    int viewWidth;
    int viewHeight;

    Preview(Context context) {
        super(context);

        mSurfaceView = new CustomSurfaceView(context);
        addView(mSurfaceView);

        requestLayout();

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void setCamera(Camera camera, int cameraId) {
        mCamera = camera;
        this.cameraId = cameraId;
        if (mCamera != null) {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            setCameraDisplayOrientation();
            //mCamera.getParameters().setRotation(getDisplayOrientation());
            //requestLayout();
        }
    }

    public int getDisplayOrientation() {
        return displayOrientation;
    }

    private void setCameraDisplayOrientation() {
        Camera.CameraInfo info=new Camera.CameraInfo();
        int rotation=
                ((Activity)getContext()).getWindowManager().getDefaultDisplay()
                        .getRotation();
        int degrees=0;
        DisplayMetrics dm=new DisplayMetrics();

        Camera.getCameraInfo(cameraId, info);
        ((Activity)getContext()).getWindowManager().getDefaultDisplay().getMetrics(dm);

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees=0;
                break;
            case Surface.ROTATION_90:
                degrees=90;
                break;
            case Surface.ROTATION_180:
                degrees=180;
                break;
            case Surface.ROTATION_270:
                degrees=270;
                break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation=(info.orientation + degrees) % 360;
            displayOrientation=(360 - displayOrientation) % 360;
        } else {
            displayOrientation=(info.orientation - degrees + 360) % 360;
        }

        Log.d(TAG, "screen is rotated " + degrees + "deg from natural");
        Log.d(TAG, (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back")
                + " camera is oriented -" + info.orientation + "deg from natural");
        Log.d(TAG, "need to rotate preview " + displayOrientation + "deg");
        mCamera.setDisplayOrientation(displayOrientation);
    }

    public void switchCamera(Camera camera, int cameraId) {
        setCamera(camera, cameraId);
        mSupportedPreviewSizes = camera.getParameters().getSupportedPreviewSizes();
        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, viewWidth, viewHeight);
        }
        try {
            camera.setPreviewDisplay(mHolder);
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            camera.setParameters(parameters);
        }
        catch (IOException exception) {
            Log.e(TAG, exception.getMessage());
        }
        //requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        viewWidth = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        viewHeight = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(viewWidth, viewHeight);

        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, viewWidth, viewHeight);
            Log.i(TAG, "called supported sizes");
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            int width = r - l;
            int height = b - t;

            int previewWidth = width;
            int previewHeight = height;

            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;

                if(displayOrientation == 90 || displayOrientation == 270) {
                    previewWidth = mPreviewSize.height;
                    previewHeight = mPreviewSize.width;
                }

                LOG.d(TAG, "previewWidth:" + previewWidth + " previewHeight:" + previewHeight);
            }

            int nW;
            int nH;
            int top;
            int left;

            float scale = 1.0f;

            // Center the child SurfaceView within the parent.
            if (width * previewHeight < height * previewWidth) {
                Log.d(TAG, "center horizontally");
                int scaledChildWidth = (int)((previewWidth * height / previewHeight) * scale);
                nW = (width + scaledChildWidth) / 2;
                nH = (int)(height * scale);
                top = 0;
                left = (width - scaledChildWidth) / 2;
            }
            else {
                Log.d(TAG, "center vertically");
                int scaledChildHeight = (int)((previewHeight * width / previewWidth) * scale);
                nW = (int)(width * scale);
                nH = (height + scaledChildHeight) / 2;
                top = (height - scaledChildHeight) / 2;
                left = 0;
            }
            child.layout(left, top, nW, nH);

            Log.d("layout", "left:" + left);
            Log.d("layout", "top:" + top);
            Log.d("layout", "right:" + nW);
            Log.d("layout", "bottom:" + nH);
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        try {
            if (mCamera != null) {
                mSurfaceView.setWillNotDraw(false);
                mCamera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (displayOrientation == 90 || displayOrientation == 270) {
            targetRatio = (double) h / w;
        }
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        Log.d(TAG, "optimal preview size: w: " + optimalSize.width + " h: " + optimalSize.height);
        return optimalSize;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if(mCamera != null) {
            // Now that the size is known, set up the camera parameters and begin
            // the preview.
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            if (mSupportedPreviewSizes != null) {
                mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, w, h);
            }
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            requestLayout();
            //mCamera.setDisplayOrientation(90);
            mCamera.setParameters(parameters);
            mCamera.startPreview();
        }
    }

    public byte[] getFramePicture(byte[] data, Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        int format = parameters.getPreviewFormat();

        //YUV formats require conversion
        if (format == ImageFormat.NV21 || format == ImageFormat.YUY2 || format == ImageFormat.NV16) {
            int w = parameters.getPreviewSize().width;
            int h = parameters.getPreviewSize().height;

            // Get the YuV image
            YuvImage yuvImage = new YuvImage(data, format, w, h, null);
            // Convert YuV to Jpeg
            Rect rect = new Rect(0, 0, w, h);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(rect, 80, outputStream);
            return outputStream.toByteArray();
        }
        return data;
    }
    public void setOneShotPreviewCallback(Camera.PreviewCallback callback) {
        if(mCamera != null) {
            mCamera.setOneShotPreviewCallback(callback);
        }
    }
}
