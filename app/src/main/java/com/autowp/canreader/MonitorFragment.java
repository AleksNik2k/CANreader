package com.autowp.canreader;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Locale;

public class MonitorFragment extends ServiceConnectedFragment {

    private MonitorCanMessageListAdapter adapter;

    private ListView mListView;

    private CanReaderService.OnMonitorChangedListener mOnMonitorChangedListener = new CanReaderService.OnMonitorChangedListener() {
        @Override
        public void handleMonitorUpdated() {
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        }

        @Override
        public void handleMonitorUpdated(final MonitorCanMessage message) {
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mListView == null || adapter == null) {
                            return;
                        }
                        int start = mListView.getFirstVisiblePosition();
                        int end = mListView.getLastVisiblePosition();
                        for(int i=start; i<=end; i++)
                            if (message == mListView.getItemAtPosition(i)){
                                View view = mListView.getChildAt(i-start);
                                if (view != null) {
                                    adapter.updateView(view, message);
                                }
                                break;
                            }
                    }
                });
            }
        }

        @Override
        public void handleSpeedChanged(final double speed) {
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        View view = getView();
                        if (view == null) {
                            return;
                        }
                        TextView tv = (TextView)view.findViewById(R.id.textViewMonitorSpeed);
                        if (tv != null) {
                            tv.setText(String.format(Locale.getDefault(), "%.2f frames/sec", speed));
                        }
                    }
                });
            }
        }
    };

    public MonitorFragment() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_monitor, container, false);

        mListView = (ListView)view.findViewById(R.id.listViewMonitor);
        if (adapter != null) {
            mListView.setAdapter(adapter);
        }

        registerForContextMenu(mListView);

        Button buttonMonitorClear = (Button) view.findViewById(R.id.buttonMonitorClear);
        buttonMonitorClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                canReaderService.clearMonitor();
            }
        });

        return view;
    }

    @Override
    protected void afterConnect() {
        canReaderService.addListener(mOnMonitorChangedListener);

        adapter = new MonitorCanMessageListAdapter(
                getActivity().getApplicationContext(),
                R.layout.listitem_monitor,
                canReaderService.getMonitorFrames()
        );

        mListView.setAdapter(adapter);
    }

    @Override
    protected void beforeDisconnect() {
        canReaderService.removeListener(mOnMonitorChangedListener);
        if (mListView != null) {
            mListView.setAdapter(null);
        }
        adapter = null;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (v.getId() == R.id.listViewMonitor) {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.monitor_item_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (isVisible() && bound && canReaderService != null && adapter != null) {
            int itemId = item.getItemId();
            if (itemId == R.id.action_monitor_delete) {
                AdapterView.AdapterContextMenuInfo info = getContextMenuInfo(item);
                if (info != null && info.position >= 0 && info.position < adapter.getCount()) {
                    canReaderService.removeMonitor(info.position);
                    return true;
                }
            } else if (itemId == R.id.action_monitor_copy || itemId == R.id.action_monitor_focus) {
                AdapterView.AdapterContextMenuInfo info = getContextMenuInfo(item);
                if (info == null || info.position < 0 || info.position >= adapter.getCount()) {
                    return super.onContextItemSelected(item);
                }
                MonitorCanMessage message = adapter.getItem(info.position);
                if (message != null) {
                    if (itemId == R.id.action_monitor_copy) {
                        ClipboardManager clipboard = (ClipboardManager) getActivity()
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("CAN message", message.getCanMessage().toString());
                        clipboard.setPrimaryClip(clip);
                        return true;
                    } else {
                        Intent intent = new Intent(getActivity(), MessageActivity.class);
                        intent.putExtra(MessageActivity.EXTRA_CAN_ID, message.getCanMessage().getId());
                        startActivity(intent);
                        return true;
                    }
                }
            }
        }
        return super.onContextItemSelected(item);
    }

    private AdapterView.AdapterContextMenuInfo getContextMenuInfo(MenuItem item) {
        if (item.getMenuInfo() instanceof AdapterView.AdapterContextMenuInfo) {
            return (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        }
        return null;
    }
}
