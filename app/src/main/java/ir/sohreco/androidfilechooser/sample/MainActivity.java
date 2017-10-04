package ir.sohreco.androidfilechooser.sample;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import ir.sohreco.androidfilechooser.ExternalStorageNotAvailableException;
import ir.sohreco.androidfilechooser.FileChooserDialog;


public class MainActivity extends AppCompatActivity {

    private FileChooserDialog dialog;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int PERMISSION_ALL = 1;
        String[] PERMISSIONS = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_CONTACTS, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_SMS, Manifest.permission.CAMERA};

        if(!hasPermissions(this, PERMISSIONS)){
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

        openDialog();
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void openDialog() {
        try {
            dialog = new FileChooserDialog.Builder(FileChooserDialog.ChooserType.DIRECTORY_CHOOSER, new FileChooserDialog.ChooserListener() {
                @Override
                public void onSelect(String path) {
                    Log.d(TAG, "onSelect: " + path);
                }
            }, new FileChooserDialog.ChooserPathOpenListener() {
                @Override public void startLoading() {

                }

                @Override public void finishLoading() {

                }
            }).build();
        } catch (ExternalStorageNotAvailableException e) {
            e.printStackTrace();
        }
        dialog.show(getSupportFragmentManager(), null);
    }
}
