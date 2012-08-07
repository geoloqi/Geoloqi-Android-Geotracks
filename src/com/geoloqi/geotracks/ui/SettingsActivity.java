package com.geoloqi.geotracks.ui;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.geoloqi.android.sdk.LQBuild;
import com.geoloqi.android.sdk.LQSharedPreferences;
import com.geoloqi.android.sdk.LQTracker.LQTrackerProfile;
import com.geoloqi.android.sdk.service.LQService;
import com.geoloqi.android.sdk.service.LQService.LQBinder;
import com.geoloqi.geotracks.Build;
import com.geoloqi.geotracks.R;

/**
 * <p>This activity class is used to expose location tracking
 * preferences to a user.</p>
 * 
 * @author Tristan Waddington
 */
public class SettingsActivity extends SherlockPreferenceActivity implements
        OnPreferenceChangeListener, OnPreferenceClickListener {
    private static final String TAG = "SettingsActivity";
    private static final String PREF_USER_EMAIL = "com.geoloqi.geotracks.preference.EMAIL";
    private static final String URL_PRIVACY_POLICY =
            "https://geoloqi.com/privacy?utm_source=preferences&utm_medium=app&utm_campaign=android";
    
    /** The default tracker profile. */
    private static final LQTrackerProfile DEFAULT_TRACKER_PROFILE =
            LQTrackerProfile.LOGGING;
    
    /** A cached reference of the application version from the manifest. */
    private static String sAppVersion;
    
    /** An instance of the default SharedPreferences. */
    private SharedPreferences mPreferences;
    private LQService mService;
    private boolean mBound;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        
        // Get a shared preferences instance
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Set any preference listeners
        Preference preference = findPreference(
                getString(R.string.pref_key_tracker_status));
        if (preference != null) {
            preference.setOnPreferenceChangeListener(this);
        }
        
        preference = findPreference(getString(R.string.pref_key_real_time));
        if (preference != null) {
            preference.setOnPreferenceChangeListener(this);
        }
        
        preference = findPreference(
                getString(R.string.pref_key_account_username));
        if (preference != null) {
            preference.setOnPreferenceClickListener(this);
        }
        
        preference = findPreference(
                getString(R.string.pref_key_privacy_policy));
        if (preference != null) {
            preference.setOnPreferenceClickListener(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        
        if (mPreferences != null) {
            Preference preference = null;
            
            // Display the account
            preference = findPreference(getString(R.string.pref_key_account_username));
            if (preference != null) {
                if (!LQSharedPreferences.getSessionIsAnonymous(this)) {
                    preference.setTitle(getUserEmail(this));
                    preference.setSummary(R.string.user_account_summary);
                }
            }
            
            // Display the app version
            preference = findPreference(getString(R.string.pref_key_app_version));
            if (preference != null) {
                preference.setSummary(getAppVersion(this));
            }
            
            // Display the app build
            preference = findPreference(getString(R.string.pref_key_app_build));
            if (preference != null) {
                preference.setSummary(Build.APP_BUILD);
            }
            
            // Display the SDK version
            preference = findPreference(getString(R.string.pref_key_sdk_version));
            if (preference != null) {
                preference.setSummary(LQBuild.LQ_SDK_VERSION);
            }
            
            // Display the SDK build
            preference = findPreference(getString(R.string.pref_key_sdk_build));
            if (preference != null) {
                preference.setSummary(LQBuild.LQ_SDK_BUILD);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // Bind to the tracking service so we can call public methods on it
        Intent intent = new Intent(this, LQService.class);
        bindService(intent, mConnection, 0);
    }

    @Override
    public void onPause() {
        super.onPause();
        
        // Unbind from LQService
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        CheckBoxPreference realtime =
                (CheckBoxPreference) findPreference(getString(R.string.pref_key_real_time));
        
        String key = preference.getKey();
        if (key.equals(getString(R.string.pref_key_tracker_status))) {
            boolean enableLocation = newValue.equals(true);
            
            if (enableLocation) {
                // Start the service
                startTracker(this);
                
                // Enable the start on boot option
                realtime.setEnabled(true);
            } else {
                // Stop the service
                stopService(new Intent(this, LQService.class));
                
                // Disable the start on boot option
                realtime.setEnabled(false);
                realtime.setChecked(false);
            }
        } else if (key.equals(getString(R.string.pref_key_real_time))) {
            boolean isRealTime = Boolean.parseBoolean(newValue.toString());
            if (isRealTime) {
                // Start the tracker in real time mode
                startTracker(this, LQTrackerProfile.REAL_TIME);
            } else {
                // Start the tracker in the default mode
                startTracker(this);
            }
        }
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (key.equals(getString(R.string.pref_key_account_username))) {
            startActivity(new Intent(this, SignInActivity.class));
            return true;
        } else if (key.equals(getString(R.string.pref_key_privacy_policy))) {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(URL_PRIVACY_POLICY));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        }
        return false;
    }

    /**
     * Store the active user's email address in shared preferences.
     * 
     * @param context
     * @param email
     * @return true if the value was successfully written.
     */
    public static boolean setUserEmail(Context context, String email) {
        if (!TextUtils.isEmpty(email)) {
            SharedPreferences preferences =
                    PreferenceManager.getDefaultSharedPreferences(context);
            Editor editor = preferences.edit();
            editor.putString(PREF_USER_EMAIL, email);
            return editor.commit();
        }
        return false;
    }

    /**
     * Get the active user's email address as a string.
     * 
     * @param context
     * @return the email address; "Anonymous" if not set.
     */
    public static String getUserEmail(Context context) {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString(PREF_USER_EMAIL, "Anonymous");
    }

    /** Determine if the user has disabled the tracker. */
    public static boolean isTrackerEnabled(Context context) {
        SharedPreferences preferences = (SharedPreferences)
                PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(
                context.getString(R.string.pref_key_tracker_status), true);
    }

    /** Get the human-readable application version. */
    public static String getAppVersion(Context context) {
        if (TextUtils.isEmpty(sAppVersion)) {
            PackageManager pm = context.getPackageManager();
            try {
                sAppVersion = pm.getPackageInfo(
                        context.getPackageName(), 0).versionName;
            } catch (NameNotFoundException e) {
                // Pass
            }
        }
        return sAppVersion;
    }
    
    /** Start the background location service. */
    public static void startTracker(Context c) {
        startTracker(c, DEFAULT_TRACKER_PROFILE);
    }
    
    /** Start the background location service. */
    public static void startTracker(Context c, LQTrackerProfile profile) {
        Intent intent = new Intent(c, LQService.class);
        intent.setAction(LQService.ACTION_FOREGROUND);
        intent.putExtra(LQService.EXTRA_NOTIFICATION,
                getForegroundNotification(c));
        c.startService(intent);
        
        // Ensure the tracker is always in the correct profile
        Intent profileIntent = new Intent(c, LQService.class);
        profileIntent.setAction(LQService.ACTION_SET_TRACKER_PROFILE);
        profileIntent.putExtra(LQService.EXTRA_PROFILE, profile);
        c.startService(profileIntent);
    }
    
    /**
     * Return the {@link Notification} to be displayed when the location
     * service is running.
     */
    public static Notification getForegroundNotification(Context c) {
        Intent intent = new Intent(c, SettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(c, 0, intent, 0);
        
        // TODO: Add action buttons to stop the service in JellyBean!
        NotificationCompat.Builder builder = new NotificationCompat.Builder(c);
        builder.setWhen(0);
        builder.setOngoing(true);
        builder.setOnlyAlertOnce(true);
        builder.setContentIntent(pendingIntent);
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setTicker(c.getString(R.string.foreground_ticker));
        builder.setContentTitle(c.getString(R.string.foreground_title));
        builder.setContentText(c.getString(R.string.foreground_text));
        
        return builder.getNotification();
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
