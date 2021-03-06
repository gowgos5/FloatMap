package com.gowgos5.floatmap;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@TargetApi(26)
public class MapService extends AccessibilityService {
    private static final String TAG = MapService.class.getSimpleName();

    public static final String ACTION_START_MAP_SERVICE = "START_MAP_SERVICE";
    public static final String ACTION_STOP_MAP_SERVICE = "STOP_MAP_SERVICE";
    public static final String ACTION_TOGGLE_OVERLAY_MAP_SERVICE = "ACTION_TOGGLE_OVERLAY_MAP_SERVICE";
    public static final String ACTION_TOGGLE_WAKELOCK_MAP_SERVICE = "ACTION_TOGGLE_WAKELOCK_MAP_SERVICE";

    private static final String BASE_MAP_URL = "https://www.google.com/maps/dir/?api=1";
    private static final String ALLOWED_URI_CHARS = "@#&=*+-_.,:!?()/~'%";

    private SharedPreferences mPreferences;
    private Notification mNotification;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mButtonParams;
    private WindowManager.LayoutParams mMapParams;
    private WindowManager.LayoutParams mOverlayParams;
    private ImageView mButton;
    private WebView mMap;
    private ConstraintLayout mOverlay;
    private PowerManager.WakeLock mWakeLock;

    private ArrayList<Pair<String, CharSequence>> mNodesList;
    private Pair<String, String> mAddresses;
    private String mMapUrl = "https://www.google.com/maps/place/Singapore";

    private void createNotification() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("MAP_SERVICE_ID",
                    "FloatMap", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Keep MapService alive");

            // Register the channel with the system
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }

