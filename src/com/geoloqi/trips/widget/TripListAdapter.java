package com.geoloqi.trips.widget;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONObject;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.geoloqi.trips.R;

/**
 * This class is a simple implementation of ArrayAdapter and
 * should be used for displaying trip details in a list.
 * 
 * @author Tristan Waddington
 */
public class TripListAdapter extends ArrayAdapter<JSONObject> {
    private LayoutInflater mInflater;
    
    public TripListAdapter(Context context) {
        super(context, R.layout.simple_text_list_item);
        
        // Get our layout inflater
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ImageViewHolder holder;
        
        if (convertView == null) {
            // Inflate our row layout
            convertView = mInflater.inflate(
                    R.layout.simple_text_list_item, parent, false);
            
            // Cache the row elements for efficient retrieval
            holder = new ImageViewHolder();
            holder.text1 = (TextView) convertView.findViewById(R.id.text1);
            holder.text2 = (TextView) convertView.findViewById(R.id.text2);
            
            // Store the holder object on the row
            convertView.setTag(holder);
        } else {
            holder = (ImageViewHolder) convertView.getTag();
        }
        
        // Reset our row values
        holder.text1.setText("");
        holder.text2.setText("");
        
        // Populate our data
        JSONObject trip = getItem(position);
        
        // Is the trip active?
        // TODO: Mark expired trips in the UI.
        boolean isActive = trip.optBoolean("currently_active");
        
        holder.text1.setText(trip.optString("token"));
        
        // Format the metadata line
        String subText = formatTimestamp(
                trip.optLong("date_created_ts") * 1000);
        
        String description = trip.optString("description");
        if (!TextUtils.isEmpty(description)) {
            subText = String.format("%s | %s", subText, description);
        }
        holder.text2.setText(subText);
        
        return convertView;
    }
    
    /**
     * Format the created_at timestamp.
     * 
     * @param timestamp
     * @return a formatted String.
     */
    private String formatTimestamp(long timestamp) {
        Date date = new Date(timestamp);
        
        // Create our date formatter
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, h:mma",
                Locale.US);
        
        // Override the default AM/PM String values
        DateFormatSymbols symbols = formatter.getDateFormatSymbols();
        symbols.setAmPmStrings(new String[] { "am", "pm" });
        formatter.setDateFormatSymbols(symbols);
        
        return formatter.format(date);
    }
}
