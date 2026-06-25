package com.example.emoscope;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.color.MaterialColors;

/**
 * 统一的心情选择弹窗。主入口和历史页都应通过此组件保持一致的保存校验与视觉反馈。
 */
public final class MoodDialogHelper {

    private MoodDialogHelper() {
    }

    public interface MoodPickerCallback {
        void onMoodSelected(int index, String label, String tag, String note);
    }

    public static void showMoodPicker(Context context, boolean showTags,
                                      boolean showNote, String title,
                                      MoodPickerCallback callback) {
        int horizontalPadding = dp(context, 24);
        int itemSpacing = dp(context, 8);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(horizontalPadding, dp(context, 8), horizontalPadding, dp(context, 4));
        scrollView.addView(content);

        TextView prompt = new TextView(context);
        prompt.setText(R.string.mood_picker_prompt);
        prompt.setTextSize(15);
        prompt.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        content.addView(prompt);

        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridParams.setMargins(0, dp(context, 12), 0, 0);
        content.addView(grid, gridParams);

        final int[] selectedIndex = {-1};
        for (int row = 0; row < 2; row++) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(android.view.Gravity.CENTER);
            for (int column = 0; column < 4; column++) {
                int index = row * 4 + column;
                LinearLayout item = new LinearLayout(context);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(android.view.Gravity.CENTER);
                item.setPadding(itemSpacing, itemSpacing, itemSpacing, itemSpacing);
                item.setMinimumHeight(dp(context, 72));
                item.setClickable(true);
                item.setContentDescription(Constants.MANUAL_MOOD_LABELS[index]);
                item.setBackgroundColor(0x00000000);

                ImageView icon = new ImageView(context);
                icon.setImageResource(Constants.MANUAL_MOOD_ICONS[index]);
                icon.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)));

                TextView label = new TextView(context);
                label.setText(Constants.MANUAL_MOOD_LABELS[index]);
                label.setTextSize(12);
                label.setGravity(android.view.Gravity.CENTER);
                label.setPadding(0, dp(context, 4), 0, 0);
                label.setTextColor(MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant, 0));

                int selectedMoodIndex = index;
                item.setOnClickListener(view -> {
                    selectedIndex[0] = selectedMoodIndex;
                    for (int rowIndex = 0; rowIndex < grid.getChildCount(); rowIndex++) {
                        LinearLayout gridRow = (LinearLayout) grid.getChildAt(rowIndex);
                        for (int childIndex = 0; childIndex < gridRow.getChildCount(); childIndex++) {
                            gridRow.getChildAt(childIndex).setBackgroundColor(0x00000000);
                        }
                    }
                    item.setBackgroundResource(R.drawable.filter_pill_selected);
                });
                item.addView(icon);
                item.addView(label);
                rowLayout.addView(item, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (row > 0) {
                rowParams.setMargins(0, itemSpacing, 0, 0);
            }
            grid.addView(rowLayout, rowParams);
        }

        TextView selectionHint = new TextView(context);
        selectionHint.setText(R.string.mood_picker_selection_required);
        selectionHint.setTextSize(12);
        selectionHint.setVisibility(View.GONE);
        selectionHint.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorError, 0));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.setMargins(0, dp(context, 8), 0, 0);
        content.addView(selectionHint, hintParams);

        final EditText noteInput;
        if (showNote) {
            noteInput = new EditText(context);
            noteInput.setHint(R.string.mood_picker_note_hint);
            noteInput.setSingleLine(true);
            LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            noteParams.setMargins(0, dp(context, 12), 0, 0);
            content.addView(noteInput, noteParams);
        } else {
            noteInput = null;
        }

        final String[] selectedTag = {""};
        if (showTags) {
            HorizontalScrollView tagScroll = new HorizontalScrollView(context);
            tagScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout tagRow = new LinearLayout(context);
            tagRow.setOrientation(LinearLayout.HORIZONTAL);
            tagRow.setPadding(0, dp(context, 12), 0, 0);
            for (String tag : Constants.EMOTION_TAGS) {
                TextView tagView = new TextView(context);
                tagView.setText(tag);
                tagView.setTextSize(11);
                tagView.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
                tagView.setClickable(true);
                tagView.setBackgroundResource(R.drawable.filter_pill);
                tagView.setTextColor(MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                tagView.setOnClickListener(view -> {
                    if (tag.equals(selectedTag[0])) {
                        selectedTag[0] = "";
                        tagView.setBackgroundResource(R.drawable.filter_pill);
                        tagView.setTextColor(MaterialColors.getColor(context,
                                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                        return;
                    }
                    for (int childIndex = 0; childIndex < tagRow.getChildCount(); childIndex++) {
                        TextView tagItem = (TextView) tagRow.getChildAt(childIndex);
                        tagItem.setBackgroundResource(R.drawable.filter_pill);
                        tagItem.setTextColor(MaterialColors.getColor(context,
                                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                    }
                    selectedTag[0] = tag;
                    tagView.setBackgroundResource(R.drawable.filter_pill_selected);
                    tagView.setTextColor(MaterialColors.getColor(context,
                            com.google.android.material.R.attr.colorPrimary, 0));
                });
                LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                tagParams.setMargins(0, 0, itemSpacing, 0);
                tagRow.addView(tagView, tagParams);
            }
            tagScroll.addView(tagRow);
            content.addView(tagScroll);
        }

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton(R.string.mood_picker_save, null)
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    if (!MoodSelectionPolicy.hasValidSelection(selectedIndex[0])) {
                        selectionHint.setVisibility(View.VISIBLE);
                        return;
                    }
                    String label = Constants.MANUAL_MOOD_LABELS[selectedIndex[0]];
                    String note = noteInput != null ? noteInput.getText().toString().trim() : "";
                    if (callback != null) {
                        callback.onMoodSelected(selectedIndex[0], label, selectedTag[0], note);
                    }
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
