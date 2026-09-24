package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import com.google.android.material.color.MaterialColors;

/**
 * Three dots bouncing in sequence; used to visualize an incoming
 * {@code <composing/>} chat state in the conversation overview.
 */
public class TypingIndicatorView extends View {

    private static final int DOTS = 3;
    private static final long CYCLE_DURATION = 1000L;
    private static final long DOT_OFFSET = 130L;
    private static final float BOUNCE_FRACTION = 0.45f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long startTime = 0;
    private boolean animating = false;

    public TypingIndicatorView(Context context) {
        super(context);
        init(context);
    }

    public TypingIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TypingIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(final Context context) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(
                MaterialColors.getColor(
                        context, androidx.appcompat.R.attr.colorPrimary, 0xff326130));
    }

    public void setColor(@ColorInt final int color) {
        if (paint.getColor() != color) {
            paint.setColor(color);
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimationState();
    }

    @Override
    protected void onDetachedFromWindow() {
        animating = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull final View changedView, final int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimationState();
    }

    @Override
    protected void onWindowVisibilityChanged(final int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimationState();
    }

    private void updateAnimationState() {
        final boolean shouldAnimate =
                ViewCompat.isAttachedToWindow(this)
                        && isShown()
                        && getWindowVisibility() == View.VISIBLE;
        if (shouldAnimate == animating) {
            return;
        }
        animating = shouldAnimate;
        if (animating) {
            startTime = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);
        final float width = getWidth() - getPaddingLeft() - getPaddingRight();
        final float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0) {
            return;
        }
        // dots plus a gap of one radius in between, and room to bounce
        final float radius = Math.min(width / (DOTS * 3f - 1f), height / 4f);
        final float gap = radius * 3f;
        final float bounce = radius;
        final float left =
                getPaddingLeft() + (width - ((DOTS - 1) * gap + 2 * radius)) / 2f + radius;
        final float centerY = getPaddingTop() + height / 2f;
        final long elapsed = animating ? SystemClock.uptimeMillis() - startTime : 0;
        for (int i = 0; i < DOTS; ++i) {
            final float progress = animating ? phase(elapsed - i * DOT_OFFSET) : 0f;
            paint.setAlpha(Math.round(255 * (0.4f + 0.6f * progress)));
            canvas.drawCircle(
                    left + i * gap,
                    centerY - bounce * progress,
                    radius * (0.8f + 0.2f * progress),
                    paint);
        }
        if (animating) {
            postInvalidateOnAnimation();
        }
    }

    /** A single half sine bump within the first {@link #BOUNCE_FRACTION} of every cycle. */
    private static float phase(final long elapsed) {
        final long time = ((elapsed % CYCLE_DURATION) + CYCLE_DURATION) % CYCLE_DURATION;
        final float progress = time / (float) CYCLE_DURATION;
        if (progress >= BOUNCE_FRACTION) {
            return 0f;
        }
        return (float) Math.sin(progress / BOUNCE_FRACTION * Math.PI);
    }
}
