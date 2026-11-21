package dev.copydirectlink;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

final class PageLinkShare {
    private static final int TIMEOUT_MS = 4000;
    private static final int RETRY_DELAY_MS = 40;
    private static final String URL_WRAPPER = "page_info_url_wrapper";

    private final CopyLinkService service;
    private final Handler handler;
    private Request pending;

    private record Request(int tabWindowId, long deadline) {}

    PageLinkShare(CopyLinkService service, Handler handler) {
        this.service = service;
        this.handler = handler;
    }

    boolean isPending() {
        return pending != null;
    }

    void open(AccessibilityNodeInfo tab) {
        var siteInfo = findView(tab, "security_button");
        if (siteInfo == null || !siteInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            fail(R.string.share_link_unavailable);
            return;
        }
        pending = new Request(tab.getWindowId(), SystemClock.uptimeMillis() + TIMEOUT_MS);
        handler.postDelayed(this::awaitSiteInfo, this, 0);
    }

    void cancel() {
        handler.removeCallbacksAndMessages(this);
        pending = null;
    }

    private void awaitSiteInfo() {
        var root = activeRoot();
        var wrapper = findView(root, URL_WRAPPER);
        if (wrapper == null) {
            retry(this::awaitSiteInfo);
        } else if (wrapper.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            awaitAddress(root.getWindowId());
        } else {
            fail(R.string.share_link_unavailable);
        }
    }

    private void awaitAddress(int panelWindowId) {
        var root = activeRoot();
        if (root == null) {
            retry(() -> awaitAddress(panelWindowId));
            return;
        }
        if (root.getWindowId() != panelWindowId) {
            cancel();
            service.refreshAfterShare();
            return;
        }
        var address = findView(root, "page_info_url");
        if (address == null) {
            retry(() -> awaitAddress(panelWindowId));
            return;
        }
        String url = webAddress(address.getText());
        var close = findView(root, "page_info_close");
        boolean dismissed = close != null
                ? close.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                : service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        if (url == null || !dismissed) {
            fail(R.string.share_link_unavailable);
            return;
        }
        // Wait for Chrome's dialog to close before Android can receive the share intent.
        awaitTab(url, panelWindowId);
    }

    private void awaitTab(String url, int panelWindowId) {
        var root = activeRoot();
        if (root == null || root.getWindowId() != pending.tabWindowId()
                || (root.getWindowId() == panelWindowId && findView(root, URL_WRAPPER) != null)) {
            retry(() -> awaitTab(url, panelWindowId));
            return;
        }
        cancel();
        var send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url);
        try {
            service.startActivity(Intent.createChooser(send, null)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            service.refreshAfterShare();
        } catch (ActivityNotFoundException unavailable) {
            fail(R.string.share_sheet_unavailable);
        }
    }

    private AccessibilityNodeInfo activeRoot() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? service.getRootInActiveWindow(0) : service.getRootInActiveWindow();
    }

    private void retry(Runnable step) {
        if (SystemClock.uptimeMillis() < pending.deadline()) {
            handler.postDelayed(step, this, RETRY_DELAY_MS);
        } else {
            fail(R.string.share_link_unavailable);
        }
    }

    private void fail(int message) {
        cancel();
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show();
        service.refreshAfterShare();
    }

    private static AccessibilityNodeInfo findView(AccessibilityNodeInfo root, String id) {
        if (root == null || !TextUtils.equals(CopyLinkService.CHROME, root.getPackageName())) {
            return null;
        }
        return root.findAccessibilityNodeInfosByViewId(CopyLinkService.CHROME + ":id/" + id)
                .stream().filter(AccessibilityNodeInfo::isVisibleToUser).findFirst().orElse(null);
    }

    private static String webAddress(CharSequence text) {
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
