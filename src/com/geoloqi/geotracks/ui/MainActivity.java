package com.geoloqi.geotracks.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
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
import com.geoloqi.geotracks.Constants;
import com.geoloqi.geotracks.R;
import com.geoloqi.geotracks.maps.DoubleTapMapView;
import com.geoloqi.geotracks.utils.LocationUtils;
import com.google.android.maps.*;

import java.util.List;

/**
 * The main activity for the Geoloqi trips application.
 * 
 * @author Tristan Waddington
 */
public class MainActivity extends SherlockMapActivity implements
        OnClickListener, ActionBar.OnNavigationListener {
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
                R.array.action_list, R.layout.sherlock_spinner_dropdown_item);
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
        
        // Get our list of map overlays
        List<Overlay> mapOverlays = mMapView.getOverlays();
        
        // Set our map location overlay
        final MyLocationOverlay locationOverlay = new MyLocationOverlay(this, mMapView);
        locationOverlay.disableCompass();
        locationOverlay.enableMyLocation();
        mapOverlays.add(locationOverlay);
        
        // Get our MapController
        mMapController = mMapView.getController();
        
        // Insert the MapView into the layout
        ((ViewGroup) findViewById(android.R.id.content)).addView(mMapView, 0);
        
        // Restore our saved instance state
        if (savedInstanceState != null) {
            mMapZoom = savedInstanceState.getInt(EXTRA_ZOOM, DEFAULT_ZOOM_LEVEL);
        } else {
            locationOverlay.runOnFirstFix(new Runnable() {
                @Override
                public void run() {
                    mMapController.animateTo(locationOverlay.getMyLocation());
                }
            });
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
        
        // Set our zoom level
        mMapController.setZoom(mMapZoom);
        
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
        
        // Unregister our location receiver
        unregisterReceiver(mLocationReceiver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // Persist our map zoom
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
            Location location = LocationUtils.getLastKnownLocation(this);
            if (location != null) {
                // Set the map center to the device's last known location
                mMapCenter = new GeoPoint((int) (location.getLatitude() * 1e6),
                        (int) (location.getLongitude() * 1e6));
            }
            mMapController.animateTo(mMapCenter);
            return true;
        case R.id.menu_share:
            startActivity(new Intent(this, NewShareLinkActivity.class));
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
     * Handle broadcast messages from the location service when
     * this Activity is running in the foreground.
     * 
     * @author Tristan Waddington
     */
    private class MapBroadcastReceiver extends LQBroadcastReceiver {
        @Override
        public void onLocationChanged(Context context, Location location) {
            // TODO: Display the number of batched fixes!
        }

        @Override
        public void onLocationUploaded(Context context, int count) {
            // TODO: Update the number of batched fixes!
        }

        @Override
        public void onPushMessageReceived(Context context, Bundle data) {
            // Pass
        }

        @Override
        public void onTrackerProfileChanged(Context context,
                LQTrackerProfile oldProfile, LQTrackerProfile newProfile) {
            // TODO: Display the active tracker profile!
        }
    }
}