        // Set up notification
        mNotification = new NotificationCompat.Builder(this, "MAP_SERVICE_ID")
                .setSmallIcon(R.drawable.ic_map)
                .setContentTitle("FloatMap")
                .setContentText("MapService is running.")
                .setOngoing(true)
                .setPriority(0)
                .setAutoCancel(false)
                .setContentIntent(null)
                .addAction(R.drawable.ic_overlay, "Toggle overlay",
                        PendingIntent.getService(this, 1,
                                new Intent(this, MapService.class)
                                        .setAction(ACTION_TOGGLE_OVERLAY_MAP_SERVICE), 0))
                .addAction(R.drawable.ic_lock, "Toggle wakelock",
                        PendingIntent.getService(this, 2,
                                new Intent(this, MapService.class)
                                        .setAction(ACTION_TOGGLE_WAKELOCK_MAP_SERVICE), 0))
                .addAction(R.drawable.ic_close, "Exit",
                        PendingIntent.getService(this, 3,
                                new Intent(this, MapService.class)
                                        .setAction(ACTION_STOP_MAP_SERVICE), 0))
                .build();
    }

    private void createFloatingButton() {
        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        mButtonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mButtonParams.gravity = Gravity.CENTER;
        mButtonParams.x = 0;
        mButtonParams.y = 0;
        mButtonParams.width = 120;
        mButtonParams.height = 120;

        mButton = new ImageView(this);
        mButton.setImageResource(R.drawable.btn_map);
        //noinspection AndroidLintClickableViewAccessibility
        mButton.setOnTouchListener(new View.OnTouchListener() {
            private static final int MAX_CLICK_DURATION = 1000;
            private static final int MAX_CLICK_DISTANCE = 15;

            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long pressStartTime;
            private boolean stayedWithinClickDistance;

            private float distance(float x1, float y1, float x2, float y2) {
                float dx = x1 - x2;
                float dy = y1 - y2;
                float distanceInPx = (float) Math.sqrt(dx * dx + dy * dy);
                return pxToDp(distanceInPx);
            }

            private float pxToDp(float px) {
                return px / getResources().getDisplayMetrics().density;
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        pressStartTime = System.currentTimeMillis();
                        stayedWithinClickDistance = true;

                        // get initial position.
                        initialX = mButtonParams.x;
                        initialY = mButtonParams.y;

                        // get touch position.
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();

                        break;
                    case MotionEvent.ACTION_UP:
                        long pressDuration = System.currentTimeMillis() - pressStartTime;

                        if (pressDuration < MAX_CLICK_DURATION && stayedWithinClickDistance) {
                            if (!mMap.isShown()) {
                                createFloatingMapView();
                                mWindowManager.addView(mMap, mMapParams);

                                // Bring floating button to the topmost view
                                if (mButton.isShown()) mWindowManager.removeView(mButton);
                                mWindowManager.addView(mButton, mButtonParams);
                            } else {
                                mWindowManager.removeView(mMap);
                            }
                        }

                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (stayedWithinClickDistance && distance(initialTouchX, initialTouchY,
                                event.getRawX(), event.getRawY()) > MAX_CLICK_DISTANCE) {
                            stayedWithinClickDistance = false;
                        }

                        mButtonParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        mButtonParams.y = initialY + (int) (event.getRawY() - initialTouchY);

                        // Update the button's position.
                        mWindowManager.updateViewLayout(mButton, mButtonParams);

                        break;
                }

                return true;
            }
        });
    }

    private void createTranslucentOverlay() {
        mOverlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mOverlayParams.gravity = Gravity.CENTER;

        mOverlay = (ConstraintLayout) View.inflate(getBaseContext(), R.layout.layout_translucent_overlay, null);
        ImageButton imbExit = mOverlay.findViewById(R.id.imvExit);
        imbExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOverlay.isShown()) {
                    mWindowManager.removeView(mOverlay);
                    Toast.makeText(MapService.this.getBaseContext(), "Overlay removed", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createFloatingMapView() {
        mMapParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mMapParams.gravity = Gravity.CENTER;

        mMap = new WebView(getBaseContext());
        mMap.setWebChromeClient(new WebChromeClient() {
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
        mMap.getSettings().setGeolocationEnabled(true);
        mMap.getSettings().setJavaScriptEnabled(true);
        mMap.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        mMap.getSettings().setBuiltInZoomControls(true);
        mMap.loadUrl(mMapUrl);
    }

    private void getNodeChildren(AccessibilityNodeInfo node) {
        if (node != null) {
            if (node.getViewIdResourceName() != null || node.getText() != null) {
                mNodesList.add(new Pair<>(node.getViewIdResourceName(), node.getText()));
            }

            // DFS
            for (int i = 0; i < node.getChildCount(); ++i) {
                getNodeChildren(node.getChild(i));
            }
        }
    }

    private void getAddresses() {
        int addressType = 0;
        String pickupAddress = "";
        String dropOffAddress = "";

        for (int i = 0; i < mNodesList.size(); ++i) {
            if ("com.grabtaxi.driver2:id/item_jod_ad_address_keywords".equals(mNodesList.get(i).first)) {
                if (addressType == 0) {
                    // use postal code for pick-up address
                    pickupAddress = "Singapore " + extractPostalCode(mNodesList.get(++i).second.toString());
                    addressType++;
                } else {
                    // use block and street for drop-off address
                    dropOffAddress = extractBlockAndStreet(mNodesList.get(++i).second.toString());
                    break;
                }
            }
        }

        mAddresses = new Pair<>(pickupAddress, dropOffAddress);
    }

    private void updateMapUrl() {
        String origin = "&origin=" + mAddresses.first;
        String destination = "&destination=" + mAddresses.second;
        String travel = "&travelmode=" + "walking";
        mMapUrl = Uri.encode(BASE_MAP_URL+origin+destination+travel, ALLOWED_URI_CHARS);
    }

    private String extractPostalCode(final String address) {
        final Pattern p = Pattern.compile( "(\\d{6})" );
        final Matcher m = p.matcher( address );
        if ( m.find() ) {
            return m.group( 0 );
        }
        return "";
    }

    private String extractBlockAndStreet(final String address) {
        String[] s = address.split(",");
        return (s[0] + s[1]);
    }

    private static void logViewHierarchy(AccessibilityNodeInfo nodeInfo, final int depth) {
        if (nodeInfo == null) return;

        String s = "";
        for (int i = 0; i < depth; ++i) {
            s = s.concat("-");
        }

        s = s.concat(nodeInfo.getClassName() + " " + nodeInfo.getViewIdResourceName());
        if ("android.widget.TextView".contentEquals(nodeInfo.getClassName())) {
            s = s.concat(" " + nodeInfo.getText());
        }
        Log.d(TAG, s);

        for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
            logViewHierarchy(nodeInfo.getChild(i), depth + 1);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!mPreferences.getBoolean("isServiceEnabled", true)) return;

        final int eventType = event.getEventType();

        // For debugging purposes
        Log.d(TAG, event.toString());
        Log.d(TAG, String.format(
                "onAccessibilityEvent: type = [ %s ], class = [ %s ], package = [ %s ]" +
                        ", time = [ %s ], text = [ %s ]",
                eventType, event.getClassName(), event.getPackageName(),
                event.getEventTime(), event.getText()));
        logViewHierarchy(getRootInActiveWindow(), 0);

        // Heartbeat
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName().equals("com.grabtaxi.driver2"))
            Toast.makeText(getBaseContext(), "TYPE_WINDOW_STATE_CHANGED detected", Toast.LENGTH_SHORT).show();

        // Check if job card is received from Grab Driver App
        if ((event.getText().contains("Accept") && event.getClassName().equals("android.view.ViewGroup"))) {
            Log.v(TAG, "Job card received");

            AccessibilityNodeInfo source = event.getSource();
            mNodesList = new ArrayList<>();
            getNodeChildren(source);
            getAddresses();
            updateMapUrl();

            // Bring floating button to the top
            if (mButton.isShown()) mWindowManager.removeView(mButton);
            mWindowManager.addView(mButton, mButtonParams);
        }
    }

    @Override
    public void onCreate() {
        Log.v(TAG, "MapService: onCreate()");
        super.onCreate();

        createNotification();
        createFloatingButton();
        createFloatingMapView();
        createTranslucentOverlay();

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        mPreferences.edit().putBoolean("isServiceEnabled", true).apply();
        if (!mButton.isShown()) mWindowManager.addView(mButton, mButtonParams);
        startForeground(1, mNotification);

        // Initialise wakelock (proximity sensor)
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START_MAP_SERVICE:
                    Log.v(TAG, "MapService: onStartCommand(), ACTION_START_MAP_SERVICE");
                    if (!mButton.isShown()) mWindowManager.addView(mButton, mButtonParams);

                    startForeground(1, mNotification);
                    break;
                case ACTION_STOP_MAP_SERVICE:
                    Log.v(TAG, "MapService: onStartCommand(), ACTION_STOP_MAP_SERVICE");
                    mPreferences.edit().putBoolean("isServiceEnabled", false).apply();
                    if (mButton.isShown()) mWindowManager.removeView(mButton);
                    if (mMap.isShown()) mWindowManager.removeView(mMap);
                    if (mOverlay.isShown()) mWindowManager.removeView(mOverlay);

                    getBaseContext().sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                    stopForeground(true);
                    stopSelf();
                    break;
                case ACTION_TOGGLE_OVERLAY_MAP_SERVICE:
                    Log.v(TAG, "MapService: onStartCommand(), ACTION_TOGGLE_OVERLAY_MAP_SERVICE");

                    if (mOverlay.isShown()) {
                        mWindowManager.removeView(mOverlay);
                    } else {
                        mWindowManager.addView(mOverlay, mOverlayParams);
                    }

                    getBaseContext().sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                    Toast.makeText(getBaseContext(), "Overlay " + (mOverlay.isShown() ? "spawned" : "removed"),
                            Toast.LENGTH_LONG).show();
                    break;
                case ACTION_TOGGLE_WAKELOCK_MAP_SERVICE:
                    Log.v(TAG, "MapService: onStartCommand(), ACTION_TOGGLE_WAKELOCK_MAP_SERVICE");

                    if (mWakeLock.isHeld()) {
                        mWakeLock.release();
                    } else {
                        mWakeLock.acquire();
                    }

                    getBaseContext().sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                    Toast.makeText(getBaseContext(), "Wakelock " + (mWakeLock.isHeld() ? "enabled" : "disabled"),
                            Toast.LENGTH_LONG).show();
                    break;
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        Log.v(TAG, "MapService: onTaskRemoved()");
    }

    @Override
    public void onInterrupt() {
        Log.v(TAG, "MapService: onInterrupt()");
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "MapService: onDestroy()");
        super.onDestroy();
        if (mWakeLock.isHeld()) mWakeLock.release();
    }
}