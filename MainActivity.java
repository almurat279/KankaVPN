package com.kankavpn.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int PICK_CONFIG = 10;
    private static final int VPN_PERMISSION = 11;
    private GoBackend backend;
    private Config config;
    private TextView status;
    private Button connectButton;
    private boolean connected = false;

    private final Tunnel tunnel = new Tunnel() {
        @Override public String getName() { return "KankaVPN"; }
        @Override public void onStateChange(State newState) {
            runOnUiThread(() -> setUi(newState == State.UP));
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.kankavpn.app.R.layout.activity_main);
        backend = new GoBackend(getApplicationContext());
        status = findViewById(R.id.status);
        connectButton = findViewById(R.id.connectButton);
        Button configButton = findViewById(R.id.configButton);

        configButton.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, PICK_CONFIG);
        });
        connectButton.setOnClickListener(v -> {
            if (connected) disconnect(); else requestVpnAndConnect();
        });
    }

    private void requestVpnAndConnect() {
        Intent permission = VpnService.prepare(this);
        if (permission != null) startActivityForResult(permission, VPN_PERMISSION);
        else connect();
    }

    private void connect() {
        if (config == null) return;
        new Thread(() -> {
            try { backend.setState(tunnel, Tunnel.State.UP, config); }
            catch (Exception e) { showError(e); }
        }).start();
    }

    private void disconnect() {
        new Thread(() -> {
            try { backend.setState(tunnel, Tunnel.State.DOWN, null); }
            catch (Exception e) { showError(e); }
        }).start();
    }

    private void setUi(boolean up) {
        connected = up;
        status.setText(up ? "Bağlandı ✓" : "Bağlı değil");
        connectButton.setText(up ? "BAĞLANTIYI KES" : "BAĞLAN");
    }

    private void showError(Exception e) {
        runOnUiThread(() -> Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONFIG && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                config = Config.parse(in);
                connectButton.setEnabled(true);
                status.setText("Ayar hazır — bağlanabilirsin");
            } catch (Exception e) { showError(e); }
        } else if (requestCode == VPN_PERMISSION && resultCode == RESULT_OK) {
            connect();
        }
    }
}
