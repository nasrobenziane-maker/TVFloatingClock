package dz.stream.tvfloatingclock;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    static final String PREFS = "clock_prefs";
    static final String AUTO_START = "auto_start";
    static final String KEY_X = "x";
    static final String KEY_Y = "y";
    static final String KEY_SIZE = "size";
    static final String KEY_COLOR = "color";
    static final String KEY_BG = "bg_percent";
    static final int[] COLORS = {
            0xFFFFFFFF, 0xFFFFEB3B, 0xFF00E5FF, 0xFF69F0AE, 0xFFFF9800, 0xFFFF5252
    };
    static final String[] COLOR_NAMES = {
            "أبيض", "أصفر", "سماوي", "أخضر", "برتقالي", "أحمر"
    };
    private static final int STEP = 20;

    private TextView status;
    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

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
        description.setText("ساعة عائمة + اسم الشبكة");
        description.setTextSize(18);
        description.setTextColor(0xFFE0E6ED);
        description.setPadding(0, 12, 0, 18);
        layout.addView(description);

        layout.addView(button("السماح بالعرض فوق التطبيقات", v -> requestOverlayThenStart()));
        layout.addView(button("السماح بإذن الموقع (لإظهار اسم الشبكة)", v -> requestLocation()));
        layout.addView(button("تشغيل الساعة الآن", v -> startClock()));
        layout.addView(button("إيقاف الساعة",
                v -> stopService(new Intent(this, FloatingClockService.class))));

        layout.addView(label("مكان النافذة"));
        layout.addView(row(
                button("◀", v -> move(-STEP, 0)),
                button("▶", v -> move(STEP, 0)),
                button("▲", v -> move(0, -STEP)),
                button("▼", v -> move(0, STEP))));

        layout.addView(label("حجم الخط ولونه"));
        layout.addView(row(
                button("A-", v -> changeSize(-2)),
                button("A+", v -> changeSize(2)),
                button("اللون", v -> nextColor())));

        layout.addView(label("شفافية الخلفية"));
        layout.addView(row(
                button("أكثر شفافية", v -> changeBg(-10)),
                button("أقل شفافية", v -> changeBg(10))));

        CheckBox auto = new CheckBox(this);
        auto.setText("تشغيل تلقائيًا عند بدء التلفاز");
        auto.setTextColor(0xFFFFFFFF);
        auto.setChecked(prefs.getBoolean(AUTO_START, true));
        auto.setOnCheckedChangeListener((buttonView, checked) ->
                prefs.edit().putBoolean(AUTO_START, checked).apply());
        layout.addView(auto);

        status = new TextView(this);
        status.setTextColor(0xFFFFD166);
        status.setTextSize(14);
        status.setPadding(0, 16, 0, 0);
        layout.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF101820);
        scroll.addView(layout);
        setContentView(scroll);
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        return b;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFE0E6ED);
        t.setTextSize(16);
        t.setPadding(0, 16, 0, 4);
        return t;
    }

    private LinearLayout row(Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button b : buttons) {
            row.addView(b, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private void move(int dx, int dy) {
        int x = Math.max(0, prefs.getInt(KEY_X, 30) + dx);
        int y = Math.max(0, prefs.getInt(KEY_Y, 50) + dy);
        prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply();
        refreshStatus();
    }

    private void changeSize(int delta) {
        int size = prefs.getInt(KEY_SIZE, 26) + delta;
        size = Math.max(10, Math.min(120, size));
        prefs.edit().putInt(KEY_SIZE, size).apply();
        refreshStatus();
    }

    private void changeBg(int delta) {
        int bg = prefs.getInt(KEY_BG, 72) + delta;
        bg = Math.max(0, Math.min(100, bg));
        prefs.edit().putInt(KEY_BG, bg).apply();
        refreshStatus();
    }

    private void nextColor() {
        int c = (prefs.getInt(KEY_COLOR, 0) + 1) % COLORS.length;
        prefs.edit().putInt(KEY_COLOR, c).apply();
        refreshStatus();
    }

    private void requestLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            status.setText("إذن الموقع ممنوح بالفعل.");
        }
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
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean overlay = true;
        boolean loc = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlay = Settings.canDrawOverlays(this);
            loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        int ci = prefs.getInt(KEY_COLOR, 0);
        if (ci < 0 || ci >= COLORS.length) ci = 0;
        status.setText((overlay ? "العرض العائم: ✔" : "العرض العائم: ✘")
                + "   |   " + (loc ? "الموقع: ✔" : "الموقع: ✘")
                + "\nالحجم: " + prefs.getInt(KEY_SIZE, 26)
                + "   |   اللون: " + COLOR_NAMES[ci]
                + "\nالخلفية: " + prefs.getInt(KEY_BG, 72) + "%"
                + "   |   X: " + prefs.getInt(KEY_X, 30)
                + "   Y: " + prefs.getInt(KEY_Y, 50));
    }

    private void startClock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            status.setText("امنح إذن العرض العائم أولًا.");
            requestOverlayThenStart();
            return;
        }
        startService(new Intent(this, FloatingClockService.class));
        refreshStatus();
    }
}
