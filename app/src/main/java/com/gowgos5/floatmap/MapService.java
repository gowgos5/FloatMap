package com.gowgos5.floatmap;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
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
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@TargetApi(26)
public class MapService extends AccessibilityService {
    public static final String ACTION_START_MAP_SERVICE = "START_MAP_SERVICE";
    public static final String ACTION_STOP_MAP_SERVICE = "STOP_MAP_SERVICE";
    public static final String ACTION_SPAWN_BUTTON_MAP_SERVICE = "SPAWN_BUTTON_MAP_SERVICE";

    private static final String BASE_MAP_URL = "https://www.google.com/maps/dir/?api=1";
    private static final String DEFAULT_MAP_URL = "https://www.google.com/maps/";
    private static final String ALLOWED_URI_CHARS = "@#&=*+-_.,:!?()/~'%";

    private Notification mNotification;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mButtonParams;
    private WindowManager.LayoutParams mMapParams;
    private ImageView mButton;
    private WebView mMap;

    private ArrayList<Pair<String, CharSequence>> mNodesList;
    private Pair<String, String> mAddresses;
    private String mMapUrl;

    private boolean isMapVisible = false;

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

        // Set up Notification
        mNotification = new NotificationCompat.Builder(this, "MAP_SERVICE_ID")
                .setSmallIcon(R.drawable.ic_map_icon)
                .setContentTitle("FloatMap")
                .setContentText("MapService is running.")
                .setOngoing(true)
                .setPriority(0)
                .setAutoCancel(false)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP), 0))
                .addAction(R.drawable.ic_refresh, "Respawn button",
                        PendingIntent.getService(this, 1,
                                new Intent(this, MapService.class)
                                        .setAction(ACTION_SPAWN_BUTTON_MAP_SERVICE), 0))
                .addAction(R.drawable.ic_close, "Exit",
                        PendingIntent.getService(this, 1,
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
        mButtonParams.width = 150;
        mButtonParams.height = 150;

        mButton = new ImageView(this);
        mButton.setImageResource(R.drawable.ic_map);
        //noinspection AndroidLintClickableViewAccessibility
        mButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (MainActivity.ACTIVITY_STATE_ACTIVE) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            // get initial position.
                            initialX = mButtonParams.x;
                            initialY = mButtonParams.y;

                            // get touch location.
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();

                            return true;
                        case MotionEvent.ACTION_MOVE:
                            // Calculate the X and Y coordinates of the button.
                            mButtonParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                            mButtonParams.y = initialY + (int) (event.getRawY() - initialTouchY);

                            // Update the button's position.
                            mWindowManager.updateViewLayout(mButton, mButtonParams);

                            return true;
                    }

                    return false;
                } else {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (!isMapVisible) {
                            mMap.loadUrl(mMapUrl);
                            mWindowManager.addView(mMap, mMapParams);

                            // Bring floating button to the topmost view
                            mWindowManager.removeView(mButton);
                            mWindowManager.addView(mButton, mButtonParams);
                        } else {
                            mWindowManager.removeView(mMap);
                        }

                        isMapVisible = !isMapVisible;

                        return true;
                    }

                    return false;
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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
        mMap.loadUrl(DEFAULT_MAP_URL);
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
                    pickupAddress = "Singapore " + extractDigits(mNodesList.get(++i).second.toString());
                    addressType++;
                } else {
                    dropOffAddress = "Singapore " + extractDigits(mNodesList.get(++i).second.toString());
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

    public static String extractDigits(final String in) {
        final Pattern p = Pattern.compile( "(\\d{6})" );
        final Matcher m = p.matcher( in );
        if ( m.find() ) {
            return m.group( 0 );
        }
        return "";
    }

    @Override
    public void onCreate() {
        Log.v("MapService", "onCreate()");
        super.onCreate();

        createNotification();
        createFloatingButton();
        createFloatingMapView();
        mWindowManager.addView(mButton, mButtonParams);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START_MAP_SERVICE:
                    Log.v("MapService", "MapService started.");
                    startForeground(1, mNotification);
                    break;
                case ACTION_STOP_MAP_SERVICE:
                    Log.v("MapService", "MapService terminated.");
                    stopForeground(true);
                    stopSelf();
                    break;
                case ACTION_SPAWN_BUTTON_MAP_SERVICE:
                    Log.v("MapService", "Floating button respawned");
                    Toast.makeText(this, "Button respawned", Toast.LENGTH_LONG).show();
                    mWindowManager.removeView(mButton);
                    mWindowManager.addView(mButton, mButtonParams);
                    break;
            }
        }

        return START_STICKY;
        // return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        // For debugging purposes
        Log.v("MapService", String.format(
                "onAccessibilityEvent: type = [ %s ], class = [ %s ], package = [ %s ]" +
                        ", time = [ %s ], text = [ %s ]",
                eventType, event.getClassName(), event.getPackageName(),
                event.getEventTime(), event.getText()));

        // Heartbeat
        Toast.makeText(getBaseContext(), "TYPE_WINDOW_STATE_CHANGED detected", Toast.LENGTH_SHORT).show();

        // Check if job card is received from Grab Driver App
        if ((event.getText().contains("Accept") && event.getClassName().equals("android.view.ViewGroup"))) {
            Log.v("MapService", "Job card received");
            Toast.makeText(getBaseContext(), "Job card received!", Toast.LENGTH_LONG).show();

            AccessibilityNodeInfo source = event.getSource();
            mNodesList = new ArrayList<>();
            getNodeChildren(source);
            getAddresses();
            updateMapUrl();

            Log.v("MapService", "Pickup Address: " + mAddresses.first);
            Log.v("MapService", "Dropoff Address: " + mAddresses.second);

            // Bring floating button to the topmost view
            mWindowManager.removeView(mButton);
            mWindowManager.addView(mButton, mButtonParams);
        }
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mButton != null) mWindowManager.removeView(mButton);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent){
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());

        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext()
                , 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT);
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);

        super.onTaskRemoved(rootIntent);
    }
}