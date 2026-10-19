package com.ipcamlocal.app;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Gestos sobre el video:
 * - pellizcar: zoom (1x a 4x) alrededor del punto entre los dedos;
 * - arrastrar con zoom: mover la imagen;
 * - doble toque: quitar el zoom;
 * - un toque: acción externa (pantalla completa).
 *
 * Escala la vista del video (VLCVideoLayout); su contenedor la recorta. No toca LibVLC,
 * así que no afecta a la sesión RTSP ni a las fotos (que capturan el fotograma completo).
 */
final class VideoZoomController implements View.OnTouchListener {
    private static final float MAX_SCALE = 4f;

    private final View target;
    private final Runnable onSingleTap;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private float scale = 1f;
    private float tx;
    private float ty;

    VideoZoomController(Context context, View target, Runnable onSingleTap) {
        this.target = target;
        this.onSingleTap = onSingleTap;
        target.setPivotX(0f);
        target.setPivotY(0f);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float nuevo = Math.max(1f, Math.min(MAX_SCALE, scale * detector.getScaleFactor()));
                        float factor = nuevo / scale;
                        // Mantener fijo el punto que está bajo los dedos.
                        tx = detector.getFocusX() - (detector.getFocusX() - tx) * factor;
                        ty = detector.getFocusY() - (detector.getFocusY() - ty) * factor;
                        scale = nuevo;
                        aplicar();
                        return true;
                    }
                });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                VideoZoomController.this.onSingleTap.run();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                reset();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (scale <= 1f || scaleDetector.isInProgress() || e2.getPointerCount() > 1) return false;
                tx -= dx;
                ty -= dy;
                aplicar();
                return true;
            }
        });
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // Ambos detectores reciben todo: GestureDetector cancela el toque simple
        // cuando entra un segundo dedo, así que un pellizco no activa pantalla completa.
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    /** Quitar el zoom (al cambiar de tamaño: pantalla completa, PiP, desconectar). */
    void reset() {
        scale = 1f;
        tx = 0f;
        ty = 0f;
        aplicar();
    }

    private void aplicar() {
        // Con pivote en (0,0), la imagen escalada cubre el contenedor si la traslación
        // está entre (tamaño - tamaño * escala) y 0.
        float minX = target.getWidth() - target.getWidth() * scale;
        float minY = target.getHeight() - target.getHeight() * scale;
        tx = Math.max(minX, Math.min(0f, tx));
        ty = Math.max(minY, Math.min(0f, ty));
        target.setScaleX(scale);
        target.setScaleY(scale);
        target.setTranslationX(tx);
        target.setTranslationY(ty);
    }
}
