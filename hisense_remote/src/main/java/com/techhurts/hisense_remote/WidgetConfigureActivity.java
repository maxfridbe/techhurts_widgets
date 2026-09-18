package com.techhurts.hisense_remote;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.kunal52.AndroidRemoteContext;
import com.kunal52.AndroidRemoteTv;
import com.kunal52.AndroidTvListener;
import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WidgetConfigureActivity extends Activity {

    public static class Device {
        public final String name;
        public final String ip;
        public final String protocol;

        public Device(String name, String ip, String protocol) {
            this.name = name;
            this.ip = ip;
            this.protocol = protocol;
        }

        @Override
        public String toString() {
            return name + " (" + ip + ")";
        }
    }

    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    EditText editIp;
    RadioGroup groupProtocol;
    Spinner spinnerDevices;
    TextView txtScanStatus;
    Button btnTest;
    Button btnSave;

    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListenerCast;
    private NsdManager.DiscoveryListener mDiscoveryListenerRemote;

    private final List<Device> mDevices = new ArrayList<>();
    private ArrayAdapter<Device> mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_configure);

        editIp = findViewById(R.id.edit_ip);
        groupProtocol = findViewById(R.id.group_protocol);
        spinnerDevices = findViewById(R.id.spinner_devices);
        txtScanStatus = findViewById(R.id.txt_scan_status);
        btnTest = findViewById(R.id.btn_test);
        btnSave = findViewById(R.id.btn_save);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        // Setup Spinner
        mDevices.add(new Device("Enter IP Manually...", "", "roku"));
        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mDevices);
        mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDevices.setAdapter(mAdapter);

        spinnerDevices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Device selected = mDevices.get(position);
                if (position > 0) {
                    editIp.setText(selected.ip);
                    if ("android_tv".equals(selected.protocol)) {
                        ((RadioButton) findViewById(R.id.radio_androidtv)).setChecked(true);
                    } else if ("roku".equals(selected.protocol)) {
                        ((RadioButton) findViewById(R.id.radio_roku)).setChecked(true);
                    } else if ("ascii".equals(selected.protocol)) {
                        ((RadioButton) findViewById(R.id.radio_ascii)).setChecked(true);
                    } else if ("hex".equals(selected.protocol)) {
                        ((RadioButton) findViewById(R.id.radio_hex)).setChecked(true);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Load existing preference if any
        SharedPreferences prefs = getSharedPreferences("com.techhurts.hisense_remote.prefs", 0);
        String existingIp = prefs.getString("ip_" + mAppWidgetId, "");
        String existingProto = prefs.getString("protocol_" + mAppWidgetId, "android_tv");

        if (!existingIp.isEmpty()) {
            editIp.setText(existingIp);
        }
        if ("android_tv".equals(existingProto)) {
            ((RadioButton) findViewById(R.id.radio_androidtv)).setChecked(true);
        } else if ("roku".equals(existingProto)) {
            ((RadioButton) findViewById(R.id.radio_roku)).setChecked(true);
        } else if ("ascii".equals(existingProto)) {
            ((RadioButton) findViewById(R.id.radio_ascii)).setChecked(true);
        } else if ("hex".equals(existingProto)) {
            ((RadioButton) findViewById(R.id.radio_hex)).setChecked(true);
        }

        btnTest.setOnClickListener(v -> {
            String ip = editIp.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(this, "Please enter an IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            String protocol = getSelectedProtocol();
            btnTest.setEnabled(false);
            btnTest.setText("Testing...");
            executorService.submit(() -> {
                boolean success = RemoteCommandExecutor.testConnection(WidgetConfigureActivity.this, ip, protocol);
                runOnUiThread(() -> {
                    btnTest.setEnabled(true);
                    btnTest.setText("Test Connection");
                    if (success) {
                        Toast.makeText(WidgetConfigureActivity.this, "Connection Successful!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(WidgetConfigureActivity.this, "Connection Failed. Check IP and network.", Toast.LENGTH_LONG).show();
                    }
                });
            });
        });

        btnSave.setOnClickListener(v -> {
            String ip = editIp.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(this, "Please enter an IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            String protocol = getSelectedProtocol();

            if ("android_tv".equals(protocol)) {
                // A keystore file on disk proves nothing: a failed pairing
                // leaves one behind, and skipping pairing on that basis saves a
                // widget that can never talk to the TV. Trust only a pairing
                // this app saw complete, per TV.
                if (isPairedWith(ip)) {
                    saveAndFinish(ip, protocol);
                } else {
                    btnSave.setEnabled(false);
                    btnSave.setText("Pairing...");
                    handleAndroidTvPairing(ip, () -> {
                        btnSave.setEnabled(true);
                        btnSave.setText("Save Widget");
                        saveAndFinish(ip, protocol);
                    });
                }
            } else {
                saveAndFinish(ip, protocol);
            }
        });

        // Start Discovery
        startDiscovery();
        discoverRokuDevices();
    }

    private String getSelectedProtocol() {
        int checkedId = groupProtocol.getCheckedRadioButtonId();
        if (checkedId == R.id.radio_androidtv) {
            return "android_tv";
        } else if (checkedId == R.id.radio_ascii) {
            return "ascii";
        } else if (checkedId == R.id.radio_hex) {
            return "hex";
        } else {
            return "roku";
        }
    }

    private void saveAndFinish(String ip, String protocol) {
        SharedPreferences prefs = getSharedPreferences("com.techhurts.hisense_remote.prefs", 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("ip_" + mAppWidgetId, ip);
        editor.putString("protocol_" + mAppWidgetId, protocol);
        editor.apply();

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        HisenseRemoteWidgetProvider.updateWidget(this, appWidgetManager, mAppWidgetId);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }


    private static final String PREF_PAIRED_PREFIX = "paired_";

    private boolean isPairedWith(String ip) {
        return getSharedPreferences("com.techhurts.hisense_remote.prefs", 0)
                .getBoolean(PREF_PAIRED_PREFIX + ip, false)
                && new File(getFilesDir(), "androidtv.keystore").exists();
    }

    private void rememberPairing(String ip) {
        getSharedPreferences("com.techhurts.hisense_remote.prefs", 0).edit()
                .putBoolean(PREF_PAIRED_PREFIX + ip, true).apply();
    }

    /** Clears a half-finished pairing so the next attempt starts clean. */
    private void forgetPairing(String ip) {
        getSharedPreferences("com.techhurts.hisense_remote.prefs", 0).edit()
                .remove(PREF_PAIRED_PREFIX + ip).apply();
        File keystore = new File(getFilesDir(), "androidtv.keystore");
        if (keystore.exists()) keystore.delete();
    }

    private void handleAndroidTvPairing(String ip, Runnable onSuccess) {
        File keystore = new File(getFilesDir(), "androidtv.keystore");
        if (keystore.exists()) {
            keystore.delete();
        }
        AndroidRemoteContext.getInstance().setKeyStoreFile(keystore);

        final AndroidRemoteTv remoteTv = new AndroidRemoteTv();
        // Pairing opens a TLS socket, so it must not touch the main thread.
        executorService.submit(() -> {
        try {
            remoteTv.connect(ip, new AndroidTvListener() {
                @Override public void onSessionCreated() {}

                @Override
                public void onSecretRequested() {
                    runOnUiThread(() -> {
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(WidgetConfigureActivity.this);
                        builder.setTitle("Enter Pairing Code");
                        builder.setMessage("Enter the 6-character code shown on your TV screen:");

                        final EditText input = new EditText(WidgetConfigureActivity.this);
                        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                        builder.setView(input);

                        builder.setPositiveButton("OK", (dialog, which) -> {
                            String pin = input.getText().toString().trim();
                            if (!pin.isEmpty()) {
                                executorService.submit(() -> {
                                    remoteTv.sendSecret(pin);
                                });
                            }
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> {
                            dialog.cancel();
                            executorService.submit(remoteTv::disconnect);
                            runOnUiThread(() -> {
                                btnSave.setEnabled(true);
                                btnSave.setText("Save Widget");
                            });
                        });
                        builder.show();
                    });
                }

                @Override
                public void onPaired() {
                    runOnUiThread(() -> Toast.makeText(WidgetConfigureActivity.this, "Paired successfully!", Toast.LENGTH_SHORT).show());
                }

                @Override public void onConnectingToRemote() {}

                @Override
                public void onConnected() {
                    rememberPairing(ip);
                    // Closing the session writes to a TLS socket, which throws
                    // NetworkOnMainThreadException (and killed the app here).
                    executorService.submit(remoteTv::disconnect);
                    runOnUiThread(() -> {
                        Toast.makeText(WidgetConfigureActivity.this, "TV Connected!", Toast.LENGTH_SHORT).show();
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    });
                }

                @Override public void onDisconnect() {}

                @Override
                public void onError(String error) {
                    forgetPairing(ip);
                    executorService.submit(remoteTv::disconnect);
                    runOnUiThread(() -> {
                        Toast.makeText(WidgetConfigureActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                        btnSave.setEnabled(true);
                        btnSave.setText("Save Widget");
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            forgetPairing(ip);
            runOnUiThread(() -> {
                Toast.makeText(WidgetConfigureActivity.this, "Pairing failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                btnSave.setEnabled(true);
                btnSave.setText("Save Widget");
            });
        }
        });
    }

    private void addDiscoveredDevice(String name, String ip, String protocol) {
        runOnUiThread(() -> {
            // Avoid duplicate IPs
            for (Device d : mDevices) {
                if (d.ip.equals(ip)) return;
            }
            Device dev = new Device(name, ip, protocol);
            mDevices.add(dev);
            mAdapter.notifyDataSetChanged();
            txtScanStatus.setText("TVs Discovered: " + (mDevices.size() - 1));
        });
    }

    private void startDiscovery() {
        mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        mDiscoveryListenerCast = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                runOnUiThread(() -> txtScanStatus.setText("Discovery Start Failed"));
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {}

            @Override
            public void onDiscoveryStarted(String serviceType) {}

            @Override
            public void onDiscoveryStopped(String serviceType) {}

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                mNsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {}

                    @Override
                    public void onServiceResolved(NsdServiceInfo resolvedServiceInfo) {
                        String ip = resolvedServiceInfo.getHost().getHostAddress();
                        String name = resolvedServiceInfo.getServiceName();
                        addDiscoveredDevice(name, ip, "android_tv");
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {}
        };

        mDiscoveryListenerRemote = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {}

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {}

            @Override
            public void onDiscoveryStarted(String serviceType) {}

            @Override
            public void onDiscoveryStopped(String serviceType) {}

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                mNsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {}

                    @Override
                    public void onServiceResolved(NsdServiceInfo resolvedServiceInfo) {
                        String ip = resolvedServiceInfo.getHost().getHostAddress();
                        String name = resolvedServiceInfo.getServiceName();
                        addDiscoveredDevice(name, ip, "android_tv");
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {}
        };

        try {
            mNsdManager.discoverServices("_googlecast._tcp", NsdManager.PROTOCOL_DNS_SD, mDiscoveryListenerCast);
            mNsdManager.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, mDiscoveryListenerRemote);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopDiscovery() {
        if (mNsdManager != null) {
            try {
                if (mDiscoveryListenerCast != null) mNsdManager.stopServiceDiscovery(mDiscoveryListenerCast);
                if (mDiscoveryListenerRemote != null) mNsdManager.stopServiceDiscovery(mDiscoveryListenerRemote);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void discoverRokuDevices() {
        executorService.submit(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setSoTimeout(3000);

                String mSearch = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: roku:ecp\r\n\r\n";

                byte[] sendData = mSearch.getBytes(StandardCharsets.UTF_8);
                InetAddress group = InetAddress.getByName("239.255.255.250");
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, group, 1900);
                socket.send(sendPacket);

                byte[] recvBuf = new byte[1024];
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < 3000) {
                    DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                    try {
                        socket.receive(recvPacket);
                        String resp = new String(recvPacket.getData(), 0, recvPacket.getLength());
                        if (resp.contains("Location:")) {
                            String loc = extractHeader(resp, "Location");
                            if (loc != null) {
                                URL url = new URL(loc);
                                String ip = url.getHost();
                                String friendlyName = queryRokuName(ip);
                                addDiscoveredDevice(friendlyName, ip, "roku");
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (socket != null) socket.close();
            }
        });
    }

    private String extractHeader(String response, String header) {
        String[] lines = response.split("\r\n");
        for (String line : lines) {
            if (line.toLowerCase().startsWith(header.toLowerCase() + ":")) {
                return line.substring(header.length() + 1).trim();
            }
        }
        return null;
    }

    private String queryRokuName(String ip) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://" + ip + ":8060/query/device-info");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String xml = sb.toString();
            int start = xml.indexOf("<user-device-name>");
            int end = xml.indexOf("</user-device-name>");
            if (start != -1 && end != -1) {
                return xml.substring(start + 18, end);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return "Hisense Roku TV";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopDiscovery();
        executorService.shutdownNow();
    }
}
