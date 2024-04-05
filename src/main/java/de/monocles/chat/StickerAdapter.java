package de.monocles.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import eu.siacs.conversations.R;

public class StickerAdapter extends BaseAdapter {
    private Context ctx;
    private final String[] filesNamesStickers;
    private final String[] filesPathsStickers;

    public StickerAdapter(Context ctx, String[] filesNamesStickers, String[] filesPathsStickers) {
        this.ctx = ctx;
        this.filesNamesStickers = filesNamesStickers;
        this.filesPathsStickers = filesPathsStickers;
    }

    @Override
    public int getCount() {
        if (filesNamesStickers != null) {
            return filesNamesStickers.length;
        } else {
            return 0;
        }
    }

    @Override
    public Object getItem(int pos) {
        return pos;
    }

    @Override
    public long getItemId(int pos) {
        return pos;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View v;
        if (convertView == null) {  // if it's not recycled, initialize some attributes
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(     Context.LAYOUT_INFLATER_SERVICE );
            v = inflater.inflate(R.layout.activity_gridview_stickers, parent, false);
        } else {
            v = (View) convertView;
        }
        ImageView image = (ImageView)v.findViewById(R.id.grid_item);
        Glide.with(ctx).load(filesPathsStickers[position]).into(image);
        return v;
    }


}