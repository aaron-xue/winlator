package com.winlator.cmod.widget;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.contentdialog.DebugDialog;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.UnitUtils;
import com.winlator.cmod.math.Mathf;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

public class LogView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<String> lines = new ArrayList<>();
    private final float rowHeight = UnitUtils.dpToPx(30);
    private final float defaultTextSize = UnitUtils.dpToPx(16);
    private final float minScrollThumbSize = UnitUtils.dpToPx(6);
    private final PointF lastPoint = new PointF();
    private final PointF scrollPosition = new PointF();
    private final PointF scrollSize = new PointF();
    private boolean isActionDown = false;
    private static String fileName;
    private boolean scrollingHorizontally = false;
    private boolean scrollingVertically = false;
    private final Object lock = new Object();
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private float downX, downY;
    private int longPressedLineIndex = -1;
    private static final int LONG_PRESS_TIMEOUT = 500;
    private static final int LONG_PRESS_TOLERANCE = 20;

    public LogView(Context context) {
        this(context, null);
    }

    public LogView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LogView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public LogView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeScrollSize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0) return;
        
        synchronized (lock) {
            paint.setStyle(Paint.Style.FILL);

            if (lines.isEmpty()) {
                paint.setTextSize(UnitUtils.dpToPx(20));
                paint.setColor(0xffbdbdbd);
                String text = getContext().getString(R.string.no_items_to_display);
                float centerX = (width - paint.measureText(text)) * 0.5f;
                float centerY = (height - paint.getFontSpacing()) * 0.5f - paint.ascent();
                canvas.drawText(text, centerX, centerY, paint);
                return;
            }

            paint.setTextSize(defaultTextSize);
            float textHeight = paint.getFontSpacing();

            float rowY = -scrollPosition.y;
            
            
            for (int i = 0, count = lines.size(); i < count; i++) {
                if ((rowY + rowHeight) < 0 || rowY >= height) {
                    rowY += rowHeight;
                    continue;
                }

                paint.setColor((i % 2) != 0 ? 0xffe1f5fe : 0xffffffff);
                canvas.drawRect(-scrollPosition.x, rowY, width, rowY + rowHeight, paint);

                if (i == longPressedLineIndex) {
                    paint.setColor(0x33009688);
                    canvas.drawRect(-scrollPosition.x, rowY, width, rowY + rowHeight, paint);
                }

                paint.setColor(0xff212121);
                float centerY = (rowY - paint.ascent()) + (rowHeight - textHeight) * 0.5f;
                canvas.drawText(lines.get(i), -scrollPosition.x, centerY, paint);
                rowY += rowHeight;
            }
             
            drawScrollThumbs(canvas);
        }
    }

    private void drawScrollThumbs(Canvas canvas) {
        float scrollThumbX = getScrollThumbX();
        float scrollThumbY = getScrollThumbY();
        float scrollThumbWidth = getScrollThumbWidth();
        float scrollThumbHeight = getScrollThumbHeight();

        paint.setColor(0x33000000);
        float radius = minScrollThumbSize * 0.5f;

        canvas.drawRoundRect(scrollThumbX, getHeight() - minScrollThumbSize, scrollThumbX + scrollThumbWidth, getHeight(), radius, radius, paint);
        canvas.drawRoundRect(getWidth() - minScrollThumbSize, scrollThumbY, getWidth(), scrollThumbY + scrollThumbHeight, radius, radius, paint);
    }

    public float getScrollMaxLeft() {
        return Math.max(0, scrollSize.x - getWidth());
    }

    public float getScrollMaxTop() {
        return Math.max(0, scrollSize.y - getHeight());
    }

    public float getScrollThumbX() {
        float width = getWidth();
        if (scrollSize.x > 0 && scrollSize.x > width) return scrollPosition.x * (width / scrollSize.x);
        return -Float.MAX_VALUE;
    }

    public float getScrollThumbY() {
        float height = getHeight();
        if (scrollSize.y > 0 && scrollSize.y > height) return scrollPosition.y * (height / scrollSize.y);
        return -Float.MAX_VALUE;
    }

    public float getScrollThumbWidth() {
        float width = getWidth();
        if (scrollSize.x > 0 && scrollSize.x > width) {
            return Math.max(width - width * (getScrollMaxLeft() / scrollSize.x), minScrollThumbSize);
        }
        return 0;
    }

    public float getScrollThumbHeight() {
        float height = getHeight();
        if (scrollSize.y > 0 && scrollSize.y > height) {
            return Math.max(height - height * (getScrollMaxTop() / scrollSize.y), minScrollThumbSize);
        }
        return 0;
    }

    private void computeScrollSize() {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float maxWidth = 0;
        paint.setTextSize(defaultTextSize);
        for (int i = 0, count = lines.size(); i < count; i++) maxWidth = Math.max(paint.measureText(lines.get(i)), maxWidth);
        scrollSize.x = Math.max(maxWidth, width);
        scrollSize.y = Math.max(rowHeight * lines.size(), height);
        scrollPosition.set(0, getScrollMaxTop());
    }

    public void clear() {
        synchronized (lock) {
            lines.clear();
        }
        postInvalidate();
    }

    public void append(String line) {
        synchronized (lock) {
            lines.add("["+DateFormat.format("HH:mm:ss", System.currentTimeMillis())+"]  "+line.replace("\n", ""));
            computeScrollSize();
        }
    }

    public static void setFilename(String file) {
        fileName = file.substring(0, file.lastIndexOf("."));
    }

    public static File getLogFile(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String winlatorPath = sp.getString("winlator_path_uri", null);
        File logsDir;

        if (winlatorPath != null) {
            Uri winlatorUri = Uri.parse(winlatorPath);
            logsDir = new File(FileUtils.getFilePathFromUri(context, winlatorUri), "logs");
        }
        else {
            logsDir = new File(SettingsFragment.DEFAULT_WINLATOR_PATH, "logs");
        }

        if (!logsDir.exists())
            logsDir.mkdirs();

        String logFile = fileName.replaceAll("\\s", "_").toLowerCase() + "_" + DateFormat.format("yyyy-MM-dd_HH-mm-ss", new Date()) + ".txt";
        return new File(logsDir, logFile);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                lastPoint.set(event.getX(), event.getY());
                isActionDown = true;
                scrollingHorizontally = false;
                scrollingVertically = false;

                final float downYFinal = event.getY();
                longPressRunnable = () -> {
                    if (isActionDown && !scrollingHorizontally && !scrollingVertically) {
                        int lineIndex = getLineIndexAtY(downYFinal);
                        if (lineIndex >= 0) {
                            longPressedLineIndex = lineIndex;
                            invalidate();
                            copyLineToClipboard(lineIndex);
                        }
                    }
                };
                longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                break;
            case MotionEvent.ACTION_MOVE:
                if (isActionDown) {
                    float dx = event.getX() - lastPoint.x;
                    float dy = event.getY() - lastPoint.y;

                    if (Math.abs(event.getX() - downX) > LONG_PRESS_TOLERANCE || Math.abs(event.getY() - downY) > LONG_PRESS_TOLERANCE) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                    }

                    if (Math.abs(dx) > 10) scrollingHorizontally = true;
                    if (Math.abs(dy) > 10) scrollingVertically = true;

                    if (scrollingHorizontally) {
                        DebugDialog.setPaused(true);
                        scrollPosition.x = Mathf.clamp(scrollPosition.x - dx, 0, getScrollMaxLeft());
                        lastPoint.set(event.getX(), event.getY());
                        invalidate();
                    }

                    if (scrollingVertically) {
                        DebugDialog.setPaused(true);
                        scrollPosition.y = Mathf.clamp(scrollPosition.y - dy, 0, getScrollMaxTop());
                        lastPoint.set(event.getX(), event.getY());
                        invalidate();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                longPressHandler.removeCallbacks(longPressRunnable);
                DebugDialog.setPaused(false);
                isActionDown = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                longPressHandler.removeCallbacks(longPressRunnable);
                isActionDown = false;
                break;
        }

        return true;
    }

    private int getLineIndexAtY(float y) {
        float rowY = -scrollPosition.y;
        for (int i = 0, count = lines.size(); i < count; i++) {
            if (y >= rowY && y < rowY + rowHeight) {
                return i;
            }
            rowY += rowHeight;
        }
        return -1;
    }

    private void copyLineToClipboard(int lineIndex) {
        synchronized (lock) {
            if (lineIndex >= 0 && lineIndex < lines.size()) {
                String text = lines.get(lineIndex);
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("log", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
