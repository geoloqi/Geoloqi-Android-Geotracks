package com.geoloqi.trips.ui;

import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
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
import android.widget.ListView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListFragment;
import com.geoloqi.android.sdk.LQException;
import com.geoloqi.android.sdk.LQSession;
import com.geoloqi.android.sdk.LQSession.OnRunApiRequestListener;
import com.geoloqi.android.sdk.service.LQService;
import com.geoloqi.trips.R;
import com.geoloqi.trips.ui.MainActivity.LQServiceConnection;
import com.geoloqi.trips.widget.TripListAdapter;

/**
 * An implementation of {@link ListFragment} for displaying
 * the currently authenticated user's list of trips.
 * 
 * @author Tristan Waddington
 */
public class TripListFragment extends SherlockListFragment implements
        OnItemClickListener, LQServiceConnection {
    private static final String TAG = "TripListFragment";
    
    private JSONArray mItems;
    private TripListAdapter mAdapter;
    
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
        setEmptyText(getString(R.string.empty_trip_list));
        
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
        //MenuInflater inflater = getActivity().getMenuInflater();
        //inflater.inflate(R.menu.trip_context_menu, menu);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        JSONObject trip = (JSONObject) mAdapter.getItem(info.position);
        
        /*
        switch (item.getItemId()) {
        case R.id.delete:
            deleteGeonote(info, geonote);
            return true;
        }
        */
        return false;
    }
    
    /*
    private void deleteGeonote(final AdapterContextMenuInfo info, final JSONObject geonote) {
        ArrayList<JSONObject> list = new ArrayList<JSONObject>();
        
        // We have to build an ArrayList here because JSONArray
        // lacks a remove method.
        for (int i = 0; i < mItems.length(); i++) {
            list.add(mItems.optJSONObject(i));
        }
        
        // Remove the geonote
        list.remove(info.id);
        
        // Update our data array
        mItems = new JSONArray(list);
        
        // Notify our list adapter of the update
        mAdapter.remove(((JSONObject) mAdapter.getItem(info.position)));
        
        // Notify the server that the Geonote should be deleted
        LQService service = ((MainActivity) getActivity()).getService();
        if (service != null) {
            LQSession session = service.getSession();
            if (session != null) {
                String path = String.format("trigger/delete/%s",
                        geonote.optString("geonote_id"));
                session.runPostRequest(path, new JSONObject(),
                        new DeleteGeonoteListener());
            }
        }
    }
    */

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
                    mAdapter = new TripListAdapter(activity);
                    
                    try {
                        mItems = json.getJSONArray("trips");
                        
                        // We iterate over the list in reverse order so the
                        // latest trip is at the top.
                        for (int i = (mItems.length() - 1); i >= 0; i--) {
                            mAdapter.add(mItems.optJSONObject(i));
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
                setListAdapter(new TripListAdapter(getActivity()));
            }
            @Override
            public void onComplete(LQSession session, JSONObject json,
                    Header[] headers, StatusLine status) {
                Log.d(TAG, status.toString());
                Log.e(TAG, "Failed to load the trip list!");
                
                // Set an empty adapter on the list
                setListAdapter(new TripListAdapter(getActivity()));
            }
        });
    }
}
