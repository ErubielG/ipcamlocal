package com.ipcamlocal.app;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Contenedor del video con relación fija 16:9.
 *
 * VLCVideoLayout (LibVLC 3.7.6) fuerza su propio LayoutParams a MATCH_PARENT en
 * onAttachedToWindow(), ignorando el layout_height del XML. Por eso la altura del
 * video se decide aquí, en un único sitio, y no en VLCVideoLayout ni desde Java.
 */
public class AspectRatioFrameLayout extends FrameLayout {
    private static final float RATIO = 16f / 9f;

    public AspectRatioFrameLayout(Context context) {
        super(context);
    }

    public AspectRatioFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AspectRatioFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width / RATIO);

        // Nunca más alto que el espacio disponible (PiP, pantallas bajas).
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
        }

        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
