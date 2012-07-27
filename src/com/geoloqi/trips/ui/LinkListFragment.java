package com.geoloqi.trips.ui;

import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.geoloqi.android.sdk.LQException;
import com.geoloqi.android.sdk.LQSession;
import com.geoloqi.android.sdk.LQSession.OnRunApiRequestListener;
import com.geoloqi.android.sdk.service.LQService;
import com.geoloqi.trips.R;
import com.geoloqi.trips.ui.MainActivity.LQServiceConnection;
import com.geoloqi.trips.widget.LinkListAdapter;

/**
 * An implementation of {@link ListFragment} for displaying
 * the currently authenticated user's list of share links.
 * 
 * @author Tristan Waddington
 */
public class LinkListFragment extends SherlockListFragment implements
        OnItemClickListener, LQServiceConnection {
    private static final String TAG = "LinkListFragment";
    
    private JSONArray mItems;
    private LinkListAdapter mAdapter;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        // Configure our ListView
        ListView lv = getListView();
        lv.setFastScrollEnabled(false);
        lv.setOnItemClickListener(this);
        
        // Set the default text
        setEmptyText(getString(R.string.empty_link_list));
        
        // Register our context menu
        registerForContextMenu(lv);
        
        LQService service = ((MainActivity) getActivity()).getService();
        if (service != null) {
            onServiceConnected(service);
        }
    }
    
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Pass
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        
        // Inflate our context menu
        MenuInflater inflater = getActivity().getMenuInflater();
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
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
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
            LQService service = ((MainActivity) getActivity()).getService();
            if (service != null) {
                LQSession session = service.getSession();
                if (session != null) {
                    JSONObject data = new JSONObject();
                    data.put("token", link.optString("token"));
                    session.runPostRequest("link/expire", data,
                            new ExpireLinkListener());
                }
            }
        } catch (JSONException e) {
            // Notify the user
            Toast.makeText(getActivity(),
                    "Failed to expire share link!", Toast.LENGTH_SHORT).show();
        }
    }

    /** Handle the server response from an expire link request. */
    private class ExpireLinkListener implements OnRunApiRequestListener {
        @Override
        public void onComplete(LQSession session, JSONObject json,
                Header[] headers, StatusLine status) {
            // Notify the user
            Toast.makeText(getActivity(),
                    json.optString("error_description"), Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onFailure(LQSession session, LQException e) {
            Log.e(TAG, "Failed to expire share link!", e);
            
            // Notify the user
            Toast.makeText(getActivity(),
                    "Failed to expire share link!", Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onSuccess(LQSession session, JSONObject json,
                Header[] headers) {
            // Notify the user
            Toast.makeText(getActivity(),
                    "Link expired!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onServiceConnected(LQService service) {
        Log.d(TAG, "onServiceConnected");
        
        if (getListAdapter() != null) {
            // Bail out if our list adapter has already
            // been populated!
            return;
        }
        onRefreshRequested(service);
    }

    @Override
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
                Activity activity = getActivity();
                if (activity != null) {
                    // Create our list adapter
                    mAdapter = new LinkListAdapter(activity);
                    
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
            }
            @Override
            public void onFailure(LQSession session, LQException e) {
                Log.e(TAG, "Failed to load the trip list!", e);
                
                // Set an empty adapter on the list
                setListAdapter(new LinkListAdapter(getActivity()));
            }
            @Override
            public void onComplete(LQSession session, JSONObject json,
                    Header[] headers, StatusLine status) {
                Log.d(TAG, status.toString());
                Log.e(TAG, "Failed to load the trip list!");
                
                // Set an empty adapter on the list
                setListAdapter(new LinkListAdapter(getActivity()));
            }
        });
    }
}
