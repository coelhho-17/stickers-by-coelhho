package com.coelhho.sticker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Canvas 512x512 (exigência do WhatsApp). Mostra a imagem inteira ajustada
 * (fit) preservando a proporção, centralizada com fundo transparente. Em modo
 * borracha, apaga o fundo pintando com alpha transparente.
 */
public class EraseView extends View {

    private Bitmap bitmap;
    private Canvas bitmapCanvas;
    private final Paint erasePaint = new Paint();
    private boolean eraseMode = false;
    private float brushSize = 48f;
    private float lastX, lastY;

    public EraseView(Context context) {
        this(context, null);
    }

    public EraseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        erasePaint.setAntiAlias(true);
        erasePaint.setDither(true);
        erasePaint.setStyle(Paint.Style.STROKE);
        erasePaint.setStrokeCap(Paint.Cap.ROUND);
        erasePaint.setStrokeJoin(Paint.Join.ROUND);
        erasePaint.setStrokeWidth(brushSize);
        erasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void setImage(Bitmap src) {
        if (src == null) return;
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) return;

        // Mantém o formato original (fit com fundo transparente, sem cortar):
        // escala a imagem inteira para caber em 512x512 preservando a proporção
        // e centraliza. O arquivo continua 512x512 (exigência do WhatsApp), mas
        // a figurinha visível fica retangular, com margens transparentes.
        bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        bitmapCanvas = new Canvas(bitmap);
        float scale = Math.min(512f / sw, 512f / sh);
        int dw = Math.round(sw * scale);
        int dh = Math.round(sh * scale);
        int dx = (512 - dw) / 2;
        int dy = (512 - dh) / 2;
        bitmapCanvas.drawBitmap(src, null, new RectF(dx, dy, dx + dw, dy + dh), null);
        invalidate();
    }

    public boolean hasImage() {
        return bitmap != null;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setEraseMode(boolean on) {
        eraseMode = on;
    }

    public void setBrushSize(float px) {
        brushSize = px;
        erasePaint.setStrokeWidth(px);
    }

    public void clearImage() {
        if (bitmap != null) {
            bitmap.eraseColor(Color.TRANSPARENT);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.parseColor("#DADADA"));
        if (bitmap != null) {
            float scale = (float) getWidth() / 512f;
            canvas.save();
            canvas.scale(scale, scale);
            canvas.drawBitmap(bitmap, 0, 0, null);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!eraseMode || bitmap == null) return true;
        float scale = 512f / (float) getWidth();
        float x = e.getX() * scale;
        float y = e.getY() * scale;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = x;
                lastY = y;
                bitmapCanvas.drawPoint(x, y, erasePaint);
                break;
            case MotionEvent.ACTION_MOVE:
                bitmapCanvas.drawLine(lastX, lastY, x, y, erasePaint);
                lastX = x;
                lastY = y;
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }
}
