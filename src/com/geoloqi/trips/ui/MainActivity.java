package com.geoloqi.trips.ui;

import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SpinnerAdapter;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockMapActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.geoloqi.android.sdk.LQSharedPreferences;
import com.geoloqi.android.sdk.LQTracker.LQTrackerProfile;
import com.geoloqi.android.sdk.receiver.LQBroadcastReceiver;
import com.geoloqi.android.sdk.service.LQService;
import com.geoloqi.android.sdk.service.LQService.LQBinder;
import com.geoloqi.trips.Constants;
import com.geoloqi.trips.R;
import com.geoloqi.trips.maps.DoubleTapMapView;
import com.geoloqi.trips.maps.LocationItemizedOverlay;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

/**
 * The main activity for the Geoloqi trips application.
 * 
 * @author Tristan Waddington
 */
public class MainActivity extends SherlockMapActivity implements
        OnClickListener, ActionBar.OnNavigationListener {
    public static final String EXTRA_LAT = "com.geoloqi.geonotes.extra.LAT";
    public static final String EXTRA_LNG = "com.geoloqi.geonotes.extra.LNG";
    public static final String EXTRA_SPAN = "com.geoloqi.geonotes.extra.SPAN";
    public static final String EXTRA_ZOOM = "com.geoloqi.geonotes.extra.ZOOM";
    
    private static final String TAG = "MainActivity";
    
    /** The default zoom level to display. */
    private static final int DEFAULT_ZOOM_LEVEL = 19;
    
    /** The default center point to display. */
    private static final GeoPoint DEFAULT_MAP_CENTER =
            new GeoPoint(45516290, -122675943);
    
    /** An instance of {@link LQBroadcastReceiver}. */
    private final MapBroadcastReceiver mLocationReceiver = new MapBroadcastReceiver();
    
    /** The initial map zoom. */
    private int mMapZoom = DEFAULT_ZOOM_LEVEL;
    
    /** The initial map center. */
    private GeoPoint mMapCenter = DEFAULT_MAP_CENTER;
    
    private MapView mMapView;
    private MapController mMapController;
    
    private SpinnerAdapter mSpinnerAdapter;
    
    private LQService mService;
    private boolean mBound;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Define our activity layout
        setContentView(R.layout.main);
        
        // Configure our ActionBar navigation
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        
        SpinnerAdapter mSpinnerAdapter = ArrayAdapter.createFromResource(actionBar.getThemedContext(),
                R.array.action_list, android.R.layout.simple_spinner_dropdown_item);
        actionBar.setListNavigationCallbacks(mSpinnerAdapter, this);
        
        // Ensure the correct navigation item is selected!
        actionBar.setSelectedNavigationItem(0);
        
        // Configure our MapView
        mMapView = new DoubleTapMapView(this, Constants.GOOGLE_MAPS_KEY);
        mMapView.setClickable(true);
        
        // Disable the built in zoom controls if the device
        // has multitouch capabilities!
        mMapView.setBuiltInZoomControls(!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH));
        
        // Get our MapController
        mMapController = mMapView.getController();
        
        // Insert the MapView into the layout
        ((ViewGroup) findViewById(android.R.id.content)).addView(mMapView, 0);
        
        // Restore our saved map state
        if (savedInstanceState != null) {
            int lat = savedInstanceState.getInt(EXTRA_LAT, 0);
            int lng = savedInstanceState.getInt(EXTRA_LNG, 0);
            
            if ((lat + lng) != 0) {
                mMapCenter = new GeoPoint(lat, lng);
            }
            mMapZoom = savedInstanceState.getInt(EXTRA_ZOOM, DEFAULT_ZOOM_LEVEL);
        } else {
            Location location = getLastKnownLocation();
            if (location != null) {
                // Set the map center to the device's last known location
                mMapCenter = new GeoPoint((int) (location.getLatitude() * 1e6),
                        (int) (location.getLongitude() * 1e6));
            }
        }
        
        // Wire up our onclick handlers
        Button signUpButton = (Button) findViewById(R.id.sign_up_button);
        if (signUpButton != null) {
            signUpButton.setOnClickListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // Set our map center and zoom level
        setMapCenter(mMapCenter);
        //mMapController.setCenter(mMapCenter);
        mMapController.setZoom(mMapZoom);
        
        /*
        // Get our location manager
        LocationManager locationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);
        
        // Notify the user if GPS is disabled
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            DialogFragment dialog = SimpleAlertDialogFragment.newInstance(
                    R.string.dialog_gps_title, R.string.dialog_gps_message);
            dialog.show(getFragmentManager(), "gpsdialog");
        }
        */
        
        // Bind to the tracking service so we can call public methods on it
        Intent intent = new Intent(this, LQService.class);
        bindService(intent, mConnection, BIND_AUTO_CREATE);
        
        // Wire up the map location receiver
        registerReceiver(mLocationReceiver,
                LQBroadcastReceiver.getDefaultIntentFilter());
        
        // Prompt anonymous users to register
        View authNotice = findViewById(R.id.auth_notice);
        if (LQSharedPreferences.getSessionIsAnonymous(this)) {
            authNotice.setVisibility(View.VISIBLE);
        } else {
            authNotice.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        
        // Unbind from LQService
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        
        // Unregister our location receiver
        unregisterReceiver(mLocationReceiver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // Persist our map position
        outState.putInt(EXTRA_LAT,
                mMapView.getMapCenter().getLatitudeE6());
        outState.putInt(EXTRA_LNG,
                mMapView.getMapCenter().getLongitudeE6());
        outState.putInt(EXTRA_ZOOM,
                mMapView.getZoomLevel());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.menu_center_map:
            mMapController.animateTo(mMapCenter);
            return true;
        case R.id.menu_share:
            // TODO: Create a new sharing link!
            return true;
        case R.id.menu_settings:
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.sign_up_button:
            startActivity(new Intent(this, SignUpActivity.class));
            break;
        }
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        switch (itemPosition) {
        case 0:
            // Do nothing! The map is already selected.
            return true;
        case 1:
            // Start the LinkListActivity!
            Intent intent = new Intent(this, LinkListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            
            // Finish this Activity to remove it from the task stack. This
            // preserves the expected back behavior.
            finish();
            
            return true;
        }
        return false;
    }

    /**
     * Get the last known {@link Location} from the GPS provider. If the
     * GPS provider is disabled, query the network provider.
     * 
     * @return the last known location; otherwise null.
     */
    private Location getLastKnownLocation() {
        Location location;
        LocationManager locationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);
        
        // Attempt to get the last known GPS location
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            location = locationManager.getLastKnownLocation(
                    LocationManager.GPS_PROVIDER);
        } else {
            location = locationManager.getLastKnownLocation(
                    LocationManager.NETWORK_PROVIDER);
        }
        return location;
    }

    /** Stub */
    private void setMapCenter(GeoPoint center) {
        // Create our overlay item
        OverlayItem geonote = new OverlayItem(center, null, null);
        
        // Build our map overlay
        LocationItemizedOverlay geonoteOverlay = new LocationItemizedOverlay(
                getResources().getDrawable(R.drawable.marker));
        
        // Add the item to our overlay
        geonoteOverlay.addOverlay(geonote);
        
        // Add the overlay to our map view
        List<Overlay> mapOverlays = mMapView.getOverlays();
        
        for (Overlay overlay : mapOverlays) {
            if (overlay instanceof LocationItemizedOverlay) {
                mapOverlays.remove(overlay);
            }
        }
        
        // TODO: I think we're going to end up with multiple overlay
        //       instances all running on the same map.
        mapOverlays.add(geonoteOverlay);
        
        // Center the map
        mMapController.animateTo(center);
    }

    /** Stub */
    private class MapBroadcastReceiver extends LQBroadcastReceiver {
        @Override
        public void onLocationChanged(Context context, Location location) {
            mMapCenter = new GeoPoint((int) (location.getLatitude() * 1e6),
                    (int) (location.getLongitude() * 1e6));
            
            // ...
            setMapCenter(mMapCenter);
        }

        @Override
        public void onLocationUploaded(Context context, int count) {
            // Pass
        }

        @Override
        public void onPushMessageReceived(Context context, Bundle data) {
            // Pass
        }

        @Override
        public void onTrackerProfileChanged(Context context,
                LQTrackerProfile oldProfile, LQTrackerProfile newProfile) {
            // Pass
        }
    }
    
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                // We've bound to LocalService, cast the IBinder and get LocalService instance.
                LQBinder binder = (LQBinder) service;
                mService = binder.getService();
                mBound = true;
            } catch (ClassCastException e) {
                // Pass
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };
}
