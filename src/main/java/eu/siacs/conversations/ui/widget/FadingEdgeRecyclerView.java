package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** A list whose content dissolves into its padding rather than sliding under a hard bar edge. */
public class FadingEdgeRecyclerView extends RecyclerView {

    private EdgeFade edgeFade;

    public FadingEdgeRecyclerView(@NonNull final Context context) {
        super(context);
    }

    public FadingEdgeRecyclerView(@NonNull final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public FadingEdgeRecyclerView(
            @NonNull final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        if (edgeFade == null) {
            edgeFade = new EdgeFade(this);
        }
        edgeFade.draw(canvas, this, super::draw);
    }
}
