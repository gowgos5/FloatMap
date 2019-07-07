package com.gowgos5.floatmap;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

@TargetApi(23)
public class MainActivity extends AppCompatActivity {
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    private AlertDialog mDrawDialog;
    private AlertDialog mAccessDialog;

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }

    private boolean isAccessibilitySettingsOn(@org.jetbrains.annotations.NotNull Context context) {
        int accessibilityEnabled;

        // Check if system-wide accessibility settings exist.
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                splitter.setString(settingValue);
                while (splitter.hasNext()) {
                    if (splitter.next().equalsIgnoreCase(getPackageName() + "/" +
                            MapService.class.getCanonicalName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void checkRequiredPermissions() {
        boolean drawOn = Settings.canDrawOverlays(this);
        boolean accessibilityOn = isAccessibilitySettingsOn(this);
        boolean locationOn = (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED);

        if (drawOn && accessibilityOn && locationOn) {
            mDrawDialog.cancel();
            mAccessDialog.cancel();

            startService(new Intent(this, MapService.class)
                    .setAction(MapService.ACTION_START_MAP_SERVICE));

            Toast.makeText(getBaseContext(), "Button spawned", Toast.LENGTH_LONG).show();
            super.finishAndRemoveTask();
        } else {
            if (isServiceRunning(MapService.class)) {
                startService(new Intent(this, MapService.class)
                        .setAction(MapService.ACTION_STOP_MAP_SERVICE));
            }

            if (!locationOn) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }

            if (!drawOn) {
                mDrawDialog.show();
            }

            if (!accessibilityOn) {
                mAccessDialog.show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v("MainActivity", "MainActivity: onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDrawDialog = new AlertDialog.Builder(this)
                .setTitle("Allow FloatMap to display over other apps")
                .setMessage("FloatMap requires permission to display over other applications.")
                .setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getApplicationContext().getPackageName())));
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        System.exit(0);
                    }
                })
                .setCancelable(false)
                .create();

        mAccessDialog = new AlertDialog.Builder(this)
                .setTitle("Enable Accessibility Services")
                .setMessage("FloatMap uses Accessibility Services to detect job cards from Grab Driver.")
                .setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        System.exit(0);
                    }
                })
                .setCancelable(false)
                .create();
    }

    @Override
    protected void onStart() {
        Log.v("MainActivity", "MainActivity: onStart()");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.v("MainActivity", "MainActivity: onResume()");
        super.onResume();

        checkRequiredPermissions();
    }

    @Override
    protected void onPause() {
        Log.v("MainActivity", "MainActivity: onPause()");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.v("MainActivity", "MainActivity: onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.v("MainActivity", "MainActivity: onDestroy()");
        super.onDestroy();
    }
}