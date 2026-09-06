package eu.siacs.conversations.ui.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import eu.siacs.conversations.R;

/**
 * The floating top and bottom bars sit on top of the content instead of taking space away from it.
 * These helpers keep the content usable underneath them: a scrolling view is padded so its first
 * and last items can be reached, and anchored views such as a floating action button are lifted
 * clear of the bottom bar.
 *
 * <p>Everything is measured from the bars themselves rather than from a fixed dimension, so it
 * follows a bar being hidden, a taller toolbar at large font sizes, rotation and multi window
 * without any further bookkeeping.
 */
public final class FloatingBars {

    private FloatingBars() {}

    /**
     * The floating top bar of {@code activity}: the container holding the toolbar and anything
     * stacked under it where a screen has one, otherwise the toolbar's app bar on its own.
     */
    @Nullable
    public static View topBarOf(@Nullable final Activity activity) {
        if (activity == null) {
            return null;
        }
        final View container = activity.findViewById(R.id.top_bar_container);
        return container == null ? activity.findViewById(R.id.app_bar) : container;
    }

    @Nullable
    public static View bottomBarOf(@Nullable final Activity activity) {
        return activity == null ? null : activity.findViewById(R.id.bottom_navigation);
    }

    /**
     * Pads {@code content} so it scrolls underneath the bars rather than stopping at them. Pass
     * null for a bar the screen does not have.
     */
    public static void inset(
            final View content, @Nullable final View topBar, @Nullable final View bottomBar) {
        if (content == null) {
            return;
        }
        if (content instanceof ViewGroup) {
            // so a scrolling child is drawn in the padding instead of stopping at it
            ((ViewGroup) content).setClipToPadding(false);
        }
        watch(
                content,
                () ->
                        setPaddingIfChanged(
                                content, occupiedHeight(topBar), occupiedHeight(bottomBar)));
    }

    /** Keeps {@code view} the same distance above the bottom bar as it had above the screen edge. */
    public static void liftAboveBottomBar(final View view, @Nullable final View bottomBar) {
        offset(view, bottomBar, false);
    }

    /** Keeps {@code view} the same distance below the top bar as it had below the screen edge. */
    public static void dropBelowTopBar(final View view, @Nullable final View topBar) {
        offset(view, topBar, true);
    }

    private static void offset(final View view, @Nullable final View bar, final boolean fromTop) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        final var initial = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        final int baseMargin = fromTop ? initial.topMargin : initial.bottomMargin;
        watch(
                view,
                () -> {
                    final var params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                    final int margin = baseMargin + occupiedHeight(bar);
                    if (fromTop && params.topMargin != margin) {
                        params.topMargin = margin;
                        view.setLayoutParams(params);
                    } else if (!fromTop && params.bottomMargin != margin) {
                        params.bottomMargin = margin;
                        view.setLayoutParams(params);
                    }
                });
    }

    /** The space a bar takes up at the edge of the screen, margins included. Zero when hidden. */
    private static int occupiedHeight(@Nullable final View bar) {
        if (bar == null || bar.getVisibility() == View.GONE) {
            return 0;
        }
        final var params = bar.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            final var margins = (ViewGroup.MarginLayoutParams) params;
            return bar.getHeight() + margins.topMargin + margins.bottomMargin;
        }
        return bar.getHeight();
    }

    private static void setPaddingIfChanged(final View view, final int top, final int bottom) {
        if (view.getPaddingTop() == top && view.getPaddingBottom() == bottom) {
            return;
        }
        // A list anchors its layout on where its children already are, not on the padding, so
        // growing the top padding underneath a list that is resting at the top would leave the
        // first row sitting behind the bar. Send it back to the top in that case.
        final boolean restingAtTop = !view.canScrollVertically(-1);
        view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), bottom);
        if (restingAtTop && view instanceof RecyclerView) {
            ((RecyclerView) view).scrollToPosition(0);
        }
    }

    /**
     * A bar that is hidden stops being laid out, so listening on the bar itself would miss exactly
     * the change we care about. Listening on the whole tree catches every case; the callbacks above
     * write only when a value actually changes, so this does not loop.
     */
    private static void watch(final View view, final Runnable apply) {
        // Straight away as well as on every layout: when the screen is already up, as it is when a
        // fragment comes back, the bars have real heights now and the content should be padded
        // before its first layout rather than corrected after it.
        apply.run();
        final var listener = (ViewTreeObserver.OnGlobalLayoutListener) apply::run;
        view.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(final View v) {
                        v.getViewTreeObserver().addOnGlobalLayoutListener(listener);
                    }

                    @Override
                    public void onViewDetachedFromWindow(final View v) {
                        v.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
                    }
                });
        if (view.isAttachedToWindow()) {
            view.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        }
    }
}
