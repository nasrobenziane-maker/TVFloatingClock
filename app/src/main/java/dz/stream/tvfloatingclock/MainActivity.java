 package dz.stream.tvfloatingclock;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    static final String PREFS = "clock_prefs";
    static final String AUTO_START = "auto_start";

    private TextView status;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 24, 28, 24);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setBackgroundColor(0xFF101820);

        TextView title = new TextView(this);
        title.setText("TV Floating Clock");
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        layout.addView(title);

        TextView description = new TextView(this);
        description.setText("ساعة عائمة + التاريخ + اسم شبكة Wi-Fi");
        description.setTextSize(18);
        description.setTextColor(0xFFE0E6ED);
        description.setPadding(0, 12, 0, 18);
        layout.addView(description);

        Button enable = new Button(this);
        enable.setText("السماح بالعرض فوق التطبيقات");
        enable.setOnClickListener(v -> requestOverlayThenStart());
        layout.addView(enable);

        Button start = new Button(this);
        start.setText("تشغيل الساعة الآن");
        start.setOnClickListener(v -> startClock());
        layout.addView(start);

        Button stop = new Button(this);
        stop.setText("إيقاف الساعة");
        stop.setOnClickListener(v -> stopService(new Intent(this, FloatingClockService.class)));
        layout.addView(stop);

        CheckBox auto = new CheckBox(this);
        auto.setText("تشغيل تلقائيًا عند بدء التلفاز");
        auto.setTextColor(0xFFFFFFFF);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        auto.setChecked(prefs.getBoolean(AUTO_START, true));
        auto.setOnCheckedChangeListener((buttonView, checked) ->
                prefs.edit().putBoolean(AUTO_START, checked).apply());
        layout.addView(auto);

        status = new TextView(this);
        status.setTextColor(0xFFFFD166);
        status.setTextSize(14);
        status.setPadding(0, 16, 0, 0);
        status.setText("ملاحظة: يجب منح إذن العرض العائم من إعدادات Android.");
        layout.addView(status);

        setContentView(layout);
    }

    private void requestOverlayThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            startClock();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            status.setText(Settings.canDrawOverlays(this)
                    ? "إذن العرض العائم متاح. اضغط تشغيل الساعة."
                    : "لم يُمنح إذن العرض العائم بعد.");
        }
    }

    private void startClock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            status.setText("امنح إذن العرض العائم أولًا.");
            requestOverlayThenStart();
            return;
        }
        startService(new Intent(this, FloatingClockService.class));
        status.setText("تم طلب تشغيل الساعة. إذا لم تظهر، تحقق من أذونات التلفاز.");
