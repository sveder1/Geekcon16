package com.sveder.cardboardpassthrough;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.Console;

/**
 * Contains two sub-views to provide a simple stereo HUD.
 */
public class CardboardOverlayView extends LinearLayout {
    private static final String TAG = CardboardOverlayView.class.getSimpleName();
    private final CardboardOverlayEyeView mLeftView;
    private final CardboardOverlayEyeView mRightView;
    private TranslateAnimation mBloodAnimation;

    public CardboardOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);

        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f);
        params.setMargins(0, 0, 0, 0);

        mLeftView = new CardboardOverlayEyeView(context, attrs);
        mLeftView.setLayoutParams(params);
        addView(mLeftView);

        mRightView = new CardboardOverlayEyeView(context, attrs);
        mRightView.setLayoutParams(params);
        addView(mRightView);

        // Set some reasonable defaults.
        setDepthOffset(0.016f);
        setColor(Color.rgb(150, 255, 180));
        setVisibility(View.VISIBLE);

        mBloodAnimation = new TranslateAnimation(0, 0, -300, 0);
        mBloodAnimation.setDuration(1000);
    }

    public void show3DToast(String message) {
        setText(message);
        setTextAlpha(1f);
    }

    public void show3DRect(int centerX, int centerY)
    {
        EndAnimationListener mRetry = new EndAnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                Log.d("lol", "moooo!");
                mBloodAnimation = new TranslateAnimation(0, 0, -400, 0);
                mBloodAnimation.setDuration(2000);
                mBloodAnimation.setAnimationListener(this);

                startAnimation(mBloodAnimation);
            }
        };

        mLeftView.drawRect(centerX, centerY);
        mRightView.drawRect(centerX, centerY);
        mBloodAnimation.setAnimationListener(mRetry);

        startAnimation(mBloodAnimation);

    }

    private abstract class EndAnimationListener implements Animation.AnimationListener {
        @Override public void onAnimationRepeat(Animation animation) {}
        @Override public void onAnimationStart(Animation animation) {}
    }

    private void setDepthOffset(float offset) {
        mLeftView.setOffset(offset);
        mRightView.setOffset(-offset);
    }

    private void setText(String text) {
        mLeftView.setText(text);
        mRightView.setText(text);
    }

    private void setTextAlpha(float alpha) {
        mLeftView.setTextViewAlpha(alpha);
        mRightView.setTextViewAlpha(alpha);
    }

    private void setColor(int color) {
        mLeftView.setColor(color);
        mRightView.setColor(color);
    }

    /**
     * A simple view group containing some horizontally centered text underneath a horizontally
     * centered image.
     *
     * This is a helper class for CardboardOverlayView.
     */
    private class CardboardOverlayEyeView extends ViewGroup {
        private final ImageView imageView;
        private final ImageView rectView;
        private final TextView textView;
        private float offset;

        private int centerX, centerY;

        private Bitmap bm;

        public CardboardOverlayEyeView(Context context, AttributeSet attrs) {
            super(context, attrs);
            bm = BitmapFactory.decodeResource(getResources(), R.drawable.blood400);
            imageView = new ImageView(context, attrs);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setAdjustViewBounds(true);  // Preserve aspect ratio.
            addView(imageView);

            rectView = new ImageView(context, attrs);
            rectView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            rectView.setAdjustViewBounds(true);  // Preserve aspect ratio.
            addView(rectView);

            textView = new TextView(context, attrs);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.0f);
            textView.setTypeface(textView.getTypeface(), Typeface.BOLD);
            textView.setGravity(Gravity.CENTER);
            textView.setShadowLayer(3.0f, 0.0f, 0.0f, Color.DKGRAY);
            addView(textView);
        }

        public void setColor(int color) {
            imageView.setColorFilter(color);
            textView.setTextColor(color);
        }

        public void drawRect(int centerX, int centerY)
        {
            this.centerX = centerX;
            this.centerY = centerY;
            Bitmap bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            rectView.setImageBitmap(bm);


        }

        public void setText(String text) {
            textView.setText(text);
        }

        public void setTextViewAlpha(float alpha) {
            textView.setAlpha(alpha);
        }

        public void setOffset(float offset) {
            this.offset = offset;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            // Width and height of this ViewGroup.
            final int width = right - left;
            final int height = bottom - top;

            // The size of the image, given as a fraction of the dimension as a ViewGroup. We multiply
            // both width and heading with this number to compute the image's bounding box. Inside the
            // box, the image is the horizontally and vertically centered.
            final float imageSize = 0.12f;

            // The fraction of this ViewGroup's height by which we shift the image off the ViewGroup's
            // center. Positive values shift downwards, negative values shift upwards.
            final float verticalImageOffset = -0.07f;

            // Vertical position of the text, specified in fractions of this ViewGroup's height.
            final float verticalTextPos = 0.52f;

            // Layout ImageView
            float imageMargin = (1.0f - imageSize) / 2.0f;
            float leftMargin = (int) (width * (imageMargin + offset));
            float topMargin = (int) (height * (imageMargin + verticalImageOffset));
            imageView.layout(
                    (int) leftMargin, (int) topMargin,
                    (int) (leftMargin + width * imageSize), (int) (topMargin + height * imageSize));

            // Layout TextView
            leftMargin = offset * width;
            topMargin = height * verticalTextPos;
            textView.layout(
                    (int) leftMargin, (int) topMargin,
                    (int) (leftMargin + width), (int) (topMargin + height * (1.0f - verticalTextPos)));

            rectView.layout(0, 0, width, 1200);
            Log.d("tag", "Heihgt " + height + " width " + width);
        }
    }
}