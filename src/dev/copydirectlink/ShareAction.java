package dev.copydirectlink;

import android.content.Context;

enum ShareAction {
    COPY(R.string.copy_page_link),
    SHARE(R.string.share_page_link);

    private static final String PREFERENCES = "settings";
    private static final String KEY = "share_action";

    final int description;

    ShareAction(int description) {
        this.description = description;
    }

    static ShareAction load(Context context) {
        var preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        return COPY.name().equals(preferences.getString(KEY, null)) ? COPY : SHARE;
    }

    void save(Context context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, name()).apply();
    }
}
