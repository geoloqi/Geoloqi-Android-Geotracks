package com.geoloqi.geotracks.ui;

import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.geoloqi.android.sdk.LQException;
import com.geoloqi.android.sdk.LQSession;
import com.geoloqi.android.sdk.LQSession.OnRunApiRequestListener;
import com.geoloqi.android.sdk.service.LQService;
import com.geoloqi.android.sdk.service.LQService.LQBinder;
import com.geoloqi.geotracks.utils.LocationUtils;
import com.geoloqi.geotracks.R;

public class NewShareLinkActivity extends SherlockActivity implements
        OnClickListener {
    private static final String TAG = "NewShareLinkActivity";

    private String mDescription;

    private ProgressDialog mProgress;
    private LQService mService;
    private boolean mBound;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Define our layout
        setContentView(R.layout.new_share_link);

        // Wire up our on click listeners
        Button submitButton = (Button) findViewById(R.id.submit_button);
        if (submitButton != null) {
            submitButton.setOnClickListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Bind to the tracking service so we can call public methods on it
        Intent intent = new Intent(this, LQService.class);
        bindService(intent, mConnection, BIND_AUTO_CREATE);
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
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.submit_button:
            mDescription = ((TextView) findViewById(R.id.description)).getText().toString();
            createLink(mDescription);
            break;
        }
    }

    private void createLink(String description) {
        try {
            // Notify the server that the link should be created
            if (mBound && mService != null) {
                LQSession session = mService.getSession();
                if (session != null) {
                    // Show a progress dialog
                    mProgress = ProgressDialog.show(this, null, 
                            getString(R.string.loading_message), true);
                    
                    // Get our last known location
                    Location location = LocationUtils.getLastKnownLocation(this);
                    
                    JSONObject data = new JSONObject();
                    data.put("description", description);
                    data.put("longitude", location.getLongitude());
                    data.put("latitude", location.getLatitude());
                    
                    session.runPostRequest("link/create", data,
                            new CreateLinkListener());
                }
            }
        } catch (JSONException e) {
            // Notify the user
            Toast.makeText(this, "Failed to expire share link!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Handle the server response from a create link request. */
    private class CreateLinkListener implements OnRunApiRequestListener {
        @Override
        public void onComplete(LQSession session, JSONObject json,
                Header[] headers, StatusLine status) {
            // Hide the progress dialog
            mProgress.dismiss();
            
            // Notify the user
            Toast.makeText(NewShareLinkActivity.this,
                    json.optString("error_description"), Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onFailure(LQSession session, LQException e) {
            Log.e(TAG, "Failed to create new share link!", e);
            // Hide the progress dialog
            mProgress.dismiss();
            
            // Notify the user
            Toast.makeText(NewShareLinkActivity.this,
                    "Failed to create share link!", Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onSuccess(LQSession session, JSONObject json,
                Header[] headers) {
            // Hide the progress dialog
            mProgress.dismiss();
            
            // Notify the user
            Toast.makeText(NewShareLinkActivity.this,
                    "Link created!", Toast.LENGTH_SHORT).show();
            
            String shortlink = json.optString("shortlink");
            
            // Start the sharing dialog
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    String.format("%s: %s", mDescription, shortlink));
            startActivity(Intent.createChooser(shareIntent,
                    getString(R.string.share_link_dialog_title)));
            
            // End the activity
            finish();
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                // We've bound to LocalService, cast the IBinder and get
                // LocalService instance.
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
