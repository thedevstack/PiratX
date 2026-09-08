package eu.siacs.conversations.ui.util;

import android.app.Activity;
import android.content.ContextWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
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
        watch(
                content,
                () ->
                        setPaddingIfChanged(
                                content, occupiedHeight(topBars), occupiedHeight(bottomBars)));
    }

    /**
     * Lets the window's content run behind the system bars, so a chat wallpaper and the messages
     * on it reach the top and bottom edges of the display instead of stopping at a band of window
     * background. The floating bars are held clear of the system bars one by one instead, by {@link
     * #belowStatusBar(View)} and {@link #aboveNavigationBar(View)}.
     *
     * <p>The content keeps only the part of the keyboard that stands above the navigation bar. The
     * rest of that inset is the navigation bar itself, which the composer already holds itself
     * clear of, and which the messages are meant to pass behind — so the composer still rides up
     * with the keyboard exactly as it did when the window was inset as a whole.
     *
     * <p>Stable insets throughout: they report a bar's size whether or not it is showing at that
     * moment, which is both what keeps the reading measured against the keyboard alone, and what
     * makes the sizes readable on older releases, where a window laid out fullscreen reports no
     * system window inset at the top at all.
     */
    public static void behindSystemBars(final Activity activity) {
        final View content = activity == null ? null : activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                content,
                (v, insets) -> {
                    final int keyboard =
                            Math.max(
                                    0,
                                    insets.getSystemWindowInsets().bottom
                                            - insets.getStableInsets().bottom);
                    if (v.getPaddingBottom() != keyboard) {
                        v.setPadding(
                                v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), keyboard);
                    }
                    // handed on rather than consumed: every bar below reads them for itself
                    return insets;
                });
        ViewCompat.requestApplyInsets(content);
    }

    /** Holds a floating top bar clear of the status bar the content now runs behind. */
    public static void belowStatusBar(final View topBar) {
        if (topBar == null) {
            return;
        }
        final int basePadding = topBar.getPaddingTop();
        onInsets(
                topBar,
                (v, insets) -> {
                    final int top = basePadding + insets.getStableInsets().top;
                    if (v.getPaddingTop() != top) {
                        v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
                    }
                });
    }

    /**
     * Holds a floating bottom bar clear of the navigation bar the content now runs behind. Applied
     * as a margin so that {@link #inset} and {@link #liftAboveBottomBar} count it as part of what
     * the bar occupies, the same way they count the margin it floats on.
     */
    public static void aboveNavigationBar(final View view) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        final int baseMargin =
                ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).bottomMargin;
        onInsets(
                view,
                (v, insets) -> {
                    final var params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                    final int margin = baseMargin + insets.getStableInsets().bottom;
                    if (params.bottomMargin != margin) {
                        params.bottomMargin = margin;
                        v.setLayoutParams(params);
                    }
                });
    }

    private interface OnInsets {
        void apply(View view, WindowInsetsCompat insets);
    }

    /**
     * Sizes a bar against the window insets, now and whenever they change.
     *
     * <p>Now matters as much as the listener does. A view is dispatched insets once it is attached,
     * which for anything inflated later — a chat's composer, say — is after it has already been
     * laid out and drawn once at the wrong height. So the window is asked for its insets directly,
     * through the activity's decor rather than through {@code view}, which has none of its own
     * until it is attached. A request on attach covers the case where the window itself has not
     * been measured yet and there is nothing to read.
     */
    private static void onInsets(final View view, final OnInsets apply) {
        final var current = windowInsets(view);
        if (current != null) {
            apply.apply(view, current);
        }
        ViewCompat.setOnApplyWindowInsetsListener(
                view,
                (v, insets) -> {
                    apply.apply(v, insets);
                    // handed on rather than consumed: every other bar reads them for itself
                    return insets;
                });
        view.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(final View v) {
                        ViewCompat.requestApplyInsets(v);
                    }

                    @Override
                    public void onViewDetachedFromWindow(final View v) {}
                });
        ViewCompat.requestApplyInsets(view);
    }

    @Nullable
    private static WindowInsetsCompat windowInsets(final View view) {
        final var own = ViewCompat.getRootWindowInsets(view);
        if (own != null) {
            return own;
        }
        var context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return ViewCompat.getRootWindowInsets(
                        ((Activity) context).getWindow().getDecorView());
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
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
        watch(view, apply);
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
        // at that edge stranded behind a bar. LinearLayoutManager only ever closes a gap at an
        // edge, never an overhang past one, so growing the padding under a settled list is not
        // something it corrects on its own. Send the list back to the edge it was resting on.
        final RecyclerView list = view instanceof RecyclerView ? (RecyclerView) view : null;
        final boolean restingAtStart = restingAtEdge(list, true);
        final boolean restingAtEnd = restingAtEdge(list, false);
        view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), bottom);
        if (!restingAtStart && !restingAtEnd) {
            return;
        }
        // A list that fits in one screen rests against both edges at once, so the tie goes to the
        // edge it stacks from: that is the one it holds its content against.
        final var adapter = list.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0) {
            return;
        }
        if (restingAtEnd && (!restingAtStart || stacksFromEnd(list))) {
            list.scrollToPosition(adapter.getItemCount() - 1);
        } else if (restingAtStart) {
            list.scrollToPosition(0);
        }
    }

    /**
     * Whether {@code list} is resting against one of its padded edges, measured from where the row
     * at that edge actually sits.
     *
     * <p>{@code canScrollVertically} would be the obvious question to ask instead, but it is
     * answered from the scrollbar estimate, which extrapolates the whole list from the average
     * height of the rows on screen. That is close enough to size a scrollbar and nowhere near
     * exact in a chat, where one row is a line of text and the next is a full width image, so it
     * reports a list that is against the bottom as still scrollable often enough to strand the
     * newest message behind the composer.
     *
     * <p>A list with nothing laid out yet is resting against neither edge: it has no row at an edge
     * to be measured, and treating it as settled would scroll a restored list away from wherever
     * the reader left it.
     */
    private static boolean restingAtEdge(@Nullable final RecyclerView list, final boolean start) {
        if (list == null || list.getChildCount() == 0) {
            return false;
        }
        final var adapter = list.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0) {
            return false;
        }
        final View row = childAtPosition(list, start ? 0 : adapter.getItemCount() - 1);
        if (row == null) {
            return false;
        }
        return start
                ? row.getTop() >= list.getPaddingTop()
                : row.getBottom() <= list.getHeight() - list.getPaddingBottom();
    }

    @Nullable
    private static View childAtPosition(final RecyclerView list, final int position) {
        for (int i = 0; i < list.getChildCount(); ++i) {
            final View child = list.getChildAt(i);
            if (list.getChildAdapterPosition(child) == position) {
                return child;
            }
        }
        return null;
    }

    private static boolean stacksFromEnd(final RecyclerView list) {
        final var manager = list.getLayoutManager();
        return manager instanceof LinearLayoutManager
                && ((LinearLayoutManager) manager).getStackFromEnd();
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
