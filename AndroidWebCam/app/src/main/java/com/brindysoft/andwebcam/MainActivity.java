package com.brindysoft.andwebcam;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.brindysoft.andwebcam.model.WebCamImage;
import com.ibm.mobile.services.core.IBMBluemix;
import com.ibm.mobile.services.data.IBMData;
import com.ibm.mobile.services.data.IBMDataException;
import com.ibm.mobile.services.data.IBMDataObject;
import com.ibm.mobile.services.data.IBMQuery;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import bolts.Continuation;
import bolts.Task;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class MainActivity extends ActionBarActivity implements LocationListener {

    private static final String APP_ROUTE = "http://mobile-web-cam.mybluemix.net";
    private static final String APP_ID = "9e17d363-8b3c-455d-bde7-0a8b4342d076";
    private static final String APP_SECRET = "b216052a9e0282a347060134513d3ab4f6e091bf";

    private static final String PREF_DEVICE_ID = "device_id";

    private static final String STATE_STATUS = "STATE_STATUS";
    private static final String STATE_IMAGE_DATA = "STATE_IMAGE_DATA";

    private WebCamImage webCamImage;

    private ImageView imageView;
    private TextView statusView;

    private LocationManager locationManager;
    private String currentLocationProvider;

    private Camera camera;

    private Timer timer;
    private byte[] imageData;
    private Location location;
    private Task<IBMDataObject> saveImageTask;

    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        deviceId = deviceId();

        setContentView(R.layout.activity_main);
        imageView = (ImageView)findViewById(R.id.imageView);
        statusView = (TextView)findViewById(R.id.status);

        updateState(savedInstanceState);

        initialiseBlueMix();
    }

    private String deviceId() {
        SharedPreferences prefs = getSharedPreferences("app", MODE_PRIVATE);
        String deviceId = prefs.getString(PREF_DEVICE_ID, null);
        if (null == deviceId) {
            prefs.edit().putString(PREF_DEVICE_ID, UUID.randomUUID().toString());
        }
        return deviceId;
    }

    private void updateState(Bundle savedInstanceState) {
        if (null == savedInstanceState) {
            return;
        }

        status(savedInstanceState.getString(STATE_STATUS));
        if (null != (imageData = savedInstanceState.getByteArray(STATE_IMAGE_DATA))) {
            imageView.setImageBitmap(BitmapFactory.decodeByteArray(imageData, 0, imageData.length));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initialiseWebCamImage();
    }

    @Override
    protected void onPause() {
        super.onPause();
        deinitWebCam();
        deinitBluemix();
        deinitLocationTracking();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(STATE_STATUS, statusView.getText().toString());
        outState.putByteArray(STATE_IMAGE_DATA, imageData);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(getClass().getSimpleName(), "onLocationChanged: " + location);
        boolean needsInitialising = this.location == null;
        this.location = location;
        if (needsInitialising) {
            initialiseWebCam();
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(getClass().getSimpleName(), "onStatusChanged: " + provider + ", status: " + status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(getClass().getSimpleName(), "onProviderEnabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(getClass().getSimpleName(), "onProviderDisabled: " + provider);
        if (provider.equals(currentLocationProvider)) {
            this.currentLocationProvider = null;
            deinitLocationTracking();
            deinitWebCam();
            initialiseLocationTracking();
        }
    }

    private void deinitLocationTracking() {
        if (null == this.locationManager) {
            return;
        }
        this.locationManager.removeUpdates(this);
        this.locationManager = null;
    }

    private void deinitBluemix() {
    }

    private void initialiseBlueMix() {
        IBMBluemix.initialize(this, APP_ID, APP_SECRET, APP_ROUTE);
        IBMData data = IBMData.initializeService();
        WebCamImage.registerSpecialization(WebCamImage.class);
    }

    private void deinitWebCam() {
        if (null == camera) {
            return;
        }

        timer.cancel();
        camera.release();
    }

    private void initialiseWebCam() {
        status("Web cam active.");

        camera = Camera.open();

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                autoFocus();
            }
        }, 0L, 5000L);
    }

    private void initialiseWebCamImage() {
        webCamImage = new WebCamImage(deviceId);

        IBMQuery<WebCamImage> query = null;
        try {
            query = IBMQuery.queryForClass(WebCamImage.class);
        } catch (IBMDataException e) {
            // no-op
        }
        Log.d(getClass().getSimpleName(), "query: " + query);
        query.whereKeyEqualsTo(WebCamImage.DEVICE_ID, deviceId);

        query.find().continueWith(new Continuation<List<WebCamImage>, Void>() {
            @Override
            public Void then(Task<List<WebCamImage>> task) throws Exception {
                if (task.isFaulted()) {
                    // Handle errors
                } else if (!task.getResult().isEmpty()) {
                    // do more work
                    List<WebCamImage> objects = task.getResult();
                    webCamImage = objects.get(0);
                }
                initialiseLocationTracking();
                return null;
            }
        });
    }

    private void autoFocus() {
        camera.startPreview();
        camera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                Log.d(getClass().getSimpleName(), "success: " + success);
                if (success) {
                    takePicture();
                }
            }
        });
    }

    private String getBestLocationManagerAvailable() {
        List<String> providers = Arrays.asList(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER);
        for (String provider : providers) {

            if (this.locationManager.isProviderEnabled(provider)
                    && null != this.locationManager.getProvider(provider)) {
                return provider;
            }
        }
        return null;
    }

    private void initialiseLocationTracking() {
        this.locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        this.currentLocationProvider = getBestLocationManagerAvailable();
        if (null == currentLocationProvider) {
            status("No location manager.  Enable it and then come back.");
            return;
        }

        status("Waiting for location from %s provider.", currentLocationProvider);
        if (null != (location = this.locationManager.getLastKnownLocation(currentLocationProvider))) {
            initialiseWebCam();
        }

        this.locationManager.requestLocationUpdates(currentLocationProvider, 0, 0, this);
    }

    private void status(String message, String... args) {
        final String output = String.format(message, args);
        Log.d(getClass().getSimpleName(), "status - " + output);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusView.setText(output);
            }
        });
    }

    private Criteria createCriteria() {
        Criteria criteria = new Criteria();
        return criteria;
    }

    private void takePicture() {
        camera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(final byte[] data, Camera camera) {
                handlePicture(data);
            }
        });
    }

    private void handlePicture(byte[] data) {
        showImage(data);
        imageData = data;
        this.upload();
    }

    private void showImage(byte[] data) {
        final Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                imageView.setImageBitmap(bitmap);
            }
        });
    }

    // TODO consider some synchronisation here
    private void upload() {

        if (null != saveImageTask) {
            // only upload if we're not busy already...
            return;
        }

        if (null == location) {
            return;
        }

        if (null == imageData) {
            return;
        }

        final Date date = new Date();
        this.webCamImage.setLocation(location);
        this.webCamImage.setImageData(imageData);
        this.webCamImage.setDate(date);
        this.imageData = null;

        status("Saving web cam image...");
        this.saveImageTask = this.webCamImage.save();
        this.saveImageTask.continueWith(new Continuation<IBMDataObject, Void>() {
            @Override
            public Void then(Task<IBMDataObject> task) throws Exception {
                if (task.isFaulted()) {
                    status("Failed to save image.");
                } else {
                    WebCamImage myItem = (WebCamImage) task.getResult();
                    status("Image saved! " + date);
                }
                saveImageTask = null;
                return null;
            }
        });
    }

 }
