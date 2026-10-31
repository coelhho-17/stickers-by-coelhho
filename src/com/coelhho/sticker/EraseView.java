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
 * Canvas quadrado 512x512. Mostra a imagem centralizada (center-crop) e,
 * em modo borracha, apaga o fundo pintando com alpha transparente.
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
        int size = Math.min(sw, sh);
        if (size <= 0) return;
        int x = (sw - size) / 2;
        int y = (sh - size) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, size, size);

        bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        bitmapCanvas = new Canvas(bitmap);
        bitmapCanvas.drawBitmap(cropped, null, new RectF(0, 0, 512, 512), null);
        if (cropped != src) cropped.recycle();
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
