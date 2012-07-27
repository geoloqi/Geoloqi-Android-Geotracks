package com.geoloqi.trips.widget;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONObject;

import android.annotation.TargetApi;
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
 * should be used for displaying share link details in a list.
 * 
 * @author Tristan Waddington
 */
public class LinkListAdapter extends ArrayAdapter<JSONObject> {
    private LayoutInflater mInflater;
    
    public LinkListAdapter(Context context) {
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
        JSONObject link = getItem(position);
        
        // Is the link still active?
        boolean isActive = link.optInt("currently_active") > 0;
        if (!isActive) {
            // TODO: Inactive link!
            //convertView.setAlpha(.6f);
        } else {
            // TODO: Active link!
            //convertView.setAlpha(1);
        }
        
        // Format the first line of text
        String description = link.optString("description");
        if (!TextUtils.isEmpty(description)) {
            holder.text1.setText(description);
        } else {
            holder.text1.setText(link.optString("shortlink"));
        }
        
        // Format the link created at timestamp
        String createdAt = formatTimestamp(
                link.optLong("date_creaated_ts") * 1000);
        
        // Format the second line of text
        String locationName = link.optString("start_location_name");
        if (!TextUtils.isEmpty(locationName)) {
            holder.text2.setText(String.format("%s | %s",
                    createdAt, locationName));
        } else {
            holder.text2.setText(createdAt);
        }
        
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
