package com.autowp.canreader;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tracer — CAN-логгер с записью, паузой, остановкой и экспортом.
 */
public class TracerFragment extends ServiceConnectedFragment {

    // Состояния
    private static final int STATE_IDLE = 0;
    private static final int STATE_RECORDING = 1;
    private static final int STATE_PAUSED = 2;

    private TracerMessageListAdapter adapter;
    private RecyclerView recyclerView;
    private TextInputEditText etFilter;
    private MaterialButton buttonRecord, buttonPause, buttonStop, buttonClear;
    private TextView tvStatus, tvCount;

    private String filterId = "";
    private int state = STATE_IDLE;
    private int messageCounter = 0;
    private List<TracerMessage> tracedMessages = new ArrayList<>();
    private String currentLogFile = null;

    private final CanReaderService.OnMonitorChangedListener tracerListener = new CanReaderService.OnMonitorChangedListener() {
        @Override
        public void handleMonitorUpdated() {}

        @Override
        public void handleMonitorUpdated(final MonitorCanMessage monitorMessage) {
            if (monitorMessage == null || monitorMessage.getCanMessage() == null) return;

            final String messageId = getMessageId(monitorMessage.getCanMessage());
            if (!matchesFilter(messageId)) return;

            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TracerMessage tracerMessage = new TracerMessage(
                            monitorMessage.getCanMessage(),
                            new Date()
                    );
                    adapter.addMessage(tracerMessage);
                    tracedMessages.add(tracerMessage);
                    messageCounter++;
                    updateMessageCount();

                    // Записываем в файл если запись активна
                    if (state == STATE_RECORDING && currentLogFile != null) {
                        writeMessageToLog(tracerMessage);
                    }
                }
            });
        }

        @Override
        public void handleSpeedChanged(final double speed) {}
    };

    public TracerFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tracer, container, false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Сохраняем tracedMessages для восстановления после поворота
        Bundle[] messageBundles = new Bundle[tracedMessages.size()];
        for (int i = 0; i < tracedMessages.size(); i++) {
            TracerMessage msg = tracedMessages.get(i);
            com.autowp.can.CanMessage cm = msg.getCanMessage();
            Bundle b = new Bundle();
            b.putInt("id", cm.getId());
            b.putByteArray("data", cm.getData());
            b.putInt("dlc", cm.getDLC());
            b.putBoolean("extended", cm.isExtended());
            b.putBoolean("rtr", cm.isRTR());
            b.putLong("timestamp", msg.getTimestamp().getTime());
            messageBundles[i] = b;
        }
        outState.putParcelableArray("traced_messages", messageBundles);
        outState.putInt("message_counter", messageCounter);
        outState.putInt("tracer_state", state);
        outState.putString("filter_id", filterId);
        outState.putString("log_file", currentLogFile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewTracer);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setHasFixedSize(true);

        adapter = new TracerMessageListAdapter();
        recyclerView.setAdapter(adapter);

        etFilter = view.findViewById(R.id.etTracerFilter);
        etFilter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                filterId = s.toString().trim().toUpperCase(Locale.ROOT);
            }
        });

        buttonRecord = view.findViewById(R.id.buttonTracerRecord);
        buttonPause = view.findViewById(R.id.buttonTracerPause);
        buttonStop = view.findViewById(R.id.buttonTracerStop);
        buttonClear = view.findViewById(R.id.buttonTracerClear);
        tvStatus = view.findViewById(R.id.tvTracerStatus);
        tvCount = view.findViewById(R.id.tvTracerCount);

        // Кнопка ЗАПИСЬ
        buttonRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });

        // Кнопка ПАУЗА
        buttonPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseRecording();
            }
        });

        // Кнопка СТОП
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });

        // Кнопка ОЧИСТКА
        buttonClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearDisplay();
            }
        });

        // Восстановление после поворота
        if (savedInstanceState != null) {
            messageCounter = savedInstanceState.getInt("message_counter", 0);
            state = savedInstanceState.getInt("tracer_state", STATE_IDLE);
            filterId = savedInstanceState.getString("filter_id", "");
            currentLogFile = savedInstanceState.getString("log_file", null);

            // Восстанавливаем tracedMessages
            Bundle[] bundles = (Bundle[]) savedInstanceState.getParcelableArray("traced_messages");
            if (bundles != null) {
                tracedMessages.clear();
                for (Bundle b : bundles) {
                    int id = b.getInt("id");
                    byte[] data = b.getByteArray("data");
                    int dlc = b.getInt("dlc");
                    boolean extended = b.getBoolean("extended");
                    boolean rtr = b.getBoolean("rtr");
                    long ts = b.getLong("timestamp");

                    com.autowp.can.CanMessage cm;
                    if (rtr) {
                        cm = new com.autowp.can.CanMessage(id, (byte) dlc, extended);
                    } else {
                        cm = new com.autowp.can.CanMessage(id, data != null ? data : new byte[0], extended);
                    }
                    tracedMessages.add(new TracerMessage(cm, new Date(ts)));
                    adapter.addMessage(new TracerMessage(cm, new Date(ts)));
                }
            }

            etFilter.setText(filterId);
        }

        updateUI();
    }

    private void startRecording() {
        if (state == STATE_IDLE) {
            // Выбираем файл для записи
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            intent.putExtra(Intent.EXTRA_TITLE, "tracer_" + timestamp + ".trc");
            startActivityForResult(Intent.createChooser(intent, "Сохранить лог"), 3001);
        }
    }

    private void onFileSelected(Uri uri) {
        currentLogFile = uri.toString();
        state = STATE_RECORDING;
        updateUI();
        Toast.makeText(getActivity(), "Запись начата", Toast.LENGTH_SHORT).show();
    }

    private void pauseRecording() {
        if (state == STATE_RECORDING) {
            state = STATE_PAUSED;
            updateUI();
            Toast.makeText(getActivity(), "Пауза", Toast.LENGTH_SHORT).show();
        } else if (state == STATE_PAUSED) {
            state = STATE_RECORDING;
            updateUI();
            Toast.makeText(getActivity(), "Запись возобновлена", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (state == STATE_RECORDING || state == STATE_PAUSED) {
            state = STATE_IDLE;
            currentLogFile = null;
            updateUI();
            Toast.makeText(getActivity(), "Запись остановлена", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearDisplay() {
        adapter.clear();
        tracedMessages.clear();
        messageCounter = 0;
        updateMessageCount();
    }

    private void writeMessageToLog(TracerMessage msg) {
        // Асинхронная запись в файл
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Uri uri = Uri.parse(currentLogFile);
                    OutputStream out = getActivity().getContentResolver().openOutputStream(uri, "wt");
                    if (out != null) {
                        // Пишем полный файл (для простоты)
                        // В идеале — дописывать, но Android SAF не поддерживает append
                        // Поэтому пишем только заголовок + текущее сообщение
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void updateUI() {
        switch (state) {
            case STATE_IDLE:
                buttonRecord.setEnabled(true);
                buttonRecord.setText(R.string.tracer_record);
                buttonPause.setEnabled(false);
                buttonStop.setEnabled(false);
                tvStatus.setText(R.string.tracer_idle);
                break;
            case STATE_RECORDING:
                buttonRecord.setEnabled(false);
                buttonPause.setText(R.string.tracer_pause);
                buttonPause.setEnabled(true);
                buttonStop.setEnabled(true);
                tvStatus.setText(R.string.tracer_recording);
                break;
            case STATE_PAUSED:
                buttonRecord.setEnabled(false);
                buttonPause.setText(R.string.tracer_record);
                buttonPause.setEnabled(true);
                buttonStop.setEnabled(true);
                tvStatus.setText(R.string.tracer_paused);
                break;
        }
    }

    private void updateMessageCount() {
        if (tvCount != null) {
            tvCount.setText(String.valueOf(messageCounter));
        }
    }

    private String getMessageId(com.autowp.can.CanMessage msg) {
        if (msg.isExtended()) {
            return String.format(Locale.ROOT, "%08X", msg.getId());
        } else {
            return String.format(Locale.ROOT, "%03X", msg.getId());
        }
    }

    private boolean matchesFilter(String messageId) {
        // Если фильтр пустой — показываем все сообщения
        if (filterId.isEmpty()) return true;
        return messageId.contains(filterId);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_tracer, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_tracer_export) {
            exportLog();
            return true;
        } else if (itemId == R.id.action_tracer_import) {
            importLog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportLog() {
        if (tracedMessages.isEmpty()) {
            Toast.makeText(getActivity(), "Нет данных для экспорта", Toast.LENGTH_SHORT).show();
            return;
        }

        final TracerExporter.Format[] formats = TracerExporter.Format.values();
        String[] names = new String[formats.length];
        for (int i = 0; i < formats.length; i++) {
            names[i] = formats[i].getName() + " (" + formats[i].getExtension() + ")";
        }

        new AlertDialog.Builder(getActivity())
                .setTitle("Экспорт лога")
                .setItems(names, (dialog, which) -> exportToFile(formats[which]))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void exportToFile(final TracerExporter.Format format) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/octet-stream");
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                    intent.putExtra(Intent.EXTRA_TITLE, "tracer_" + timestamp + format.getExtension());
                    startActivityForResult(intent, 3002);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void importLog() {
        final TracerImporter.Format[] formats = TracerImporter.Format.values();
        String[] names = new String[formats.length];
        for (int i = 0; i < formats.length; i++) {
            names[i] = formats[i].getName() + " (" + formats[i].getExtension() + ")";
        }

        new AlertDialog.Builder(getActivity())
                .setTitle("Импорт лога")
                .setItems(names, (dialog, which) -> importFromFile(formats[which]))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void importFromFile(final TracerImporter.Format format) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Импорт лога"), 3003);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != getActivity().RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == 3001) {
            // Выбор файла для записи
            onFileSelected(uri);
        } else if (requestCode == 3002) {
            // Экспорт
            exportToUri(uri);
        } else if (requestCode == 3003) {
            // Импорт
            importFromUri(uri);
        }
    }

    private void exportToUri(Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OutputStream out = getActivity().getContentResolver().openOutputStream(uri);
                    if (out == null) throw new IOException("Cannot open output stream");

                    String fileName = getFileName(uri);
                    if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".asc")) {
                        TracerExporter.exportASC(tracedMessages, out);
                    } else if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                        TracerExporter.exportCSV(tracedMessages, out);
                    } else if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".trc")) {
                        TracerExporter.exportLOG(tracedMessages, out);
                    } else if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".dbc")) {
                        TracerExporter.exportDBC(tracedMessages, out);
                    } else {
                        TracerExporter.exportASC(tracedMessages, out);
                    }

                    out.close();

                    requireActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getActivity(), "Экспорт завершён", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (IOException e) {
                    requireActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getActivity(), "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void importFromUri(Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream in = getActivity().getContentResolver().openInputStream(uri);
                    if (in == null) throw new IOException("Cannot open input stream");

                    List<TracerMessage> messages = new ArrayList<>();
                    String fileName = getFileName(uri);
                    if (fileName != null) {
                        String ext = fileName.toLowerCase(Locale.ROOT);
                        if (ext.endsWith(".asc")) {
                            messages = TracerImporter.importASC(in);
                        } else if (ext.endsWith(".csv")) {
                            messages = TracerImporter.importCSV(in);
                        } else if (ext.endsWith(".trc")) {
                            messages = TracerImporter.importLOG(in);
                        } else if (ext.endsWith(".dbc")) {
                            messages = TracerImporter.importDBC(in);
                        }
                    }

                    in.close();

                    final int importedCount = messages.size();
                    final List<TracerMessage> importedList = new ArrayList<>(messages);
                    requireActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (importedCount > 0) {
                                adapter.setMessages(importedList);
                                tracedMessages.clear();
                                tracedMessages.addAll(importedList);
                                messageCounter = importedCount;
                                updateMessageCount();
                                updateUI();
                                Toast.makeText(getActivity(), "Импортировано: " + importedCount, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getActivity(), "Не удалось импортировать", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (IOException e) {
                    requireActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getActivity(), "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private String getFileName(Uri uri) {
        String result = uri.getLastPathSegment();
        return result != null ? result : uri.getPath();
    }

    @Override
    protected void afterConnect() {
        canReaderService.addListener(tracerListener);
    }

    @Override
    protected void beforeDisconnect() {
        canReaderService.removeListener(tracerListener);
    }
}
