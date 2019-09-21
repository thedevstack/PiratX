package eu.siacs.conversations.ui.adapter;

import android.app.Activity;
import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Locale;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.SearchResultItemBinding;
import eu.siacs.conversations.http.services.MuclumbusService;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import rocks.xmpp.addr.Jid;

public class ChannelSearchResultAdapter extends ListAdapter<MuclumbusService.Room, ChannelSearchResultAdapter.ViewHolder> implements View.OnCreateContextMenuListener {

    private static final DiffUtil.ItemCallback<MuclumbusService.Room> DIFF = new DiffUtil.ItemCallback<MuclumbusService.Room>() {
        @Override
        public boolean areItemsTheSame(@NonNull MuclumbusService.Room a, @NonNull MuclumbusService.Room b) {
            return a.address != null && a.address.equals(b.address);
        }

        @Override
        public boolean areContentsTheSame(@NonNull MuclumbusService.Room a, @NonNull MuclumbusService.Room b) {
            return a.equals(b);
        }
    };
    private OnChannelSearchResultSelected listener;
    private MuclumbusService.Room current;

    public ChannelSearchResultAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        return new ViewHolder(DataBindingUtil.inflate(LayoutInflater.from(viewGroup.getContext()), R.layout.search_result_item, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        final MuclumbusService.Room searchResult = getItem(position);
        viewHolder.binding.name.setText(searchResult.getName());
        final String description = searchResult.getDescription();
        final String language = searchResult.getLanguage();
        if (TextUtils.isEmpty(description)) {
            viewHolder.binding.description.setVisibility(View.GONE);
        } else {
            viewHolder.binding.description.setText(description);
            viewHolder.binding.description.setVisibility(View.VISIBLE);
        }
        if (language == null || language.length() != 2) {
            viewHolder.binding.language.setVisibility(View.GONE);
        } else {
            viewHolder.binding.language.setText(language.toUpperCase(Locale.ENGLISH));
            viewHolder.binding.language.setVisibility(View.VISIBLE);
        }
        final Jid room = searchResult.getRoom();
        viewHolder.binding.room.setText(room != null ? room.asBareJid().toString() : "");
        AvatarWorkerTask.loadAvatar(searchResult, viewHolder.binding.avatar, R.dimen.avatar);
        final View root = viewHolder.binding.getRoot();
        root.setTag(searchResult);
        root.setOnClickListener(v -> listener.onChannelSearchResult(searchResult));
        root.setOnCreateContextMenuListener(this);
    }

    public void setOnChannelSearchResultSelectedListener(OnChannelSearchResultSelected listener) {
        this.listener = listener;
    }

    public MuclumbusService.Room getCurrent() {
        return this.current;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        final Activity activity = XmppActivity.find(v);
        final Object tag = v.getTag();
        if (activity != null && tag instanceof MuclumbusService.Room) {
            activity.getMenuInflater().inflate(R.menu.channel_item_context, menu);
            this.current = (MuclumbusService.Room) tag;
        }
    }


    public interface OnChannelSearchResultSelected {
        void onChannelSearchResult(MuclumbusService.Room result);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final SearchResultItemBinding binding;

        private ViewHolder(SearchResultItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
