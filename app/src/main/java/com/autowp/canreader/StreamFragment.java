package com.autowp.canreader;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.card.MaterialCardView;

import com.autowp.can.CanFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Stream (Поток) fragment - displays all CAN messages with timestamps
 * Similar to CarBusAnalyzer's "Поток" mode
 */
public class StreamFragment extends Fragment {

    private RecyclerView recyclerViewStream;
    private StreamMessageListAdapter adapter;
    private List<StreamMessage> messageList;

    private MaterialButton buttonRecord, buttonStop, buttonExport, buttonImport, buttonClear;
    private Chip chipAll, chipRx, chipTx;
    private TextView tvStatus, tvCount;

    private boolean isRecording = false;
    private int messageCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stream, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        recyclerViewStream = view.findViewById(R.id.recyclerViewStream);
        buttonRecord = view.findViewById(R.id.buttonStreamRecord);
        buttonStop = view.findViewById(R.id.buttonStreamStop);
        buttonExport = view.findViewById(R.id.buttonStreamExport);
        buttonImport = view.findViewById(R.id.buttonStreamImport);
        buttonClear = view.findViewById(R.id.buttonStreamClear);
        chipAll = view.findViewById(R.id.chipAll);
        chipRx = view.findViewById(R.id.chipRx);
        chipTx = view.findViewById(R.id.chipTx);
        tvStatus = view.findViewById(R.id.tvStreamStatus);
        tvCount = view.findViewById(R.id.tvStreamCount);

        // Setup RecyclerView
        messageList = new ArrayList<>();
        adapter = new StreamMessageListAdapter(messageList);
        recyclerViewStream.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewStream.setAdapter(adapter);

        // Setup chip group - single selection
        ChipGroup chipGroup = view.findViewById(R.id.chipAll);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds == null || checkedIds.isEmpty()) {
                chipAll.setChecked(true);
            }
        });

        // Record button
        buttonRecord.setOnClickListener(v -> startRecording());

        // Stop button
        buttonStop.setOnClickListener(v -> stopRecording());

        // Clear button
        buttonClear.setOnClickListener(v -> clearMessages());

        // Export button
        buttonExport.setOnClickListener(v -> exportStream());

        // Import button
        buttonImport.setOnClickListener(v -> importStream());
    }

    private void startRecording() {
        isRecording = true;
        buttonRecord.setEnabled(false);
        buttonStop.setEnabled(true);
        tvStatus.setText(R.string.stream_recording);
        tvStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_dark));
    }

    private void stopRecording() {
        isRecording = false;
        buttonRecord.setEnabled(true);
        buttonStop.setEnabled(false);
        tvStatus.setText(R.string.stream_idle);
        tvStatus.setTextColor(requireContext().getColor(android.R.color.darker_gray));
    }

    private void clearMessages() {
        messageList.clear();
        adapter.notifyDataSetChanged();
        messageCount = 0;
        tvCount.setText("0");
        buttonExport.setEnabled(false);
        buttonImport.setEnabled(false);
    }

    private void exportStream() {
        // TODO: Implement stream export to .trc, .csv, .asc formats
    }

    private void importStream() {
        // TODO: Implement stream import from .trc, .csv, .asc formats
    }

    /**
     * Add a message to the stream
     */
    public void addMessage(StreamMessage message) {
        if (!isRecording && !isConnected()) {
            return;
        }
        
        messageList.add(message);
        messageCount++;
        tvCount.setText(String.valueOf(messageCount));
        adapter.notifyItemInserted(messageList.size() - 1);
        
        // Auto-scroll to bottom
        recyclerViewStream.scrollToPosition(messageList.size() - 1);
        
        // Enable export/import after first message
        buttonExport.setEnabled(true);
        buttonImport.setEnabled(true);
    }

    /**
     * Check if CAN service is connected
     */
    private boolean isConnected() {
        // TODO: Check connection status via CanReaderService
        return false;
    }

    /**
     * Stream message data class
     */
    public static class StreamMessage {
        public final long timestamp;
        public final String channel;
        public final boolean isTx;
        public final CanFrame frame;

        public StreamMessage(long timestamp, String channel, boolean isTx, CanFrame frame) {
            this.timestamp = timestamp;
            this.channel = channel;
            this.isTx = isTx;
            this.frame = frame;
        }
    }

    /**
     * Stream message adapter
     */
    public static class StreamMessageListAdapter extends RecyclerView.Adapter<StreamMessageListAdapter.ViewHolder> {
        private final List<StreamMessage> messages;

        public StreamMessageListAdapter(List<StreamMessage> messages) {
            this.messages = messages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.listitem_stream, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StreamMessage msg = messages.get(position);
            
            // Format timestamp
            String timeStr = formatTimestamp(msg.timestamp);
            holder.tvTime.setText(timeStr);
            
            // Channel
            holder.tvChannel.setText(msg.channel);
            
            // Direction indicator
            holder.tvDirection.setText(msg.isTx ? "Tx" : "Rx");
            holder.tvDirection.setTextColor(msg.isTx 
                    ? holder.itemView.getContext().getColor(android.R.color.holo_blue_dark)
                    : holder.itemView.getContext().getColor(android.R.color.holo_green_dark));
            
            // CAN ID
            holder.tvId.setText(String.format("0x%03X", msg.frame.getId()));
            
            // DLC
            holder.tvDlc.setText(String.valueOf(msg.frame.getDLC()));
            
            // Data
            holder.tvData.setText(formatData(msg.frame.getData()));
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTime, tvChannel, tvDirection, tvId, tvDlc, tvData;
            
            ViewHolder(View itemView) {
                super(itemView);
                tvTime = itemView.findViewById(R.id.tvStreamTime);
                tvChannel = itemView.findViewById(R.id.tvStreamChannel);
                tvDirection = itemView.findViewById(R.id.tvStreamDirection);
                tvId = itemView.findViewById(R.id.tvStreamId);
                tvDlc = itemView.findViewById(R.id.tvStreamDlc);
                tvData = itemView.findViewById(R.id.tvStreamData);
            }
        }

        private String formatTimestamp(long timestamp) {
            // Format as HH:MM:SS.mmm
            long seconds = timestamp / 1000;
            long millis = timestamp % 1000;
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;
            return String.format("%02d:%02d:%02d.%03d", hours, minutes, secs, millis);
        }

        private String formatData(byte[] data) {
            if (data == null || data.length == 0) return "";
            StringBuilder sb = new StringBuilder();
            for (byte b : data) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(String.format("%02X", b & 0xFF));
            }
            return sb.toString();
        }
    }
}
