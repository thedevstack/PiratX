package eu.siacs.conversations.ui.adapter;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.databinding.DataBindingUtil;
import androidx.core.graphics.ColorUtils;

import com.wefika.flowlayout.FlowLayout;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ContactBinding;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.ui.SettingsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;

public class ListItemAdapter extends ArrayAdapter<ListItem> {

    private static final float INACTIVE_ALPHA = 0.4684f;
    private static final float ACTIVE_ALPHA = 1.0f;
    protected static XmppActivity activity;
    private boolean showDynamicTags = false;
    private boolean showPresenceColoredNames = false;
    private OnTagClickedListener mOnTagClickedListener = null;
    protected int color = 0;
    protected boolean offline = false;
    private View.OnClickListener onTagTvClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (view instanceof TextView && mOnTagClickedListener != null) {
                TextView tv = (TextView) view;
                final String tag = tv.getText().toString();
                mOnTagClickedListener.onTagClicked(tag);
            }
        }
    };

    public ListItemAdapter(XmppActivity activity, List<ListItem> objects) {
        super(activity, 0, objects);
        this.activity = activity;
    }

    public void refreshSettings() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.showDynamicTags = preferences.getBoolean(SettingsActivity.SHOW_DYNAMIC_TAGS, activity.getResources().getBoolean(R.bool.show_dynamic_tags));
        this.showPresenceColoredNames = preferences.getBoolean("presence_colored_names", activity.getResources().getBoolean(R.bool.presence_colored_names));
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = activity.getLayoutInflater();
        ListItem item = getItem(position);
        ViewHolder viewHolder;
        if (view == null) {
            ContactBinding binding = DataBindingUtil.inflate(inflater, R.layout.contact, parent, false);
            viewHolder = ViewHolder.get(binding);
            view = binding.getRoot();
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        if (activity.xmppConnectionService != null && activity.xmppConnectionService.getAccounts().size() > 1) {
            viewHolder.inner.setBackgroundColor(item.getAccount().getColor(activity.isDarkTheme()));
        }

        view.setBackground(StyledAttributes.getDrawable(view.getContext(),R.attr.list_item_background));

        List<ListItem.Tag> tags = item.getTags(activity);
        if (tags.size() == 0 || !this.showDynamicTags) {
            viewHolder.tags.setVisibility(View.GONE);
        } else {
            viewHolder.tags.setVisibility(View.VISIBLE);
            viewHolder.tags.removeAllViewsInLayout();
            for (ListItem.Tag tag : tags) {
                TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag, viewHolder.tags, false);
                tv.setText(tag.getName());
                Drawable unwrappedDrawable = AppCompatResources.getDrawable(activity, R.drawable.rounded_tag);
                Drawable wrappedDrawable = DrawableCompat.wrap(unwrappedDrawable);
                DrawableCompat.setTint(wrappedDrawable, tag.getColor());
                tv.setBackgroundResource(R.drawable.rounded_tag);
                tv.setOnClickListener(this.onTagTvClick);
                viewHolder.tags.addView(tv);

            }
        }
        final Jid jid = item.getJid();
        if (jid != null) {
            viewHolder.jid.setVisibility(View.VISIBLE);
            viewHolder.jid.setText(IrregularUnicodeDetector.style(activity, jid));
        } else {
            viewHolder.jid.setVisibility(View.GONE);
        }
        if (activity.xmppConnectionService != null && activity.xmppConnectionService.multipleAccounts() && activity.xmppConnectionService.showOwnAccounts()) {
            viewHolder.account.setVisibility(View.VISIBLE);
            viewHolder.account.setText(item.getAccount().getJid().asBareJid());
        } else {
            viewHolder.account.setVisibility(View.GONE);
        }
        viewHolder.name.setText(item.getDisplayName());
        if (tags.size() != 0) {
            for (ListItem.Tag tag : tags) {
                offline = tag.getOffline() == 1;
                color = tag.getColor();
            }
        }
        if (offline || activity.xmppConnectionService != null && !activity.xmppConnectionService.hasInternetConnection()) {
            viewHolder.name.setTextColor(StyledAttributes.getColor(activity, R.attr.text_Color_Main));
            viewHolder.name.setAlpha(INACTIVE_ALPHA);
            viewHolder.jid.setAlpha(INACTIVE_ALPHA);
            viewHolder.avatar.setAlpha(INACTIVE_ALPHA);
            viewHolder.tags.setAlpha(INACTIVE_ALPHA);
        } else {
            if (showPresenceColoredNames) {
                viewHolder.name.setTextColor(color != 0 ? color : StyledAttributes.getColor(activity, R.attr.text_Color_Main));
            } else {
                viewHolder.name.setTextColor(StyledAttributes.getColor(activity, R.attr.text_Color_Main));
            }
            viewHolder.name.setAlpha(ACTIVE_ALPHA);
            viewHolder.jid.setAlpha(ACTIVE_ALPHA);
            viewHolder.avatar.setAlpha(ACTIVE_ALPHA);
            viewHolder.tags.setAlpha(ACTIVE_ALPHA);
        }
        if (activity.xmppConnectionService != null) {
            AvatarWorkerTask.loadAvatar(item, viewHolder.avatar, R.dimen.avatar);
        }
        if (item.getActive()) {
            viewHolder.activeIndicator.setVisibility(View.VISIBLE);
        } else {
            viewHolder.activeIndicator.setVisibility(View.GONE);
        }
        return view;
    }

    public void setOnTagClickedListener(OnTagClickedListener listener) {
        this.mOnTagClickedListener = listener;
    }

    public interface OnTagClickedListener {
        void onTagClicked(String tag);
    }

    private static class ViewHolder {
        private TextView name;
        private TextView jid;
        private TextView account;
        private ImageView avatar;
        private FlowLayout tags;
        private ImageView activeIndicator;
        private View inner;


        private ViewHolder() {
        }

        public static ViewHolder get(ContactBinding binding) {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.name = binding.contactDisplayName;
            viewHolder.jid = binding.contactJid;
            viewHolder.account = binding.account;
            if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("set_round_avatars", R.bool.set_round_avatars)) {
                viewHolder.avatar = binding.contactPhoto;
            } else {
                viewHolder.avatar = binding.contactPhotoSquare;
            }
                viewHolder.tags = binding.tags;
            viewHolder.activeIndicator = binding.userActiveIndicator;
            viewHolder.inner = binding.inner;
            binding.getRoot().setTag(viewHolder);
            return viewHolder;
        }
    }
}
