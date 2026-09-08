package eu.siacs.conversations.ui.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import eu.siacs.conversations.R;

/**
 * The floating bars sit on top of the content instead of taking space away from it. These helpers
 * keep the content usable underneath them: a scrolling view is padded so its first and last items
 * can still be reached, and anchored views such as a floating action button are held clear of
 * whatever floats at that edge.
 *
 * <p>An edge can have a stack of things floating at it — the toolbar, then a tab strip, then a
 * pinned message; or a snackbar above the composer — so the calls take as many bars as the edge
 * has and add up what they occupy. Everything is measured from the bars themselves rather than
 * from fixed dimensions, so it follows a bar being hidden, a taller toolbar at large font sizes,
 * the keyboard opening, rotation and multi window without any further bookkeeping.
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
     * null for an edge that has nothing floating at it.
     */
    public static void inset(
            final View content, @Nullable final View topBar, @Nullable final View bottomBar) {
        inset(content, new View[] {topBar}, new View[] {bottomBar});
    }

    /** As above, for an edge with more than one thing stacked at it. */
    public static void inset(
            final View content, final View[] topBars, final View[] bottomBars) {
        if (content == null) {
            return;
        }
        if (content instanceof ViewGroup) {
            // so a scrolling child is drawn in the padding instead of stopping at it
            ((ViewGroup) content).setClipToPadding(false);
        }
        final Runnable apply =
                () ->
                        setPaddingIfChanged(
                                content, occupiedHeight(topBars), occupiedHeight(bottomBars));
        watchBars(apply, topBars, bottomBars);
        watch(content, apply);
    }

    /** Keeps {@code view} the same distance above the bottom bars as it had above the edge. */
    public static void liftAboveBottomBar(final View view, final View... bottomBars) {
        offset(view, bottomBars, false);
    }

    /** Keeps {@code view} the same distance below the top bars as it had below the edge. */
    public static void dropBelowTopBar(final View view, final View... topBars) {
        offset(view, topBars, true);
    }

    private static void offset(final View view, final View[] bars, final boolean fromTop) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        final var initial = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        final int baseMargin = fromTop ? initial.topMargin : initial.bottomMargin;
        final Runnable apply =
                () -> {
                    final var params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                    final int margin = baseMargin + occupiedHeight(bars);
                    if (fromTop && params.topMargin != margin) {
                        params.topMargin = margin;
                        view.setLayoutParams(params);
                    } else if (!fromTop && params.bottomMargin != margin) {
                        params.bottomMargin = margin;
                        view.setLayoutParams(params);
                    }
                };
        watchBars(apply, bars);
        watch(view, apply);
    }

    /**
     * Follows the bars themselves. This is what keeps content from visibly jumping: a layout change
     * listener runs inside the layout pass, and anything that asks for layout from there is
     * re-measured in the same frame, so the first frame drawn already has the right insets. The
     * tree observer below only catches what this cannot — a bar being hidden stops it being laid
     * out at all.
     */
    private static void watchBars(final Runnable apply, final View[]... barGroups) {
        for (final View[] bars : barGroups) {
            for (final View bar : bars) {
                if (bar == null) {
                    continue;
                }
                bar.addOnLayoutChangeListener(
                        (v, l, t, r, b, oldL, oldT, oldR, oldB) -> apply.run());
            }
        }
    }

    /**
     * The space a stack of bars takes up at one edge, margins included. Bars that are hidden take
     * up nothing, which is what makes a bar appearing or disappearing self-correcting.
     */
    private static int occupiedHeight(final View... bars) {
        int total = 0;
        for (final View bar : bars) {
            if (bar == null || bar.getVisibility() == View.GONE) {
                continue;
            }
            total += bar.getHeight();
            final var params = bar.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                final var margins = (ViewGroup.MarginLayoutParams) params;
                total += margins.topMargin + margins.bottomMargin;
            }
        }
        return total;
    }

    private static void setPaddingIfChanged(final View view, final int top, final int bottom) {
        if (view.getPaddingTop() == top && view.getPaddingBottom() == bottom) {
            return;
        }
        // A list anchors its layout on where its children already are, not on the padding, so
        // changing the padding under a list that is resting against an edge would leave the row
        // at that edge stranded behind a bar. Send it back to the edge it was resting on: the
        // end first, because a chat rests at the newest message and grows from the bottom.
        //
        // Only once it has laid something out, though. A list with no children yet reports that
        // it cannot scroll either way, which is not the same as resting against an edge -- acting
        // on that would scroll a restored list away from wherever the user left it.
        final RecyclerView list = view instanceof RecyclerView ? (RecyclerView) view : null;
        final boolean settled = list != null && list.getChildCount() > 0;
        final boolean restingAtStart = settled && !view.canScrollVertically(-1);
        final boolean restingAtEnd = settled && !view.canScrollVertically(1);
        view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), bottom);
        if (restingAtStart && restingAtEnd) {
            // it does not scroll at all, so nothing of it can end up behind a bar
            return;
        }
        final var adapter = list == null ? null : list.getAdapter();
        if (restingAtEnd && adapter != null && adapter.getItemCount() > 0) {
            list.scrollToPosition(adapter.getItemCount() - 1);
        } else if (restingAtStart) {
            list.scrollToPosition(0);
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
