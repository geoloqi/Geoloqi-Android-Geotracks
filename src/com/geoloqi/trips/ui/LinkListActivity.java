package com.geoloqi.trips.ui;

import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SpinnerAdapter;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.geoloqi.android.sdk.LQException;
import com.geoloqi.android.sdk.LQSession;
import com.geoloqi.android.sdk.LQSession.OnRunApiRequestListener;
import com.geoloqi.android.sdk.service.LQService;
import com.geoloqi.android.sdk.service.LQService.LQBinder;
import com.geoloqi.trips.R;
import com.geoloqi.trips.utils.LocationUtils;
import com.geoloqi.trips.widget.LinkListAdapter;

/**
 * An implementation of {@link ListFragment} for displaying
 * the currently authenticated user's list of share links.
 * 
 * @author Tristan Waddington
 */
public class LinkListActivity extends SherlockListActivity implements
        OnItemClickListener, ActionBar.OnNavigationListener {
    private static final String TAG = "LinkListActivity";
    
    private JSONArray mItems;
    private LinkListAdapter mAdapter;
    private SpinnerAdapter mSpinnerAdapter;
    
    private LQService mService;
    private boolean mBound;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.link_list);
        
        // Configure our ActionBar navigation
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        
        SpinnerAdapter mSpinnerAdapter = ArrayAdapter.createFromResource(actionBar.getThemedContext(),
                R.array.action_list, android.R.layout.simple_spinner_dropdown_item);
        actionBar.setListNavigationCallbacks(mSpinnerAdapter, this);
        
        // Ensure the correct navigation item is selected!
        actionBar.setSelectedNavigationItem(1);
        
        // Configure our ListView
        ListView lv = getListView();
        lv.setFastScrollEnabled(false);
        lv.setOnItemClickListener(this);
        
        // Register our context menu
        registerForContextMenu(lv);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        com.actionbarsherlock.view.MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.link_list_menu, menu);
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
        switch(item.getItemId()) {
        case R.id.menu_refresh:
            if (mBound && mService != null) {
                onRefreshRequested(mService);
            }
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
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        JSONObject link = mAdapter.getItem(position);
        
        // Start our message detail activity
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(link.optString("shortlink")));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        
        // Inflate our context menu
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.link_context_menu, menu);
        
        // Get our context item info
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        JSONObject link = (JSONObject) mAdapter.getItem(info.position);
        
        // Disable the deactivate menu item if the share link
        // has already expired!
        MenuItem linkDeactivateItem = menu.findItem(R.id.menu_link_deactivate);
        if (linkDeactivateItem != null) {
            boolean isActive = link.optInt("currently_active") > 0;
            if (isActive) {
                linkDeactivateItem.setEnabled(true);
            } else {
                linkDeactivateItem.setEnabled(false);
            }
        }
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        JSONObject link = (JSONObject) mAdapter.getItem(info.position);
        
        switch (item.getItemId()) {
        case R.id.menu_link_deactivate:
            // Expire the share link
            expireLink(info, link);
            return true;
        case R.id.menu_link_share:
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, String.format("%s: %s",
                    link.optString("description"), link.optString("shortlink")));
            startActivity(Intent.createChooser(intent, null));
            return true;
        case R.id.menu_link_export:
            // TODO: What do we want to do here?
            return true;
        }
        return false;
    }
    
    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        switch (itemPosition) {
        case 0:
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            
            // Finish this Activity to remove it from the task stack. This
            // preserves the expected back behavior.
            finish();
            
            return true;
        case 1:
            // Do nothing! The LinkListActivity is already selected.
            return true;
        }
        return false;
    }
    
    private void expireLink(final AdapterContextMenuInfo info,
            final JSONObject link) {
        try {
            // Update the link object
            link.put("currently_active", 0);
            
            // Update our items array
            mItems.put(info.position, link);
            
            // Notify our list adapter of the update
            mAdapter.notifyDataSetChanged();
            
            // Notify the server that the link should be expired
            if (mBound && mService != null) {
                LQSession session = mService.getSession();
                if (session != null) {
                    // Get our last known location
                    Location location = LocationUtils.getLastKnownLocation(this);
                    
                    JSONObject data = new JSONObject();
                    data.put("token", link.optString("token"));
                    data.put("longitude", location.getLongitude());
                    data.put("latitude", location.getLatitude());
                    
                    session.runPostRequest("link/expire", data,
                            new ExpireLinkListener());
                }
            }
        } catch (JSONException e) {
            // Notify the user
            Toast.makeText(this, "Failed to expire share link!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Handle the server response from an expire link request. */
    private class ExpireLinkListener implements OnRunApiRequestListener {
        @Override
        public void onComplete(LQSession session, JSONObject json,
                Header[] headers, StatusLine status) {
            // Notify the user
            Toast.makeText(LinkListActivity.this,
                    json.optString("error_description"), Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onFailure(LQSession session, LQException e) {
            Log.e(TAG, "Failed to expire share link!", e);
            
            // Notify the user
            Toast.makeText(LinkListActivity.this,
                    "Failed to expire share link!", Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onSuccess(LQSession session, JSONObject json,
                Header[] headers) {
            // Notify the user
            Toast.makeText(LinkListActivity.this,
                    "Link expired!", Toast.LENGTH_SHORT).show();
        }
    }

    public void onServiceConnected(LQService service) {
        Log.d(TAG, "onServiceConnected");
        
        if (getListAdapter() != null) {
            // Bail out if our list adapter has already
            // been populated!
            return;
        }
        onRefreshRequested(service);
    }

    public void onRefreshRequested(LQService service) {
        LQSession session = service.getSession();
        
        if (session == null) {
            // Bail!
            // TODO: This is a huge hack. We should always return a valid
            //       session from LQService.
            return;
        }
        
        session.runGetRequest("link/list", new OnRunApiRequestListener() {
            @Override
            public void onSuccess(LQSession session, JSONObject json,
                    Header[] headers) {
                // Create our list adapter
                mAdapter = new LinkListAdapter(LinkListActivity.this);
                
                try {
                    mItems = json.getJSONArray("links");
                    
                    for (int i = 0; i < mItems.length(); i++) {
                        mAdapter.add(mItems.getJSONObject(i));
                    }
                    setListAdapter(mAdapter);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse the list of trips!", e);
                } catch (IllegalStateException e) {
                    // The Fragment was probably detached while the
                    // request was in-progress. We should cancel
                    // the request when this happens.
                }
            }
            @Override
            public void onFailure(LQSession session, LQException e) {
                Log.e(TAG, "Failed to load the trip list!", e);
                
                // Set an empty adapter on the list
                setListAdapter(new LinkListAdapter(LinkListActivity.this));
            }
            @Override
            public void onComplete(LQSession session, JSONObject json,
                    Header[] headers, StatusLine status) {
                Log.d(TAG, status.toString());
                Log.e(TAG, "Failed to load the trip list!");
                
                // Set an empty adapter on the list
                setListAdapter(new LinkListAdapter(LinkListActivity.this));
            }
        });
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
                
                // Update the ListView
                LinkListActivity.this.onServiceConnected(mService);
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
