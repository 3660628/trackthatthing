package com.mgalgs.trackthatthing;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MyLocationService extends Service implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener {

    private LocationClient mLocationClient;
    private LocationRequest mLocationRequest;

    // Milliseconds per second
    private static final int MILLISECONDS_PER_SECOND = 1000;
    // Update frequency in seconds
    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;
    // Update frequency in milliseconds
    private static final long UPDATE_INTERVAL =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    // The fastest update frequency, in seconds
    private static final int FASTEST_INTERVAL_IN_SECONDS = 3;
    // A fast frequency ceiling in milliseconds
    private static final long FASTEST_INTERVAL =
            MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;
    private boolean mResolvingError = false;


    public MyLocationService() {
    }

    private NotificationManager mNM;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.location_service_started;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        MyLocationService getService() {
            return MyLocationService.this;
        }
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        mLocationClient = new LocationClient(this, this, this);

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();
        // Use high accuracy
        mLocationRequest.setPriority(
                LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 5 seconds
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        // Set the fastest update interval to 1 second
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

        mLocationClient.connect();

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("MyLocationService", "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);

        if (mLocationClient.isConnected()) {
            mLocationClient.removeLocationUpdates(this);
        }
        mLocationClient.disconnect();

        // Tell the user we stopped.
        Toast.makeText(this, "Stopped MyLocationService", Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence title = getText(R.string.location_service_started);
        CharSequence text = getText(R.string.touch_to_stop);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.powered_by_google_light)
                .setContentTitle(title)
                .setContentText(text)
                .build();
//        Notification notification = new Notification(R.drawable.stat_sample, text,
//                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }

    /**
     * Define a DialogFragment to display the error dialog generated in
     * showErrorDialog.
     */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode,
                    this.getActivity(), REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((MainActivity) getActivity()).onDialogDismissed();
        }
    }



    /*
     * Called by Location Services when the request to connect the
     * client finishes successfully. At this point, you can
    * request the current location or start periodic updates
    */
    @Override
    public void onConnected(Bundle dataBundle) {
        // Display the connection status
        Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    /*
     * Called by Location Services if the connection to the
     * location client drops because of an error.
     */
    @Override
    public void onDisconnected() {
        // Display the connection status
        Toast.makeText(this, "Disconnected. Please re-connect.",
                Toast.LENGTH_SHORT).show();
    }

    /*
     * Called by Location Services if the attempt to
     * Location Services fails.
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mLocationClient.connect();
            }
        } else {
            // Show dialog using GooglePlayServicesUtil.getErrorDialog()
            showErrorDialog(result.getErrorCode());
            mResolvingError = true;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // Report to the UI that the location was updated
        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + "," +
                Double.toString(location.getLongitude());
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.d(TrackThatThing.TAG, msg);

        SharedPreferences settings = getSharedPreferences(TrackThatThing.PREFS_NAME, MODE_PRIVATE);

        long lastLocTime = SystemClock.elapsedRealtime();

        float acc = location.getAccuracy();
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        float speed = location.getSpeed();

        String secret_code = settings.getString(TrackThatThing.PREF_SECRET_CODE, null);

        QueryString qs = new QueryString(TrackThatThing.BASE_URL + "/put");
        qs.add("secret", secret_code);
        qs.add("lat", Double.toString(lat));
        qs.add("lon", Double.toString(lon));
        qs.add("acc", Float.toString(acc));
        qs.add("speed", Float.toString(speed));

        Runnable r = new MyInternetThread(qs);
        new Thread(r).start();

        Log.d(TrackThatThing.TAG, "got this location: " + location.toString());
    }

    // A runnable for the HTTP request
    public class MyInternetThread implements Runnable {
        public final QueryString qs;

        public MyInternetThread(QueryString qs_) {
            qs = qs_;
        }

        public void run() {
            try {
                JSONObject json = RestClient.connect(qs.toString());
                Log.i(TrackThatThing.TAG,
                        "Got the following response from the server: "
                                + json.getString("msg"));
                SharedPreferences settings = getSharedPreferences(TrackThatThing.PREFS_NAME,
                        android.content.Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = settings.edit();

                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a yyyy-MM-dd");
                Calendar cal = Calendar.getInstance();

                editor.putString(TrackThatThing.PREF_LAST_LOC_TIME,
                        sdf.format(cal.getTime()));
                editor.commit();

//                mHandler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        mYesTrackingFragment.updateLastLoc(getApplicationContext(), getWindow().getDecorView().getRootView());
//                    }
//t                });
            } catch (JSONException e) {
                Log.e(TrackThatThing.TAG,
                        "couldn't get \"msg\" out of JSON object...");
                e.printStackTrace();
            } catch (Exception e) {
                Log.e(TrackThatThing.TAG,
                        "Something went wrong while trying to make the JSON object...");
                e.printStackTrace();
            }
        }
    }
}