package com.autowp.canreader;

import android.content.Context;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.autowp.can.CanFrame;
import com.autowp.can.CanMessage;

import java.util.List;
import java.util.Locale;

/**
 * Created by autow on 31.01.2016.
 */
public class MonitorCanMessageListAdapter extends ArrayAdapter<MonitorCanMessage> {
    public MonitorCanMessageListAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    public MonitorCanMessageListAdapter(Context context, int resource, List<MonitorCanMessage> items) {
        super(context, resource, items);
    }

    public void updateView(View v, MonitorCanMessage frame)
    {
        TextView textViewPeriod = (TextView) v.findViewById(R.id.listitem_monitor_period);
        textViewPeriod.setText(String.format(Locale.getDefault(), "%dms", frame.getPeriod()));

        TextView textViewCount = (TextView) v.findViewById(R.id.listitem_monitor_count);
        textViewCount.setText(String.format(Locale.getDefault(), "%d", frame.getCount()));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;

        if (v == null) {
            LayoutInflater vi;
            vi = LayoutInflater.from(getContext());
            v = vi.inflate(R.layout.listitem_monitor, parent, false);
        }

        MonitorCanMessage message = getItem(position);

        if (message != null) {
            CanMessage canMessage = message.getCanMessage();

            TextView textViewID = (TextView) v.findViewById(R.id.listitem_monitor_id);
            if (canMessage.isExtended()) {
                textViewID.setText(String.format(Locale.ROOT, "%08X", canMessage.getId()));
            } else {
                textViewID.setText(String.format(Locale.ROOT, "%03X", canMessage.getId()));
            }

            updateView(v, message);

            byte dlc = canMessage.getDLC();

            View rtrLine = v.findViewById(R.id.listitem_monitor_rtr_line);
            View dataLine = v.findViewById(R.id.listitem_monitor_data);

            rtrLine.setVisibility(canMessage.isRTR() ? View.VISIBLE : View.GONE);
            dataLine.setVisibility(canMessage.isRTR() ? View.GONE : View.VISIBLE);

            if (canMessage.isRTR()) {

                TextView textViewData = (TextView) v.findViewById(R.id.listitem_monitor_dlc);
                textViewData.setText(String.format(Locale.getDefault(), "%d", dlc));

            } else {

                byte[] data = canMessage.getData();

                int textViewDataID[] = {
                        R.id.listitem_transmit_data0,
                        R.id.listitem_transmit_data1,
                        R.id.listitem_transmit_data2,
                        R.id.listitem_transmit_data3,
                        R.id.listitem_transmit_data4,
                        R.id.listitem_transmit_data5,
                        R.id.listitem_transmit_data6,
                        R.id.listitem_transmit_data7,
                };

                for (int i = 0; i < data.length; i++) {
                    boolean highlight = message.getChangeHolder(i).isHighlight();
                    TextView textViewData = (TextView) v.findViewById(textViewDataID[i]);
                    textViewData.setText(String.format(Locale.ROOT, "%02X", data[i] & 0xFF));
                    int color = highlight ? R.color.can_tertiary : R.color.can_on_surface;
                    textViewData.setTextColor(ContextCompat.getColor(getContext(), color));
                }

                for (int i = data.length; i < CanFrame.MAX_DLC; i++) {
                    TextView textViewData = (TextView) v.findViewById(textViewDataID[i]);
                    textViewData.setText("");
                }
            }

        }

        return v;
    }
}
