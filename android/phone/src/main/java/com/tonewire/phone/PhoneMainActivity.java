package com.tonewire.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Tonewire's local-first phone remote, intentionally implemented with platform Views. */
public final class PhoneMainActivity extends Activity implements TvConnectionClient.Listener {
    private static final int LIME = 0xffd9ff6b;
    private static final int PAPER = 0xfff4f1e7;
    private static final int MUTED = 0xffaeb5ac;
    private static final int PANEL = 0xff1c231d;
    private final Handler handler = new Handler();
    private PhoneStateStore stateStore;
    private TokenStore tokenStore;
    private TvConnectionClient client;
    private TvDiscovery discovery;
    private PhoneState state;
    private LinearLayout content;
    private TextView connection;
    private final BandWriteCoalescer bandWrites = new BandWriteCoalescer();
    private boolean pairingDialogVisible;
    private boolean endpointInUse;
    private String currentTab = "TUNE";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        stateStore = new PhoneStateStore(this);
        tokenStore = new TokenStore(this);
        state = stateStore.restore();
        endpointInUse = !stateStore.host().isEmpty();
        client = new TvConnectionClient(this, tokenStore.getOrCreateDeviceId());
        discovery = new TvDiscovery(this, new TvDiscovery.Listener() {
            @Override public void onFound(String name, String host, int port, String tvId) {
                runOnUiThread(() -> connectDiscovered(name, host, port, tvId));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> showState(state.withConnection(PhoneState.Connection.ERROR, message)));
            }
        });
        buildChrome();
        if (!stateStore.host().isEmpty()) connect(stateStore.name(), stateStore.host(), stateStore.port());
    }

    @Override protected void onStart() { super.onStart(); discovery.start(); }
    @Override protected void onStop() { discovery.stop(); super.onStop(); }
    @Override protected void onDestroy() { client.disconnect(); super.onDestroy(); }

    private void buildChrome() {
        LinearLayout root = column(16); root.setPadding(dp(18), dp(18), dp(18), dp(12)); root.setBackgroundColor(0xff101311);
        TextView mark = text("TONEWIRE", 13, LIME); mark.setLetterSpacing(.16f); root.addView(mark);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(0, dp(14), 0, dp(10));
        TextView hello = text("Good evening.", 31, PAPER); hello.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL));
        header.addView(hello, new LinearLayout.LayoutParams(0, -2, 1));
        connection = button("FIND TV"); connection.setContentDescription("Connection status. Tap to find or enter TV address.");
        connection.setOnClickListener(v -> showConnectDialog()); header.addView(connection); root.addView(header);
        ScrollView scroll = new ScrollView(this); content = column(12); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(0, dp(10), 0, 0);
        addNav(nav, "TUNE", this::showTune); addNav(nav, "PRESETS", this::showPresets); addNav(nav, "OUTPUTS", this::showOutputs); addNav(nav, "SYSTEM", this::showSystem); root.addView(nav);
        setContentView(root); showTune();
    }

    private void addNav(LinearLayout nav, String label, Runnable action) { Button item = button(label); item.setOnClickListener(v -> action.run()); nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1)); }
    private void showTune() {
        currentTab = "TUNE";
        content.removeAllViews();
        connection.setText(state.connection == PhoneState.Connection.CONNECTED ? "LIVE" : "FIND TV");
        card("ACTIVE OUTPUT", state.route + "\n" + state.status, null);
        LinearLayout preset = card("ROUTE PRESET", state.preset, null); Button reset = button("RESET"); reset.setContentDescription("Reset equalizer to flat"); reset.setEnabled(TvConnectionClient.engineReady(state)); reset.setOnClickListener(v -> reset()); preset.addView(reset);
        Button cutOnly = button("MAKE CUT-ONLY"); cutOnly.setContentDescription("Make equalizer cut-only");
        cutOnly.setEnabled(TvConnectionClient.engineReady(state) && !state.bands.isEmpty());
        cutOnly.setOnClickListener(view -> makeCutOnly()); preset.addView(cutOnly);
        if (HeadroomGuard.hasHighBoost(state.bands)) content.addView(text(
                "Boost above +6 dB may cause clipping. Try Make cut-only.", 14, 0xffffc46b));
        TextView heading = text((state.bands.isEmpty() ? "5-BAND PREVIEW" : state.bands.size() + "-BAND HARDWARE EQ"), 12, MUTED); heading.setLetterSpacing(.12f); content.addView(heading);
        Button enabled = button(state.enabled ? "EQ ON" : "EQ BYPASSED"); enabled.setEnabled(TvConnectionClient.engineReady(state)); enabled.setContentDescription("Equalizer " + (state.enabled ? "enabled" : "bypassed")); enabled.setOnClickListener(v -> { state = state.withEnabled(!state.enabled); showTune(); client.setEnabled(state.enabled); }); content.addView(enabled);
        List<PhoneState.Band> bands = state.bands.isEmpty() ? previewBands() : state.bands;
        for (PhoneState.Band band : bands) addBand(band, !state.bands.isEmpty());
        TextView note = text(state.connection == PhoneState.Connection.CONNECTED
                ? (TvConnectionClient.engineReady(state) ? "● Changes apply as you move" : state.status)
                : "Connect to use the controls", 13, TvConnectionClient.engineReady(state) ? LIME : MUTED); content.addView(note);
    }

    private void addBand(PhoneState.Band band, boolean live) {
        LinearLayout row = column(2); row.setPadding(dp(14), dp(10), dp(14), dp(10)); row.setBackgroundColor(PANEL);
        TextView label = text(frequency(band.centerHz) + "   " + decibels(band.levelMillibels), 16, PAPER); row.addView(label);
        SeekBar slider = new SeekBar(this); slider.setMax(band.maxMillibels - band.minMillibels); slider.setProgress(band.levelMillibels - band.minMillibels); slider.setEnabled(live && TvConnectionClient.engineReady(state) && state.enabled); slider.setContentDescription(frequency(band.centerHz) + " equalizer level, " + decibels(band.levelMillibels));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { if (fromUser) { int level = progress + band.minMillibels; label.setText(frequency(band.centerHz) + "   " + decibels(level)); state = state.withBandLevel(band.index, level); queueBand(band.index, level); } }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { flushBand(); }
        }); row.addView(slider); content.addView(row);
    }

    private void showPresets() {
        currentTab = "PRESETS";
        content.removeAllViews();
        card("SOUND LIBRARY", "Presets are stored on the TV and follow the active output.", null);
        java.util.List<com.tonewire.protocol.PresetCatalog.Entry> catalog = state.presets;
        if (catalog.isEmpty() && !state.supportsSavedPresets) catalog = java.util.Arrays.asList(
                new com.tonewire.protocol.PresetCatalog.Entry("flat_reference", "Flat reference", true),
                new com.tonewire.protocol.PresetCatalog.Entry("clear_dialogue", "Clear dialogue", true),
                new com.tonewire.protocol.PresetCatalog.Entry("night_watch", "Night watch", true));
        for (com.tonewire.protocol.PresetCatalog.Entry entry : catalog) {
            Button option = button(entry.name); option.setContentDescription("Apply " + entry.name + " preset");
            option.setEnabled(TvConnectionClient.engineReady(state));
            option.setOnClickListener(v -> client.applyPreset(entry.id)); content.addView(option);
            if (!entry.builtIn && state.supportsSavedPresets) {
                Button delete = button("Delete " + entry.name);
                delete.setEnabled(TvConnectionClient.engineReady(state));
                delete.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Delete " + entry.name + "?")
                        .setMessage("The current sound stays unchanged.").setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (dialog, which) -> client.deletePreset(entry.id)).show());
                content.addView(delete);
            }
        }
        if (state.supportsSavedPresets) {
            Button save = button("Save current as…"); save.setEnabled(TvConnectionClient.engineReady(state));
            save.setOnClickListener(v -> {
                EditText name = new EditText(this); name.setHint("Preset name"); name.setSingleLine();
                name.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(48)});
                new AlertDialog.Builder(this).setTitle("Save current as…").setView(name)
                        .setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog, which) -> client.savePreset(name.getText().toString().trim())).show();
            });
            content.addView(save);
        }
    }
    private void showOutputs() { currentTab = "OUTPUTS"; content.removeAllViews(); card("ROUTING", state.route, null); card("AUTOMATION", "Route-specific preset assignment is managed on the TV service.", null); }
    private void showSystem() { currentTab = "SYSTEM"; content.removeAllViews(); card("TV SERVICE", state.tvName + "\n" + state.status, null); Button diag = button("DIAGNOSTICS"); diag.setOnClickListener(v -> diagnostics()); content.addView(diag); Button pair = button("PAIR ANOTHER TV"); pair.setOnClickListener(v -> { tokenStore.clear(); showConnectDialog(); }); content.addView(pair); }
    private void diagnostics() { new AlertDialog.Builder(this).setTitle("Diagnostics").setMessage("Connection: " + state.connection + "\nRevision: " + state.revision + "\nBands advertised by TV: " + state.bands.size() + "\nRoute: " + state.route).setPositiveButton("Close", null).show(); }

    private void showConnectDialog() {
        EditText address = new EditText(this); address.setHint("192.168.x.x:49321"); address.setInputType(InputType.TYPE_CLASS_TEXT); address.setSingleLine();
        new AlertDialog.Builder(this).setTitle("Connect to TV").setMessage("Discovery is running. Or enter the TV's local IP and optional port.").setView(address).setPositiveButton("Connect", (d, w) -> {
            String[] parts = address.getText().toString().trim().split(":", 2); if (!parts[0].isEmpty()) { int port = parts.length == 2 ? parsePort(parts[1]) : 49321; connect("Living Room TV", parts[0], port); }
        }).setNegativeButton("Cancel", null).show();
    }
    private int parsePort(String value) { try { int port = Integer.parseInt(value); return port > 0 && port < 65536 ? port : 49321; } catch (Exception ignored) { return 49321; } }
    private void connectDiscovered(String name, String host, int port, String tvId) {
        if (EndpointRediscovery.shouldAdopt(stateStore.tvId(), stateStore.host(), stateStore.port(), tvId, host, port)) {
            connect(name, host, port);
            stateStore.saveTvId(tvId);
            return;
        }
        if (endpointInUse || state.connection == PhoneState.Connection.CONNECTING
                || state.connection == PhoneState.Connection.PAIRING
                || state.connection == PhoneState.Connection.CONNECTED) return;
        connect(name, host, port);
    }
    private void connect(String name, String host, int port) {
        endpointInUse = true;
        stateStore.saveEndpoint(host, port, name);
        showState(state.withConnection(PhoneState.Connection.CONNECTING, "Connecting to " + name));
        client.connect(host, port, tokenStore.get());
    }
    private void queueBand(int index, int level) { if (bandWrites.record(index, level)) handler.postDelayed(this::flushBand, 16); }
    private void flushBand() { bandWrites.flush((band, level) -> client.setBand(band, level)); }
    private void makeCutOnly() {
        try {
            List<PhoneState.Band> shifted = HeadroomGuard.cutOnly(state.bands);
            for (PhoneState.Band band : shifted) {
                PhoneState.Band previous = state.bands.stream().filter(value -> value.index == band.index).findFirst().get();
                if (previous.levelMillibels != band.levelMillibels) {
                    state = state.withBandLevel(band.index, band.levelMillibels);
                    queueBand(band.index, band.levelMillibels);
                }
            }
            flushBand();
            showTune();
        } catch (IllegalArgumentException failure) {
            new AlertDialog.Builder(this).setTitle("Cannot preserve this curve")
                    .setMessage(failure.getMessage()).setPositiveButton("OK", null).show();
        }
    }

    private void reset() { for (PhoneState.Band band : state.bands) { state = state.withBandLevel(band.index, 0); queueBand(band.index, 0); } showTune(); }
    private void showState(PhoneState replacement) { state = replacement; stateStore.saveSnapshot(state);
        switch (currentTab) {
            case "PRESETS": showPresets(); break;
            case "OUTPUTS": showOutputs(); break;
            case "SYSTEM": showSystem(); break;
            default: showTune();
        } }
    @Override public void onState(PhoneState replacement) { showState(replacement); }
    @Override public void onPairingRequired() {
        state = state.withConnection(PhoneState.Connection.PAIRING, "Enter the code shown on the TV");
        if (pairingDialogVisible) return;
        pairingDialogVisible = true;
        EditText code = new EditText(this); code.setInputType(InputType.TYPE_CLASS_NUMBER); code.setHint("Six-digit code");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Pair with TV").setMessage("Enter the short-lived code on your TV.").setView(code).setPositiveButton("Pair", (d, w) -> client.pair(code.getText().toString())).setNegativeButton("Cancel", null).create();
        dialog.setOnDismissListener(value -> pairingDialogVisible = false); dialog.show();
    }
    @Override public void onTvIdentity(String tvId) { stateStore.saveTvId(tvId); }
    @Override public void onPairingAccepted(String token) { try { tokenStore.save(token); } catch (Exception error) { onError("Could not securely save the pairing credential"); } }
    @Override public void onCommandRejected(String detail) {
        new AlertDialog.Builder(this).setTitle("Could not apply change").setMessage(detail)
                .setPositiveButton("OK", null).show();
    }
    @Override public void onError(String message) { showState(state.withConnection(PhoneState.Connection.ERROR, message)); }
    private LinearLayout column(int spacing) { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private LinearLayout card(String title, String body, View extra) { LinearLayout card = column(4); card.setPadding(dp(15), dp(13), dp(15), dp(13)); card.setBackgroundColor(PANEL); TextView top = text(title, 11, LIME); top.setLetterSpacing(.13f); card.addView(top); TextView detail = text(body, 16, PAPER); detail.setPadding(0, dp(5), 0, 0); card.addView(detail); if (extra != null) card.addView(extra); content.addView(card, new LinearLayout.LayoutParams(-1, -2)); return card; }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(0, 1.12f); return view; }
    private Button button(String label) { Button view = new Button(this); view.setText(label); view.setTextSize(11); view.setTextColor(PAPER); view.setAllCaps(false); return view; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private static String frequency(int hertz) { return hertz >= 1000 ? String.format(java.util.Locale.US, "%.1f kHz", hertz / 1000f) : hertz + " Hz"; }
    private static String decibels(int millibels) { return String.format(java.util.Locale.US, "%+.1f dB", millibels / 100f); }
    private static List<PhoneState.Band> previewBands() { int[] centers = {60, 230, 910, 3600, 14000}; List<PhoneState.Band> bands = new ArrayList<>(); for (int i = 0; i < centers.length; i++) bands.add(new PhoneState.Band(i, centers[i], -1500, 1500, 0)); return bands; }
}
