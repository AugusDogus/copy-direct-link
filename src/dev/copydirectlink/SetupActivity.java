package dev.copydirectlink;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SetupActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom);
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
        setContentView(layout);
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
    }
}
