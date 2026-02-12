package de.thedevstack.piratx.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListPopupWindow;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.color.MaterialColors;

import de.thedevstack.logviewer.LogViewerActivity;
import eu.siacs.conversations.R;

public class PiratXLongClickAboutPreference extends Preference {
    private float lastTouchX;
    private float lastTouchY;

    public PiratXLongClickAboutPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                lastTouchX = event.getX();
                lastTouchY = event.getY();
            }
            return false; // Important to fire the long click
        });

        holder.itemView.setOnLongClickListener(v -> {
            showPopup(v);
            return true;
        });
    }

    private void showPopup(View anchor) {
        ListPopupWindow popup = new ListPopupWindow(getContext());

        TextView titleView = new TextView(getContext());
        titleView.setText(getSummary());
        titleView.setPadding(32, 24, 32, 24);
        titleView.setTextSize(14);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        int secondaryColor = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorSecondary, Color.BLACK);

        titleView.setTextColor(secondaryColor);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);

        popup.setPromptView(titleView);
        popup.setPromptPosition(ListPopupWindow.POSITION_PROMPT_ABOVE);

        String[] options = {
                getContext().getString(R.string.copy),
                getContext().getString(R.string.action_logs)
        };
        popup.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_list_item_1, options));

        popup.setAnchorView(anchor);
        popup.setHorizontalOffset((int) lastTouchX);

        popup.setVerticalOffset((int) lastTouchY - anchor.getHeight());

        popup.setWidth(500);
        popup.setModal(true);

        popup.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                copyToClipboard();
            } else if (position == 1) {
                showLogViewer(view);
            }
            popup.dismiss();
        });

        popup.show();
    }

    private void showLogViewer(View v) {
        Context context = v.getContext();
        Intent intent = new Intent(context, LogViewerActivity.class);

        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        context.startActivity(intent);
    }

    private void copyToClipboard() {
        CharSequence summary = getSummary();

        if (summary != null) {
            ClipboardManager clipboard = (ClipboardManager)
                    getContext().getSystemService(Context.CLIPBOARD_SERVICE);

            ClipData clip = ClipData.newPlainText("PiratX Version", summary);

            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);

                Toast.makeText(getContext(), getContext().getString(R.string.copied_to_clipboard, summary), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
