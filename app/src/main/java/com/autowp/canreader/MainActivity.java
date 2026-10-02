package com.autowp.canreader;

import android.net.Uri;
import android.content.Intent;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends ServiceConnectedActivity {
    private static final String TAG_MONITOR = "monitor";
    private static final String TAG_TRANSMIT = "transmit";
    private static final String TAG_TRACER = "tracer";
    private static final String STATE_SELECTED_TAB = "selected_tab";

    private List<TransmitCanFrame> mTxListToLoad = null;
    private int selectedTab;
    private final ActivityResultLauncher<String> createTxListLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/octet-stream"),
                    this::exportTxList
            );
    private final ActivityResultLauncher<String[]> openTxListLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::importTxList
            );

    private CanReaderService.OnTransmitChangeListener mOnTransmitChangeListener = new CanReaderService.OnTransmitChangeListener() {

        @Override
        public void handleTransmitUpdated() {
            runOnUiThread(MainActivity.this::invalidateOptionsMenu);
        }

        @Override
        public void handleTransmitUpdated(TransmitCanFrame frame) {
        }

        @Override
        public void handleSpeedChanged(double speed) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        TabLayout tabs = (TabLayout) findViewById(R.id.main_tabs);
        tabs.addTab(tabs.newTab().setText(R.string.tab_monitor));
        tabs.addTab(tabs.newTab().setText(R.string.tab_transmit));
        tabs.addTab(tabs.newTab().setText(R.string.tab_tracer));
        selectedTab = savedInstanceState == null ? 0
                : Math.max(0, Math.min(2, savedInstanceState.getInt(STATE_SELECTED_TAB)));
        initializeMainFragments(savedInstanceState == null);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showMainTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        tabs.selectTab(tabs.getTabAt(selectedTab));
        showMainTab(selectedTab);

        Intent intent = getIntent();
        Uri uri = intent.getData();
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && uri != null) {
            Log.v("CANreader", "Importing transmit list from " + uri);
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("Unable to open transmit list");
                }
                mTxListToLoad = TxListFile.read(input);
            } catch (ConfigurationException | IOException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initializeMainFragments(boolean createFragments) {
        androidx.fragment.app.FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment monitor = fragmentManager.findFragmentByTag(TAG_MONITOR);
        Fragment transmit = fragmentManager.findFragmentByTag(TAG_TRANSMIT);
        Fragment tracer = fragmentManager.findFragmentByTag(TAG_TRACER);
        if (createFragments && monitor == null && transmit == null && tracer == null) {
            monitor = new MonitorFragment();
            transmit = new TransmitFragment();
            tracer = new TracerFragment();
            fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.main_fragment_container, monitor, TAG_MONITOR)
                    .add(R.id.main_fragment_container, transmit, TAG_TRANSMIT)
                    .add(R.id.main_fragment_container, tracer, TAG_TRACER)
                    .commitNow();
        }
    }

    private void showMainTab(int position) {
        androidx.fragment.app.FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment monitor = fragmentManager.findFragmentByTag(TAG_MONITOR);
        Fragment transmit = fragmentManager.findFragmentByTag(TAG_TRANSMIT);
        Fragment tracer = fragmentManager.findFragmentByTag(TAG_TRACER);

        androidx.fragment.app.FragmentTransaction transaction = fragmentManager.beginTransaction()
                .setReorderingAllowed(true);

        if (position == 0) {
            if (monitor != null) transaction.show(monitor).setMaxLifecycle(monitor, Lifecycle.State.RESUMED);
            if (transmit != null) transaction.hide(transmit);
            if (tracer != null) transaction.hide(tracer);
        } else if (position == 1) {
            if (transmit != null) transaction.show(transmit).setMaxLifecycle(transmit, Lifecycle.State.RESUMED);
            if (monitor != null) transaction.hide(monitor);
            if (tracer != null) transaction.hide(tracer);
        } else if (position == 2) {
            if (tracer != null) transaction.show(tracer).setMaxLifecycle(tracer, Lifecycle.State.RESUMED);
            if (monitor != null) transaction.hide(monitor);
            if (transmit != null) transaction.hide(transmit);
        }
        transaction.commit();
        selectedTab = position;
        invalidateOptionsMenu();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_SELECTED_TAB, selectedTab);
        super.onSaveInstanceState(outState);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        super.onPrepareOptionsMenu(menu);

        boolean hasMessages = bound && (canReaderService.getTransmitFrames().size() > 0);

        /*Button buttonStartAll = (Button) getView().findViewById(R.id.buttonStartAll);
        buttonStartAll.setEnabled(isConnected && canReaderService.hasStoppedTransmits());

        Button buttonStopAll = (Button) getView().findViewById(R.id.buttonStopAll);
        buttonStopAll.setEnabled(isConnected && canReaderService.hasStartedTransmits());
*/

        MenuItem exportItem = menu.findItem(R.id.action_export_tx_list);
        if (exportItem != null) exportItem.setEnabled(hasMessages);
        MenuItem shareItem = menu.findItem(R.id.action_share_tx_list);
        if (shareItem != null) shareItem.setEnabled(hasMessages);


        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_connection) {
            startActivity(new Intent(this, ConnectionActivity.class));
            return true;
        } else if (itemId == R.id.action_settings) {
            return true;
        } else if (itemId == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else if (itemId == R.id.action_export_tx_list) {
            if (bound) {
                createTxListLauncher.launch("tx-list." + TxListFile.EXTENSION);
            }
            return true;
        } else if (itemId == R.id.action_share_tx_list) {
            if (bound) {
                try {
                    File txlDir = new File(getCacheDir(), "txl");
                    if (!txlDir.isDirectory() && !txlDir.mkdirs()) {
                        throw new IOException("Unable to create share directory");
                    }

                    File file = new File(txlDir, "tx-share." + TxListFile.EXTENSION);
                    try (OutputStream output = new FileOutputStream(file)) {
                        TxListFile.write(output, canReaderService.getTransmitFrames());
                    }

                    Uri fileUri = FileProvider.getUriForFile(
                            this,
                            "com.autowp.canreader.txlfileprovider",
                            file);
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                    shareIntent.setType("application/octet-stream");
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share_tx_list)));
                } catch (ConfigurationException | IOException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        } else if (itemId == R.id.action_import_tx_list) {
            if (bound) {
                openTxListLauncher.launch(new String[]{"*/*"});
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void exportTxList(Uri uri) {
        if (uri == null || !bound) {
            return;
        }

        try {
            OutputStream output = getContentResolver().openOutputStream(uri);
            if (output == null) {
                throw new IOException("Unable to open destination");
            }
            try (OutputStream stream = output) {
                TxListFile.write(stream, canReaderService.getTransmitFrames());
            }
            Toast.makeText(this, R.string.tx_list_exported, Toast.LENGTH_SHORT).show();
        } catch (ConfigurationException | IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importTxList(Uri uri) {
        if (uri == null || !bound) {
            return;
        }

        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Unable to open selected file");
            }
            canReaderService.setTransmitFrames(TxListFile.read(input));
        } catch (ConfigurationException | IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void afterConnect() {
        if (mTxListToLoad != null) {
            canReaderService.setTransmitFrames(mTxListToLoad);
            mTxListToLoad = null;
        }

        canReaderService.addListener(mOnTransmitChangeListener);
        invalidateOptionsMenu();
    }

    @Override
    protected void beforeDisconnect() {
        canReaderService.removeListener(mOnTransmitChangeListener);
    }
}
