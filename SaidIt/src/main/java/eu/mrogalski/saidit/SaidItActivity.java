package eu.mrogalski.saidit;

import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

public class SaidItActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 5465;
    private boolean isFragmentSet = false;
    private AlertDialog permissionDeniedDialog;
    private SaidItService echoService;
    private boolean isBound = false;

    private final ServiceConnection echoConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            SaidItService.BackgroundRecorderBinder typedBinder = (SaidItService.BackgroundRecorderBinder) binder;
            echoService = typedBinder.getService();
            isBound = true;
            if (mainFragment != null) {
                mainFragment.setService(echoService);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            echoService = null;
            isBound = false;
        }
    };
    private static final int HOW_TO_REQUEST_CODE = 123;
    private SaidItFragment mainFragment;

    private final ActivityResultLauncher<Intent> howToLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (mainFragment != null) {
                    mainFragment.startTour();
                }
            });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background_recorder);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (permissionDeniedDialog != null) {
            permissionDeniedDialog.dismiss();
        }
        requestPermissions();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (permissionDeniedDialog != null) {
            permissionDeniedDialog.dismiss();
        }
        requestPermissions();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbinding is now handled in onDestroy to keep service alive during navigation
    }

    private void requestPermissions() {
        // Ask for storage permission

        String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.FOREGROUND_SERVICE};
        if(Build.VERSION.SDK_INT >= 33) {
            permissions = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.POST_NOTIFICATIONS};
        }
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                if (!isBound) {
                    // Start the service to ensure it's running
                    Intent serviceIntent = new Intent(this, SaidItService.class);
                    startService(serviceIntent);
                    bindService(serviceIntent, echoConnection, Context.BIND_AUTO_CREATE);
                }
                showFragment();
            } else {
                if (permissionDeniedDialog == null || !permissionDeniedDialog.isShowing()) {
                    showPermissionDeniedDialog();
                }
            }
        }
    }

    private void showFragment() {
        if (!isFragmentSet) {
            isFragmentSet = true;

            // Check for first run
            SharedPreferences prefs = getSharedPreferences("eu.mrogalski.saidit", MODE_PRIVATE);
            boolean isFirstRun = prefs.getBoolean("is_first_run", true);

            mainFragment = new SaidItFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, mainFragment, "main-fragment")
                    .commit();

            if (isFirstRun) {
                howToLauncher.launch(new Intent(this, HowToActivity.class));
                prefs.edit().putBoolean("is_first_run", false).apply();
            } else {
                boolean showTour = prefs.getBoolean("show_tour_on_next_launch", false);
                if (showTour) {
                    if (mainFragment != null) {
                        mainFragment.startTour();
                    }
                    prefs.edit().putBoolean("show_tour_on_next_launch", false).apply();
                }
            }
        }
    }
    private void showPermissionDeniedDialog() {
        permissionDeniedDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.permission_required)
                .setMessage(R.string.permission_required_message)
                .setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Open app settings
                        Intent intent = new Intent();
                        intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", getPackageName(), null));
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    public SaidItService getEchoService() {
        return echoService;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(echoConnection);
            isBound = false;
        }
    }
}