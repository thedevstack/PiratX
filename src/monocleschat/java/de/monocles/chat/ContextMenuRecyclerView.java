package de.monocles.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.View;
import android.view.ViewParent;
import android.widget.AdapterView.AdapterContextMenuInfo;

import eu.siacs.conversations.ui.widget.EdgeFade;

public class ContextMenuRecyclerView extends androidx.recyclerview.widget.RecyclerView {
	protected AdapterContextMenuInfo mAdapterContextMenuInfo = null;
	private EdgeFade edgeFade = null;

	public ContextMenuRecyclerView(Context context) {
		super(context);
	}

	public ContextMenuRecyclerView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ContextMenuRecyclerView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	public void draw(Canvas canvas) {
		// so chats dissolve into the floating bars instead of sliding under a hard edge
		if (edgeFade == null) {
			edgeFade = new EdgeFade(this);
		}
		edgeFade.draw(canvas, this, super::draw);
	}

	@Override
	protected ContextMenu.ContextMenuInfo getContextMenuInfo() {
		return mAdapterContextMenuInfo;
	}

	@Override
	public boolean showContextMenuForChild(View originalView) {
		mAdapterContextMenuInfo = new AdapterContextMenuInfo(
			originalView,
			getChildAdapterPosition(originalView),
			getChildItemId(originalView)
		);
		return super.showContextMenuForChild(originalView);
	}

	@Override
	public boolean showContextMenuForChild(View originalView, float x, float y) {
		final ViewParent parent = getParent();
		if (Float.isNaN(x) || Float.isNaN(y) || parent == null) {
			return super.showContextMenuForChild(originalView, x, y);
		}
		// A popup re-aligns to its anchor on every scroll and layout pass, and refreshes keep
		// moving rows around, so anchor the menu to the list itself (which stays put) at the
		// touch point, like ListView does, instead of to the row.
		mAdapterContextMenuInfo = new AdapterContextMenuInfo(
			originalView,
			getChildAdapterPosition(originalView),
			getChildItemId(originalView)
		);
		final int[] listPos = new int[2];
		final int[] rowPos = new int[2];
		getLocationInWindow(listPos);
		originalView.getLocationInWindow(rowPos);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return parent.showContextMenuForChild(
                this, x + rowPos[0] - listPos[0], y + rowPos[1] - listPos[1]);
        }
        return false;
    }
}
