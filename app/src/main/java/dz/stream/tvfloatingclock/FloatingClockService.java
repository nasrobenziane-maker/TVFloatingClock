package dz.stream.tvfloatingclock;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingClockService extends Service {
    private WindowManager windowManager;
    private View clockView;
    private TextView clockText;
    private TextView wifiText;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private boolean dragging = false;
    private final Handler handler = new Handler();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            applyStyle();
            if (clockText != null) clockText.setText(timeFormat.format(new Date()));
            if (wifiText != null) wifiText.setText(getWifiName());
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildOverlay();
        handler.post(ticker);
    }

    private void applyStyle() {
        if (clockText == null || wifiText == null || clockView == null) return;
        int size = prefs.getInt(MainActivity.KEY_SIZE, 26);
        int ci = prefs.getInt(MainActivity.KEY_COLOR, 0);
        if (ci < 0 || ci >= MainActivity.COLORS.length) ci = 0;
        int color = MainActivity.COLORS[ci];
        clockText.setTextSize(size);
        wifiText.setTextSize(Math.max(10f, size * 0.5f));
        int textAlpha = Math.max(10, Math.min(100,
                prefs.getInt(MainActivity.KEY_TEXT_ALPHA, 100))) * 255 / 100;
        int textColor = (textAlpha << 24) | (color & 0x00FFFFFF);
        clockText.setTextColor(textColor);
        wifiText.setTextColor(textColor);

        int bgPercent = prefs.getInt(MainActivity.KEY_BG, 72);
        bgPercent = Math.max(0, Math.min(100, bgPercent));
        int alpha = bgPercent * 255 / 100;
        clockView.setBackgroundColor((alpha << 24) | 0x202020);

        if (params != null && clockView != null && !dragging) {
            int corner = prefs.getInt(MainActivity.KEY_CORNER, -1);
            int x = prefs.getInt(MainActivity.KEY_X, 30);
            int y = prefs.getInt(MainActivity.KEY_Y, 50);
            if (corner >= 0 && corner <= 3 && clockView.getWidth() > 0) {
                android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                windowManager.getDefaultDisplay().getMetrics(metrics);
                int right = Math.max(30, metrics.widthPixels - clockView.getWidth() - 30);
                int bottom = Math.max(50, metrics.heightPixels - clockView.getHeight() - 50);
                x = (corner == 1 || corner == 3) ? right : 30;
                y = (corner == 2 || corner == 3) ? bottom : 50;
            }
            boolean remoteMove = prefs.getBoolean(MainActivity.KEY_REMOTE_MOVE, false);
            clockView.setFocusableInTouchMode(remoteMove);
            int wantedFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | (remoteMove ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            boolean flagsChanged = params.flags != wantedFlags;
            params.flags = wantedFlags;
            if (params.x != x || params.y != y || flagsChanged) {
                params.x = x;
                params.y = y;
                try {
                    windowManager.updateViewLayout(clockView, params);
                    if (remoteMove && flagsChanged) clockView.requestFocus();
                } catch (Exception ignored) {}
            }
        }
    }

    private void moveByRemote(int dx, int dy) {
        prefs.edit().putInt(MainActivity.KEY_CORNER, -1).apply();
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        params.x = Math.max(0, Math.min(metrics.widthPixels - clockView.getWidth(),
                params.x + dx));
        params.y = Math.max(0, Math.min(metrics.heightPixels - clockView.getHeight(),
                params.y + dy));
        prefs.edit().putInt(MainActivity.KEY_X, params.x)
                .putInt(MainActivity.KEY_Y, params.y).apply();
        try { windowManager.updateViewLayout(clockView, params); } catch (Exception ignored) {}
    }

    private void buildOverlay() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18, 12, 18, 12);

        clockText = new TextView(this);
        clockText.setGravity(Gravity.CENTER);
        box.addView(clockText);

        wifiText = new TextView(this);
        wifiText.setGravity(Gravity.CENTER);
        box.addView(wifiText);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | (prefs.getBoolean(MainActivity.KEY_REMOTE_MOVE, false)
                        ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt(MainActivity.KEY_X, 30);
        params.y = prefs.getInt(MainActivity.KEY_Y, 50);

        box.setFocusable(true);
        box.setFocusableInTouchMode(prefs.getBoolean(MainActivity.KEY_REMOTE_MOVE, false));
        box.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN
                    || !prefs.getBoolean(MainActivity.KEY_REMOTE_MOVE, false)) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: moveByRemote(-24, 0); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: moveByRemote(24, 0); return true;
                case KeyEvent.KEYCODE_DPAD_UP: moveByRemote(0, -24); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: moveByRemote(0, 24); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    prefs.edit().putBoolean(MainActivity.KEY_REMOTE_MOVE, false).apply();
                    applyStyle();
                    return true;
                default: return false;
            }
        });

        box.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        prefs.edit().putInt(MainActivity.KEY_CORNER, -1).apply();
                        dragging = true;
                        startX = params.x;
                        startY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = Math.max(0, startX + (int)(event.getRawX() - touchX));
                        params.y = Math.max(0, startY + (int)(event.getRawY() - touchY));
                        try { windowManager.updateViewLayout(clockView, params); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        prefs.edit()
                                .putInt(MainActivity.KEY_X, params.x)
                                .putInt(MainActivity.KEY_Y, params.y)
                                .apply();
                        dragging = false;
                        return true;
                    default:
                        return false;
                }
            }
        });

        clockView = box;
        applyStyle();
        try {
            windowManager.addView(clockView, params);
            if (prefs.getBoolean(MainActivity.KEY_REMOTE_MOVE, false)) clockView.requestFocus();
        } catch (Exception e) {
            stopSelf();
        }
    }

    private String getWifiName() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return "امنح إذن الموقع";
            }
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || wifi.getConnectionInfo() == null) return "غير متاح";
            String ssid = wifi.getConnectionInfo().getSSID();
            if (ssid == null || ssid.equals("<unknown ssid>")) {
                return "فعّل الموقع في التلفاز";
            }
            return ssid.replace("\"", "");
        } catch (Exception e) {
            return "غير متاح";
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        if (windowManager != null && clockView != null) {
            try { windowManager.removeView(clockView); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
