package com.example.emoscope;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;

/**
 * 统一的心情选择弹窗组件。
 * MainActivity 和 HistoryFragment 共用此组件，消除重复代码。
 */
public final class MoodDialogHelper {

    private MoodDialogHelper() {}

    /** 心情选择回调 */
    public interface MoodPickerCallback {
        void onMoodSelected(int index, String label, String tag, String note);
    }

    /**
     * 显示 2x4 emoji 心情选择弹窗。
     *
     * @param context   上下文
     * @param showTags  是否显示分类标签行
     * @param showNote  是否显示备注输入框
     * @param title     弹窗标题
     * @param callback  选择结果回调
     */
    public static void showMoodPicker(Context context, boolean showTags,
                                       boolean showNote, String title,
                                       MoodPickerCallback callback) {
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(32, 24, 32, 16);

        final int[] selectedIdx = {-1};

        // 2 行 x 4 列 emoji 网格
        for (int row = 0; row < 2; row++) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(android.view.Gravity.CENTER);
            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                LinearLayout item = new LinearLayout(context);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(android.view.Gravity.CENTER);
                item.setPadding(16, 8, 16, 8);
                item.setClickable(true);
                item.setBackgroundColor(0x00000000);

                ImageView icon = new ImageView(context);
                icon.setImageResource(Constants.MANUAL_MOOD_ICONS[idx]);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        (int) (40 * context.getResources().getDisplayMetrics().density),
                        (int) (40 * context.getResources().getDisplayMetrics().density));
                icon.setLayoutParams(iconParams);

                TextView label = new TextView(context);
                label.setText(Constants.MANUAL_MOOD_LABELS[idx]);
                label.setTextSize(12);
                label.setTextColor(MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                label.setGravity(android.view.Gravity.CENTER);
                label.setPadding(0, 4, 0, 0);

                int finalIdx = idx;
                item.setOnClickListener(v -> {
                    selectedIdx[0] = finalIdx;
                    for (int i = 0; i < grid.getChildCount(); i++) {
                        View child = grid.getChildAt(i);
                        if (child instanceof LinearLayout) {
                            LinearLayout r = (LinearLayout) child;
                            for (int j = 0; j < r.getChildCount(); j++) {
                                r.getChildAt(j).setBackgroundColor(0x00000000);
                            }
                        }
                    }
                    item.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context,
                            R.color.selection_highlight));
                });
                item.addView(icon);
                item.addView(label);
                rowLayout.addView(item, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
            grid.addView(rowLayout);
        }

        // 可选备注
        final EditText noteInput;
        if (showNote) {
            noteInput = new EditText(context);
            noteInput.setHint("添加备注（可选）");
            noteInput.setSingleLine(true);
            LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            noteParams.setMargins(16, 12, 16, 0);
            grid.addView(noteInput, noteParams);
        } else {
            noteInput = null;
        }

        // 可选标签
        final String[] selectedTag = {""};
        if (showTags) {
            LinearLayout tagRow = new LinearLayout(context);
            tagRow.setOrientation(LinearLayout.HORIZONTAL);
            tagRow.setPadding(16, 8, 16, 0);
            tagRow.setGravity(android.view.Gravity.CENTER);
            for (String tag : Constants.EMOTION_TAGS) {
                TextView tagView = new TextView(context);
                tagView.setText(tag);
                tagView.setTextSize(11);
                tagView.setPadding(12, 6, 12, 6);
                tagView.setTextColor(MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                tagView.setBackgroundResource(R.drawable.filter_pill);
                tagView.setClickable(true);
                tagView.setOnClickListener(v -> {
                    if (tag.equals(selectedTag[0])) {
                        selectedTag[0] = "";
                        tagView.setBackgroundResource(R.drawable.filter_pill);
                        tagView.setTextColor(MaterialColors.getColor(context,
                                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                    } else {
                        for (int i = 0; i < tagRow.getChildCount(); i++) {
                            tagRow.getChildAt(i).setBackgroundResource(R.drawable.filter_pill);
                            ((TextView) tagRow.getChildAt(i)).setTextColor(
                                    MaterialColors.getColor(context,
                                            com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                        }
                        selectedTag[0] = tag;
                        tagView.setBackgroundResource(R.drawable.filter_pill_selected);
                        tagView.setTextColor(MaterialColors.getColor(context,
                                com.google.android.material.R.attr.colorPrimary, 0));
                    }
                });
                LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                tagParams.setMargins(0, 0, 8, 0);
                tagRow.addView(tagView, tagParams);
            }
            grid.addView(tagRow);
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(grid)
                .setPositiveButton("保存", (dialog, which) -> {
                    if (selectedIdx[0] < 0) return;
                    String label = Constants.MANUAL_MOOD_LABELS[selectedIdx[0]];
                    String note = (noteInput != null) ? noteInput.getText().toString().trim() : "";
                    if (callback != null) {
                        callback.onMoodSelected(selectedIdx[0], label, selectedTag[0], note);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

}
