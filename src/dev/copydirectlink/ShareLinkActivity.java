package dev.copydirectlink;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Toast;

public final class ShareLinkActivity extends Activity {
    private static final String COPIED_AFTER = "copied_after";
    private static final String EXPIRES_AT = "expires_at";
    private static final long REQUEST_TIMEOUT_MS = 2500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expire = this::finish;

    static void open(Context context, long copiedAfter) {
        context.startActivity(new Intent(context, ShareLinkActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(COPIED_AFTER, copiedAfter)
                .putExtra(EXPIRES_AT, SystemClock.elapsedRealtime() + REQUEST_TIMEOUT_MS));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Android grants clipboard access to the foreground window once it has focus.
        setContentView(new View(this));
        long remaining = getIntent().getLongExtra(EXPIRES_AT, 0) - SystemClock.elapsedRealtime();
        if (savedInstanceState != null || remaining <= 0) {
            finish();
            return;
        }
        handler.postDelayed(expire, remaining);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || isFinishing()) {
            return;
        }
        handler.removeCallbacks(expire);

        long copiedAfter = getIntent().getLongExtra(COPIED_AFTER, 0);
        long expiresAt = getIntent().getLongExtra(EXPIRES_AT, 0);
        String url = copiedAfter > 0 && SystemClock.elapsedRealtime() < expiresAt
                ? readPageUrl(copiedAfter) : null;
        if (url == null) {
            Toast.makeText(this, R.string.share_link_unavailable, Toast.LENGTH_SHORT).show();
        } else {
            var send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url);
            try {
                startActivity(Intent.createChooser(send, null));
            } catch (ActivityNotFoundException unavailable) {
                Toast.makeText(this, R.string.share_sheet_unavailable, Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(expire);
        super.onDestroy();
    }

    private String readPageUrl(long copiedAfter) {
        var clipboard = getSystemService(ClipboardManager.class);
        var description = clipboard.getPrimaryClipDescription();
        if (description == null || description.getTimestamp() < copiedAfter) {
            return null;
        }

        var clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getDescription().getTimestamp() != description.getTimestamp()
                || clip.getItemCount() != 1) {
            return null;
        }
        CharSequence text = clip.getItemAt(0).getText();
        if (text == null) {
            return null;
        }
        String url = text.toString();
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                || host == null || host.isEmpty()
                || url.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            return null;
        }
        return url;
    }
}
