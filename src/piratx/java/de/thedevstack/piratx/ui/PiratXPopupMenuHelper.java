package de.thedevstack.piratx.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.util.ConversationMenuConfigurator;

public class PiratXPopupMenuHelper {
    public interface MenuItemClickListener {
        void handleItemClick(MenuItem item);
    }
    public static void createAndShowPopupMenu(Context context, Activity activity, View anchorView, MenuItemClickListener itemClickListener,
                                              Conversation conversation, Editable inputText) {
        // 1. Inflate the popup menu layout (the container for the menu items)
        LayoutInflater inflater = LayoutInflater.from(context);
        View popupView = inflater.inflate(R.layout.popup_menu, null);

        // 2. Create the PopupWindow
        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true // true = Popup is "focusable" (can get focus and dismissed by clicking outside of the popup)
        );

        // 3. Find the menu item container and populate the attach menu to it
        LinearLayout menuContainer = popupView.findViewById(R.id.menu_container);
        Menu tmpMenu = new androidx.appcompat.view.menu.MenuBuilder(context);
        activity.getMenuInflater().inflate(R.menu.fragment_conversation, tmpMenu);
        MenuItem attachMenu = tmpMenu.findItem(R.id.action_attach_file);
        for (int i = 0; i < attachMenu.getSubMenu().size(); i++) {
            MenuItem item = attachMenu.getSubMenu().getItem(i);

            addMenuItem(menuContainer, item.getIcon(), item.getTitle().toString(), inflater, v -> {
                itemClickListener.handleItemClick(item);
                popupWindow.dismiss();
            });
        }
        ConversationMenuConfigurator.configureAttachmentMenu(conversation, tmpMenu, TextUtils.isEmpty(inputText));

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupHeight = popupView.getMeasuredHeight();
        anchorView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int anchorHeight = anchorView.getMeasuredHeight();

        popupWindow.showAsDropDown(anchorView, 0, -popupHeight-anchorHeight);
    }

    private static void addMenuItem(LinearLayout container, Drawable icon, String text, LayoutInflater inflater, View.OnClickListener listener) {
        View menuItemView = inflater.inflate(R.layout.popup_menu_item, container, false);

        ImageView iconView = menuItemView.findViewById(R.id.menu_icon);
        TextView textView = menuItemView.findViewById(R.id.menu_text);

        if (icon != null) {
            iconView.setImageDrawable(icon);
            iconView.setVisibility(View.VISIBLE);
        } else {
            iconView.setVisibility(View.GONE);
        }
        textView.setText(text);

        menuItemView.setOnClickListener(listener);

        container.addView(menuItemView);
    }
}
