package dev.copydirectlink;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.Toast;

public final class CopyLinkService extends AccessibilityService {
    private static final String CHROME = "com.android.chrome";
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";
    private static final String SHARE_LINK = "Share link";
    private static final String TAG = "CopyDirectLink";
    private static final int REFRESH_DELAY_MS = 50;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::refreshOverlay;
    private WindowManager windows;
    private Button overlay;
    private Target target;
    private String shareLabel = SHARE_LINK;

    private record Target(int windowId, Rect bounds, AccessibilityNodeInfo title) {}

    @Override
    protected void onServiceConnected() {
        windows = getSystemService(WindowManager.class);
        shareLabel = googleShareLabel();
        scheduleRefresh();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        shareLabel = googleShareLabel();
        scheduleRefresh();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        CharSequence source = event.getPackageName();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && source != null
                && !TextUtils.equals(CHROME, source) && !TextUtils.equals(getPackageName(), source)) {
            removeOverlay();
            return;
        }
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED || TextUtils.equals(CHROME, source)) {
            scheduleRefresh();
        }
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(refresh);
        removeOverlay();
    }

    @Override
    public void onDestroy() {
        onInterrupt();
        super.onDestroy();
    }

    private void scheduleRefresh() {
        if (!handler.hasCallbacks(refresh)) {
            handler.postDelayed(refresh, REFRESH_DELAY_MS);
        }
    }

    private String googleShareLabel() {
        try {
            Resources resources = getPackageManager().getResourcesForApplication(GOOGLE);
            int id = resources.getIdentifier("ga_custom_tabs_share_description", "string", GOOGLE);
            return id == 0 ? SHARE_LINK : resources.getString(id);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException unavailable) {
            return SHARE_LINK;
        }
    }

    private Target findTarget() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && TextUtils.equals(getPackageName(), root.getPackageName())) {
            // Touching the overlay can make it the active accessibility window.
            root = null;
            for (AccessibilityWindowInfo window : getWindows()) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused()) {
                    root = window.getRoot();
                    break;
                }
            }
        }
        if (root == null || !TextUtils.equals(CHROME, root.getPackageName())) {
            return null;
        }

        var title = root.findAccessibilityNodeInfosByViewId(CHROME + ":id/title_url_container")
                .stream().filter(node -> node.isVisibleToUser() && node.isLongClickable()).findFirst().orElse(null);
        if (title == null) {
            return null;
        }
        Rect bounds = findShareBounds(root);
        return bounds == null ? null : new Target(root.getWindowId(), bounds, title);
    }

    private Rect findShareBounds(AccessibilityNodeInfo root) {
        for (var actions : root.findAccessibilityNodeInfosByViewId(CHROME + ":id/action_buttons")) {
            for (int index = 0; index < actions.getChildCount(); index++) {
                var button = actions.getChild(index);
                if (button == null || !button.isVisibleToUser() || !button.isClickable()) {
                    continue;
                }
                CharSequence label = button.getContentDescription();
                if (TextUtils.equals(shareLabel, label) || TextUtils.equals(SHARE_LINK, label)) {
                    var bounds = new Rect();
                    button.getBoundsInScreen(bounds);
                    if (!bounds.isEmpty()) {
                        return bounds;
                    }
                }
            }
        }
        return null;
    }

    private void refreshOverlay() {
        Target next = findTarget();
        if (next == null) {
            removeOverlay();
            return;
        }
        if (target != null && target.windowId() == next.windowId() && target.bounds().equals(next.bounds())) {
            return;
        }

        removeOverlay();
        Rect bounds = next.bounds();
        var button = new Button(this);
        button.setBackground(null);
        button.setContentDescription(getString(R.string.copy_page_link));
        button.setOnClickListener(view -> copyPageLink());

        var params = new WindowManager.LayoutParams(bounds.width(), bounds.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.setFitInsetsTypes(0);
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.x = bounds.left;
        params.y = bounds.top;
        params.setTitle(getString(R.string.copy_page_link));
        try {
            windows.addView(button, params);
        } catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException unavailable) {
            Log.w(TAG, "Could not place the Share overlay for Chrome window " + next.windowId(), unavailable);
            return;
        }
        overlay = button;
        target = next;
    }

    private void copyPageLink() {
        Target current = findTarget();
        if (target == null || current == null || target.windowId() != current.windowId()
                || !target.bounds().equals(current.bounds())) {
            scheduleRefresh();
            return;
        }
        if (!current.title().performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            Toast.makeText(this, R.string.copy_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void removeOverlay() {
        if (overlay != null) {
            windows.removeViewImmediate(overlay);
            overlay = null;
            target = null;
        }
    }
}
