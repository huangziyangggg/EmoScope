package com.example.emoscope;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 情绪时间轴适配器 — 支持日期分组头 + 记录条目双 ViewType。
 */
public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Object> items = new ArrayList<>(); // String (header) or String[] (item)
    private static final SimpleDateFormat SDF_DAY = new SimpleDateFormat("MM-dd", Locale.getDefault());
    private static final SimpleDateFormat SDF_FULL = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public void setItems(List<String[]> rawItems) {
        items.clear();
        if (rawItems.isEmpty()) {
            notifyDataSetChanged();
            return;
        }

        String today, yesterday;
        synchronized (SDF_DAY) { today = SDF_DAY.format(new Date()); }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
        synchronized (SDF_DAY) { yesterday = SDF_DAY.format(cal.getTime()); }

        String lastGroup = "";
        for (String[] row : rawItems) {
            String time = row[0];
            String dateKey = time.length() >= 5 ? time.substring(0, 5) : time;

            if (!dateKey.equals(lastGroup)) {
                // 生成日期标签
                String label;
                if (dateKey.equals(today)) label = "今天";
                else if (dateKey.equals(yesterday)) label = "昨天";
                else label = dateKey.substring(0, 2) + "月" + dateKey.substring(3, 5) + "日";
                items.add(label);
                lastGroup = dateKey;
            }
            items.add(row);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            TextView tv = (TextView) LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            tv.setPadding(20, 20, 20, 8);
            tv.setTextSize(13);
            tv.setTextColor(MaterialColors.getColor(tv, com.google.android.material.R.attr.colorPrimary, 0));
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            return new HeaderHolder(tv);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history_record, parent, false);
            return new ItemHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).tvLabel.setText((String) items.get(position));
        } else if (holder instanceof ItemHolder) {
            String[] row = (String[]) items.get(position);
            bindItem((ItemHolder) holder, row);
        }
    }

    private void bindItem(ItemHolder holder, String[] row) {
        String time = row[0];
        String type = row[1];
        String detail = row[2];
        boolean isPos = "1".equals(row[3]);

        // 只显示时间部分 HH:mm
        String timePart = time.length() >= 11 ? time.substring(6) : time;
        holder.tvTime.setText(timePart + "  ·  " + type);
        holder.tvDetail.setText(detail);

        int detailColor = MaterialColors.getColor(holder.itemView,
                isPos ? com.google.android.material.R.attr.colorOnSurface
                      : com.google.android.material.R.attr.colorError, 0);
        holder.tvDetail.setTextColor(detailColor);

        int dotColor = isPos ? holder.itemView.getContext().getColor(R.color.positive_green)
                : MaterialColors.getColor(holder.itemView,
                        com.google.android.material.R.attr.colorError, 0);
        holder.moodDot.getBackground().setTint(dotColor);
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── ViewHolders ──
    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView tvLabel;
        HeaderHolder(TextView v) { super(v); tvLabel = v; }
    }

    static class ItemHolder extends RecyclerView.ViewHolder {
        TextView tvTime, tvDetail;
        View moodDot;
        ItemHolder(View v) {
            super(v);
            tvTime = v.findViewById(R.id.tvHistoryTime);
            tvDetail = v.findViewById(R.id.tvHistoryDetail);
            moodDot = v.findViewById(R.id.moodDot);
        }
    }
}
