package com.geoloqi.trips.utils;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

/**
 * A collection of static methods for working with location data.
 * 
 * @author Tristan Waddington
 */
public class LocationUtils {
    /**
     * Get the last known {@link Location} from the GPS provider. If the
     * GPS provider is disabled, query the network provider.
     * 
     * @param c
     * @return the last known location; otherwise null.
     */
    public static Location getLastKnownLocation(Context c) {
        Location location;
        LocationManager locationManager =
                (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        
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
}
