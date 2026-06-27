package com.example.emoscope;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
            // 统一处理两种日期格式: legacy "yyyy-MM-dd HH:mm" -> "MM-dd", current "MM-dd HH:mm" -> "MM-dd"
            String dateKey;
            if (time != null && time.length() >= 10 && time.charAt(4) == '-') {
                dateKey = time.substring(5, 10); // 从 yyyy-MM-dd 提取 MM-dd
            } else if (time != null && time.length() >= 5) {
                dateKey = time.substring(0, 5);  // 从 MM-dd 直接取
            } else {
                dateKey = (time != null) ? time : "";
            }

            if (!dateKey.equals(lastGroup)) {
                String label;
                if (dateKey.equals(today)) label = "✦ 今天";
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
            tv.setPadding(6, 12, 6, 10);
            tv.setTextSize(16);
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
        holder.tvDetail.setText(formatMoodTitle(detail, isPos));

        int dotColor = isPos ? holder.itemView.getContext().getColor(R.color.positive_green)
                : holder.itemView.getContext().getColor(R.color.warning_orange);
        holder.tvDetail.setTextColor(dotColor);
        holder.moodDot.setBackgroundResource(isPos
                ? R.drawable.bg_history_icon_positive
                : R.drawable.bg_history_icon_warning);
        holder.moodDot.setImageResource(isPos ? R.drawable.ic_mood_smile : R.drawable.ic_mood_angry);
        holder.moodDot.setColorFilter(dotColor);
        holder.moodStripe.setBackgroundColor(dotColor);
    }

    private String formatMoodTitle(String detail, boolean isPos) {
        if (detail == null || detail.trim().isEmpty()) {
            return isPos ? "心情：平静" : "心情：可以关注";
        }
        String compact = detail.replace('\n', ' ').trim();
        int moodIndex = compact.indexOf("心情:");
        if (moodIndex < 0) moodIndex = compact.indexOf("心情：");
        if (moodIndex >= 0) {
            String mood = compact.substring(moodIndex + 3).trim();
            int hashIndex = mood.indexOf('#');
            if (hashIndex >= 0) mood = mood.substring(0, hashIndex).trim();
            int noteIndex = mood.indexOf("备注");
            if (noteIndex >= 0) mood = mood.substring(0, noteIndex).trim();
            if (!mood.isEmpty()) return "心情：" + mood;
        }
        if (compact.length() > 18) compact = compact.substring(0, 18) + "...";
        return compact;
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
        ImageView moodDot;
        View moodStripe;
        ItemHolder(View v) {
            super(v);
            tvTime = v.findViewById(R.id.tvHistoryTime);
            tvDetail = v.findViewById(R.id.tvHistoryDetail);
            moodDot = v.findViewById(R.id.moodDot);
            moodStripe = v.findViewById(R.id.moodStripe);
        }
    }
}
