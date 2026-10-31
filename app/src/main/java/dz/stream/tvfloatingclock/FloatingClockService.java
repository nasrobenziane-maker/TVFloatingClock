package dz.stream.tvfloatingclock;

import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
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
    private TextView dateText;
    private TextView wifiText;
    private WindowManager.LayoutParams params;
    private final Handler handler = new Handler();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (clockText != null) clockText.setText(timeFormat.format(new Date()));
            if (dateText != null) dateText.setText(dateFormat.format(new Date()));
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
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildOverlay();
        handler.post(ticker);
    }

    private void buildOverlay() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18, 12, 18, 12);
        box.setBackgroundColor(0xB8202020);

        clockText = new TextView(this);
        clockText.setTextColor(Color.WHITE);
        clockText.setTextSize(26);
        clockText.setGravity(Gravity.CENTER);
        box.addView(clockText);

        dateText = new TextView(this);
        dateText.setTextColor(0xFFE5E5E5);
        dateText.setTextSize(13);
        dateText.setGravity(Gravity.CENTER);
        box.addView(dateText);

        wifiText = new TextView(this);
        wifiText.setTextColor(0xFF8BE9FD);
        wifiText.setTextSize(13);
        wifiText.setGravity(Gravity.CENTER);
        box.addView(wifiText);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 30;
        params.y = 50;

        // Drag with mouse, touchscreen, or compatible remote pointer.
        box.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = startX + (int)(event.getRawX() - touchX);
                        params.y = startY + (int)(event.getRawY() - touchY);
                        try { windowManager.updateViewLayout(clockView, params); } catch (Exception ignored) {}
                        return true;
                    default:
                        return false;
                }
            }
        });

        clockView = box;
        try {
            windowManager.addView(clockView, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private String getWifiName() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || wifi.getConnectionInfo() == null) return "Wi-Fi: unavailable";
            String ssid = wifi.getConnectionInfo().getSSID();
            if (ssid == null || ssid.equals("<unknown ssid>")) return "Wi-Fi: name hidden by Android";
            ssid = ssid.replace("\"", "");
            return "Wi-Fi: " + ssid;
        } catch (Exception e) {
            return "Wi-Fi: unavailable";
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
