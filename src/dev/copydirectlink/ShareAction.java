package dev.copydirectlink;

import android.content.Context;

enum ShareAction {
    COPY(R.string.copy_link, R.string.copy_page_link),
    SHARE(R.string.share_link, R.string.share_page_link),
    SHIZUKU(R.string.share_with_shizuku, R.string.share_page_link);

    private static final String PREFERENCES = "settings";
    private static final String KEY = "share_action";

    final int label;
    final int description;

    ShareAction(int label, int description) {
        this.label = label;
        this.description = description;
    }

    static ShareAction load(Context context) {
        var preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String saved = preferences.getString(KEY, "");
        return switch (saved) {
            case "COPY" -> COPY;
            case "SHIZUKU" -> SHIZUKU;
            default -> SHARE;
        };
    }

    void save(Context context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, name()).apply();
    }
}
