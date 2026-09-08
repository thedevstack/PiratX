package de.monocles.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.View;
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
}
