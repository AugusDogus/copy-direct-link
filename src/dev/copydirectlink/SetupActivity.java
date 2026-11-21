package dev.copydirectlink;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
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

public final class SetupActivity extends Activity {
    private TextView status;

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
        var copy = actionOption(R.string.copy_link);
        var share = actionOption(R.string.share_link);
        actions.addView(copy);
        actions.addView(share);
        actions.check(ShareAction.load(this) == ShareAction.SHARE ? share.getId() : copy.getId());
        actions.setOnCheckedChangeListener((group, id) -> {
            if (id == copy.getId()) {
                ShareAction.COPY.save(this);
            } else if (id == share.getId()) {
                ShareAction.SHARE.save(this);
            }
        });
        layout.addView(actions);

        setContentView(scroll);
    }

    private RadioButton actionOption(int label) {
        var button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(label);
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
    }
}
