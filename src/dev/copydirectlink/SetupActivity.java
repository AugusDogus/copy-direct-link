package dev.copydirectlink;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public final class SetupActivity extends Activity {
    private static final int SHIZUKU_PERMISSION_REQUEST = 1;
    private final Shizuku.OnBinderReceivedListener binderReceived = this::updateShizukuStatus;
    private final Shizuku.OnBinderDeadListener binderDead = this::updateShizukuStatus;
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (request, result) -> {
        if (request == SHIZUKU_PERMISSION_REQUEST) {
            updateShizukuStatus();
        }
    };
    private TextView status;
    private TextView shizukuStatus;
    private Button authorizeShizuku;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        scroll.addView(layout);
        layout.setOrientation(LinearLayout.VERTICAL);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            layout.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom);
            return insets;
        });

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        layout.addView(title);

        TextView description = new TextView(this);
        description.setText(R.string.service_description);
        description.setTextSize(16);
        description.setPadding(0, padding, 0, padding);
        layout.addView(description);

        status = new TextView(this);
        layout.addView(status);

        Button settings = new Button(this);
        settings.setText(R.string.open_settings);
        settings.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(settings);

        TextView actionLabel = new TextView(this);
        actionLabel.setText(R.string.share_action);
        actionLabel.setTextSize(18);
        actionLabel.setPadding(0, padding, 0, 0);
        layout.addView(actionLabel);

        var actions = new RadioGroup(this);
        ShareAction selected = ShareAction.load(this);
        for (ShareAction action : ShareAction.values()) {
            if (action == ShareAction.SHIZUKU && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                continue;
            }
            var option = actionOption(action);
            actions.addView(option);
            if (action == selected) {
                actions.check(option.getId());
            }
        }
        actions.setOnCheckedChangeListener((group, id) -> {
            View option = group.findViewById(id);
            if (option != null && option.getTag() instanceof ShareAction action) {
                action.save(this);
                updateShizukuStatus();
            }
        });
        layout.addView(actions);

        shizukuStatus = new TextView(this);
        layout.addView(shizukuStatus);
        authorizeShizuku = new Button(this);
        authorizeShizuku.setText(R.string.authorize_shizuku);
        authorizeShizuku.setOnClickListener(view -> {
            if (Shizuku.pingBinder()) {
                try {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
                } catch (IllegalStateException unavailable) {
                    updateShizukuStatus();
                }
            }
        });
        layout.addView(authorizeShizuku);
        setContentView(scroll);
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permissionResult);
    }

    private RadioButton actionOption(ShareAction action) {
        var button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(action.label);
        button.setTag(action);
        button.setMinimumHeight(Math.round(48 * getResources().getDisplayMetrics().density));
        button.setLayoutParams(new RadioGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return button;
    }

    @Override
    protected void onResume() {
        super.onResume();
        var manager = getSystemService(AccessibilityManager.class);
        var component = new ComponentName(this, CopyLinkService.class);
        boolean enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .stream().anyMatch(info -> {
                    var service = info.getResolveInfo().serviceInfo;
                    return component.equals(new ComponentName(service.packageName, service.name));
                });
        status.setText(enabled ? R.string.service_on : R.string.service_off);
        updateShizukuStatus();
    }

    private void updateShizukuStatus() {
        boolean selected = ShareAction.load(this) == ShareAction.SHIZUKU;
        shizukuStatus.setVisibility(selected ? View.VISIBLE : View.GONE);
        boolean running = selected && Shizuku.pingBinder();
        boolean authorized = running && ShizukuLinkReader.authorized();
        authorizeShizuku.setVisibility(running && !authorized ? View.VISIBLE : View.GONE);
        if (selected) {
            shizukuStatus.setText(authorized ? R.string.shizuku_ready
                    : running ? R.string.shizuku_permission_needed : R.string.shizuku_start_needed);
        }
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        Shizuku.removeRequestPermissionResultListener(permissionResult);
        super.onDestroy();
    }
}
