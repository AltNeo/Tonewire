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
    private static final int PAPER = PhoneUi.PAPER;
    private static final int MUTED = PhoneUi.MUTED;
    private static final int PANEL = PhoneUi.PANEL;
    private final Handler handler = new Handler();
    private PhoneStateStore stateStore;
    private TokenStore tokenStore;
    private TvConnectionClient client;
    private TvDiscovery discovery;
    private PhoneState state;
    private LinearLayout content;
    private TextView connection;
    private PhoneUi ui;
    private final java.util.Map<String, Button> tabs = new java.util.LinkedHashMap<>();
    private PhoneUi.Curve curve;
    private TextView headroomWarning;
    private boolean draggingBand;
    private final BandWriteCoalescer bandWrites = new BandWriteCoalescer();
    private boolean pairingDialogVisible;
    private boolean endpointInUse;
    private String currentTab = "TUNE";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        stateStore = new PhoneStateStore(this);
        tokenStore = new TokenStore(this);
        state = stateStore.restore();
        endpointInUse = !stateStore.host().isEmpty();
        client = new TvConnectionClient(this, tokenStore.getOrCreateDeviceId());
        discovery =
                new TvDiscovery(
                        this,
                        new TvDiscovery.Listener() {
                            @Override
                            public void onFound(String name, String host, int port, String tvId) {
                                runOnUiThread(() -> connectDiscovered(name, host, port, tvId));
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(
                                        () ->
                                                showState(
                                                        state.withConnection(
                                                                PhoneState.Connection.ERROR,
                                                                message)));
                            }
                        });
        buildChrome();
        if (!stateStore.host().isEmpty())
            connect(stateStore.name(), stateStore.host(), stateStore.port());
    }

    @Override
    protected void onStart() {
        super.onStart();
        discovery.start();
    }

    @Override
    protected void onStop() {
        discovery.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        client.disconnect();
        super.onDestroy();
    }

    private void buildChrome() {
        ui = new PhoneUi(this);
        getWindow().setStatusBarColor(PhoneUi.INK);
        getWindow().setNavigationBarColor(PhoneUi.INK);
        LinearLayout root = ui.column();
        root.setPadding(dp(14), dp(10), dp(14), dp(12));
        root.setBackgroundColor(PhoneUi.INK);
        LinearLayout top = ui.row();
        top.setPadding(dp(6), 0, dp(6), dp(8));
        TextView mark = text("TONEWIRE", 12, LIME);
        mark.setLetterSpacing(.5f);
        top.addView(mark, new LinearLayout.LayoutParams(0, -2, 1));
        connection = button("FIND TV");
        android.graphics.drawable.GradientDrawable liveDot =
                new android.graphics.drawable.GradientDrawable();
        liveDot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        liveDot.setColor(LIME);
        liveDot.setBounds(0, 0, dp(7), dp(7));
        connection.setCompoundDrawables(liveDot, null, null, null);
        connection.setCompoundDrawablePadding(dp(7));
        connection.setTextColor(LIME);
        connection.setBackground(ui.shape(0xff28301b, 99));
        connection.setContentDescription("Connection status. Tap to find or enter TV address.");
        connection.setOnClickListener(v -> showConnectDialog());
        top.addView(connection);
        root.addView(top);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        content = ui.column();
        content.setPadding(0, 0, 0, dp(14));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = ui.row();
        nav.setPadding(dp(4), dp(4), dp(4), dp(4));
        nav.setBackground(ui.shape(PANEL, 18));
        addNav(nav, "TUNE", this::showTune);
        addNav(nav, "PRESETS", this::showPresets);
        addNav(nav, "OUTPUTS", this::showOutputs);
        addNav(nav, "SYSTEM", this::showSystem);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(54));
        navParams.topMargin = dp(8);
        root.addView(nav, navParams);
        setContentView(root);
        showTune();
    }

    private void addNav(LinearLayout nav, String label, Runnable action) {
        Button item = ui.new TabButton(label);
        item.setContentDescription(label + " tab");
        item.setOnClickListener(v -> action.run());
        nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        tabs.put(label, item);
    }

    private void beginPane(String tab, String title) {
        currentTab = tab;
        content.removeAllViews();
        connection.setText(
                state.connection == PhoneState.Connection.CONNECTED ? "LIVE" : "FIND TV");
        for (java.util.Map.Entry<String, Button> entry : tabs.entrySet()) {
            boolean active = entry.getKey().equals(tab);
            Button item = entry.getValue();
            item.setSelected(active);
            item.setTextColor(active ? PAPER : MUTED);
            android.graphics.drawable.GradientDrawable background =
                    new android.graphics.drawable.GradientDrawable();
            background.setColor(active ? PhoneUi.RAISED : android.graphics.Color.TRANSPARENT);
            background.setCornerRadius(dp(14));
            item.setBackground(background);
        }
        TextView heading = text(title, "TUNE".equals(tab) ? 40 : 32, PAPER);
        heading.setTypeface(
                android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL));
        heading.setPadding(dp(6), dp(8), dp(6), dp(16));
        content.addView(heading);
    }

    private void showTune() {
        beginPane("TUNE", "Good evening.");
        boolean ready = TvConnectionClient.engineReady(state);
        LinearLayout pair = ui.row();
        pair.setGravity(Gravity.TOP);
        LinearLayout output = ui.card(), preset = ui.card();
        pair.addView(output, new LinearLayout.LayoutParams(0, -1, 1));
        View gutter = new View(this);
        pair.addView(gutter, new LinearLayout.LayoutParams(dp(10), 1));
        pair.addView(preset, new LinearLayout.LayoutParams(0, -1, 1));
        output.addView(ui.label("ACTIVE OUTPUT"));
        ui.gap(output, 8);
        String[] route = state.route.split(" · ", 2);
        output.addView(text(route[0], 19, PAPER));
        ui.gap(output, 3);
        output.addView(text(route.length > 1 ? route[1] : state.tvName, 13, MUTED));
        ui.gap(output, 12);
        TextView status = text("● " + state.status, 11, ready ? PhoneUi.CYAN : MUTED);
        output.addView(status);
        preset.addView(ui.label("ROUTE PRESET"));
        ui.gap(preset, 8);
        preset.addView(text(state.preset, 19, PAPER));
        ui.gap(preset, 12);
        LinearLayout actions = ui.row();
        Button reset = button("RESET");
        reset.setTextSize(10);
        reset.setContentDescription("Reset equalizer to flat");
        reset.setEnabled(ready);
        reset.setOnClickListener(v -> reset());
        Button cut = button("MAKE CUT-ONLY");
        cut.setTextSize(10);
        cut.setContentDescription("Make equalizer cut-only");
        cut.setEnabled(ready && !state.bands.isEmpty());
        cut.setOnClickListener(v -> makeCutOnly());
        actions.addView(reset, new LinearLayout.LayoutParams(0, dp(36), .8f));
        View actionGap = new View(this);
        actions.addView(actionGap, new LinearLayout.LayoutParams(dp(6), 1));
        actions.addView(cut, new LinearLayout.LayoutParams(0, dp(36), 1.5f));
        preset.addView(actions);
        content.addView(pair, new LinearLayout.LayoutParams(-1, dp(136)));
        ui.gap(content, 10);
        headroomWarning =
                text("Boost above +6 dB may cause clipping. Try Make cut-only.", 14, 0xffffc46b);
        headroomWarning.setVisibility(
                HeadroomGuard.hasHighBoost(state.bands) ? View.VISIBLE : View.GONE);
        content.addView(headroomWarning);
        LinearLayout eq = ui.card();
        eq.setPadding(dp(14), dp(14), dp(14), dp(12));
        content.addView(eq);
        LinearLayout eqTop = ui.row();
        TextView label =
                ui.label(
                        state.bands.isEmpty()
                                ? "5-BAND PREVIEW"
                                : state.bands.size() + "-BAND HARDWARE EQ");
        label.setLetterSpacing(.12f);
        eqTop.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        PhoneUi.EqToggle enabled = ui.new EqToggle(state.enabled);
        enabled.setEnabled(ready);
        enabled.setOnClickListener(
                v -> {
                    state = state.withEnabled(!state.enabled);
                    showTune();
                    client.setEnabled(state.enabled);
                });
        eqTop.addView(enabled, new LinearLayout.LayoutParams(dp(142), dp(36)));
        eq.addView(eqTop);
        ui.gap(eq, 12);
        List<PhoneState.Band> bands = state.bands.isEmpty() ? previewBands() : state.bands;
        curve = ui.new Curve(bands, false, !state.enabled || !ready);
        curve.status(!ready ? "LAST KNOWN" : state.enabled ? "APPLIED" : "EQ BYPASSED · STORED");
        eq.addView(curve, new LinearLayout.LayoutParams(-1, dp(76)));
        ui.gap(eq, 10);
        LinearLayout scale = ui.row();
        scale.addView(new View(this), new LinearLayout.LayoutParams(dp(60), 1));
        LinearLayout ticks = ui.row();
        int min = bands.stream().mapToInt(b -> b.minMillibels).min().orElse(-1500),
                max = bands.stream().mapToInt(b -> b.maxMillibels).max().orElse(1500);
        for (int i = 0; i < 3; i++) {
            TextView tick =
                    text(
                            i == 0 ? scaleDb(min) : i == 1 ? "0" : scaleDb(max),
                            10,
                            i == 1 ? PAPER : MUTED);
            tick.setGravity(i == 0 ? Gravity.LEFT : i == 1 ? Gravity.CENTER : Gravity.RIGHT);
            ticks.addView(tick, new LinearLayout.LayoutParams(0, -2, 1));
        }
        ticks.setPadding(dp(13), 0, dp(13), 0);
        scale.addView(ticks, new LinearLayout.LayoutParams(0, -2, 1));
        scale.addView(new View(this), new LinearLayout.LayoutParams(dp(74), 1));
        eq.addView(scale);
        for (PhoneState.Band band : bands) addBand(eq, band, !state.bands.isEmpty());
        ui.line(eq);
        ui.gap(eq, 10);
        eq.addView(
                text(
                        state.connection == PhoneState.Connection.CONNECTED
                                ? (ready ? "● Changes apply as you move" : state.status)
                                : "Connect to use the controls",
                        12,
                        MUTED));
    }

    private String scaleDb(int mb) {
        return String.format(
                java.util.Locale.US,
                mb > 0 ? "+%s" : "%s",
                mb % 100 == 0
                        ? Integer.toString(mb / 100)
                        : String.format(java.util.Locale.US, "%.1f", mb / 100f));
    }

    private void addBand(LinearLayout parent, PhoneState.Band band, boolean live) {
        ui.line(parent);
        LinearLayout row = ui.row();
        TextView hz = text(frequency(band.centerHz), 14, PAPER);
        row.addView(hz, new LinearLayout.LayoutParams(dp(60), -2));
        PhoneUi.BandSlider slider = ui.new BandSlider(band, !state.enabled);
        slider.setEnabled(live && TvConnectionClient.engineReady(state) && state.enabled);
        slider.setContentDescription(
                frequency(band.centerHz) + " equalizer level, " + decibels(band.levelMillibels));
        row.addView(slider, new LinearLayout.LayoutParams(0, -1, 1));
        TextView value =
                text(decibels(band.levelMillibels), 13, band.levelMillibels == 0 ? MUTED : PAPER);
        value.setTypeface(android.graphics.Typeface.MONOSPACE);
        value.setGravity(Gravity.CENTER);
        value.setPadding(dp(3), dp(7), dp(3), dp(7));
        value.setBackground(ui.shape(PhoneUi.RAISED, 8));
        row.addView(value, new LinearLayout.LayoutParams(dp(74), -2));
        slider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                        if (fromUser) {
                            int level = progress + band.minMillibels;
                            value.setText(decibels(level));
                            bar.setContentDescription(
                                    frequency(band.centerHz)
                                            + " equalizer level, "
                                            + decibels(level));
                            state = state.withBandLevel(band.index, level);
                            curve.update(state.bands, !state.enabled);
                            headroomWarning.setVisibility(
                                    HeadroomGuard.hasHighBoost(state.bands)
                                            ? View.VISIBLE
                                            : View.GONE);
                            queueBand(band.index, level);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                        draggingBand = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                        draggingBand = false;
                        flushBand();
                        showTune();
                    }
                });
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(54)));
    }

    private void showPresets() {
        beginPane("PRESETS", "Presets");
        LinearLayout library =
                card(
                        "SOUND LIBRARY",
                        "Presets are stored on the TV and follow the active output.",
                        null);
        java.util.List<com.tonewire.protocol.PresetCatalog.Entry> catalog = state.presets;
        if (catalog.isEmpty() && !state.supportsSavedPresets)
            catalog =
                    java.util.Arrays.asList(
                            new com.tonewire.protocol.PresetCatalog.Entry(
                                    "flat_reference", "Flat reference", true),
                            new com.tonewire.protocol.PresetCatalog.Entry(
                                    "clear_dialogue", "Clear dialogue", true),
                            new com.tonewire.protocol.PresetCatalog.Entry(
                                    "night_watch", "Night watch", true));
        for (com.tonewire.protocol.PresetCatalog.Entry entry : catalog) {
            ui.gap(library, 12);
            ui.line(library);
            ui.gap(library, 12);
            LinearLayout row = ui.row();
            PhoneUi.Curve glyph = ui.new Curve(presetPreview(entry.id), true, false);
            if (!entry.builtIn
                    || !(entry.id.equals("flat_reference")
                            || entry.id.equals("clear_dialogue")
                            || entry.id.equals("night_watch"))) glyph.unknown();
            row.addView(glyph, new LinearLayout.LayoutParams(dp(44), dp(34)));
            LinearLayout copy = ui.column();
            TextView name = text(entry.name, 17, PAPER);
            copy.addView(name);
            if (!entry.builtIn) {
                TextView saved = text("SAVED", 10, LIME);
                saved.setLetterSpacing(.16f);
                copy.addView(saved);
            }
            String subtitle =
                    entry.id.equals("flat_reference")
                            ? "Every band at 0.0 dB"
                            : entry.id.equals("clear_dialogue")
                                    ? "Presence up, rumble down"
                                    : entry.id.equals("night_watch")
                                            ? "Low end tamed for late hours"
                                            : "Saved on this TV";
            copy.addView(text(subtitle, 12, MUTED));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
            cp.setMargins(dp(12), 0, dp(8), 0);
            row.addView(copy, cp);
            LinearLayout actions = ui.column();
            Button option = button("APPLY");
            option.setContentDescription("Apply " + entry.name + " preset");
            option.setEnabled(TvConnectionClient.engineReady(state));
            option.setOnClickListener(v -> client.applyPreset(entry.id));
            actions.addView(option);
            if (!entry.builtIn && state.supportsSavedPresets) {
                Button delete = button("DELETE");
                delete.setContentDescription("Delete " + entry.name);
                delete.setEnabled(TvConnectionClient.engineReady(state));
                delete.setOnClickListener(
                        v ->
                                new AlertDialog.Builder(this)
                                        .setTitle("Delete " + entry.name + "?")
                                        .setMessage("The current sound stays unchanged.")
                                        .setNegativeButton("Cancel", null)
                                        .setPositiveButton(
                                                "Delete",
                                                (dialog, which) -> client.deletePreset(entry.id))
                                        .show());
                ui.gap(actions, 6);
                actions.addView(delete);
            }
            row.addView(actions, new LinearLayout.LayoutParams(dp(64), -2));
            library.addView(row);
        }
        if (state.supportsSavedPresets) {
            Button save = button("SAVE CURRENT AS…   →");
            save.setContentDescription("Save current as…");
            save.setEnabled(TvConnectionClient.engineReady(state));
            save.setOnClickListener(
                    v -> {
                        EditText name = new EditText(this);
                        name.setHint("Preset name");
                        name.setSingleLine();
                        name.setFilters(
                                new android.text.InputFilter[] {
                                    new android.text.InputFilter.LengthFilter(48)
                                });
                        new AlertDialog.Builder(this)
                                .setTitle("Save current as…")
                                .setView(name)
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                        "Save",
                                        (dialog, which) ->
                                                client.savePreset(name.getText().toString().trim()))
                                .show();
                    });
            ui.gap(content, 10);
            content.addView(save, new LinearLayout.LayoutParams(-1, dp(50)));
        }
    }

    private List<PhoneState.Band> presetPreview(String id) {
        List<PhoneState.Band> result = new ArrayList<>();
        for (PhoneState.Band b : state.bands.isEmpty() ? previewBands() : state.bands) {
            int hz = b.centerHz, level = 0;
            // Presentation of the existing built-in TonewirePresets curves; saved curves are not
            // advertised.
            if ("clear_dialogue".equals(id))
                level = hz <= 300 ? -150 : hz >= 600 && hz <= 3500 ? 200 : 0;
            if ("night_watch".equals(id))
                level = hz <= 300 ? -500 : hz >= 7000 ? -400 : hz >= 700 && hz <= 3000 ? 100 : -100;
            result.add(new PhoneState.Band(b.index, hz, b.minMillibels, b.maxMillibels, level));
        }
        return result;
    }

    private void showOutputs() {
        beginPane("OUTPUTS", "Outputs");
        card(
                "ROUTING",
                state.route + "\n\nRoute preset · " + state.preset + "\nControl · " + state.status,
                null);
        card("AUTOMATION", "Route-specific preset assignment is managed on the TV service.", null);
    }

    private void showSystem() {
        beginPane("SYSTEM", "System");
        card(
                "TV SERVICE",
                state.tvName
                        + "\n"
                        + state.status
                        + "\n\nAddress · "
                        + (stateStore.host().isEmpty() ? "Not selected" : stateStore.host())
                        + "\nPort · "
                        + stateStore.port()
                        + "\nRevision · "
                        + state.revision,
                null);
        Button diag = button("DIAGNOSTICS   →");
        diag.setContentDescription("Diagnostics");
        diag.setOnClickListener(v -> diagnostics());
        content.addView(diag, new LinearLayout.LayoutParams(-1, dp(50)));
        ui.gap(content, 8);
        Button pair = button("PAIR ANOTHER TV   →");
        pair.setContentDescription("Pair another TV");
        pair.setOnClickListener(
                v -> {
                    tokenStore.clear();
                    showConnectDialog();
                });
        content.addView(pair, new LinearLayout.LayoutParams(-1, dp(50)));
    }

    private void diagnostics() {
        new AlertDialog.Builder(this)
                .setTitle("Diagnostics")
                .setMessage(
                        "Connection: "
                                + state.connection
                                + "\nRevision: "
                                + state.revision
                                + "\nBands advertised by TV: "
                                + state.bands.size()
                                + "\nRoute: "
                                + state.route)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showConnectDialog() {
        EditText address = new EditText(this);
        address.setHint("192.168.x.x:49321");
        address.setInputType(InputType.TYPE_CLASS_TEXT);
        address.setSingleLine();
        new AlertDialog.Builder(this)
                .setTitle("Connect to TV")
                .setMessage("Discovery is running. Or enter the TV's local IP and optional port.")
                .setView(address)
                .setPositiveButton(
                        "Connect",
                        (d, w) -> {
                            String[] parts = address.getText().toString().trim().split(":", 2);
                            if (!parts[0].isEmpty()) {
                                int port = parts.length == 2 ? parsePort(parts[1]) : 49321;
                                connect("Living Room TV", parts[0], port);
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port > 0 && port < 65536 ? port : 49321;
        } catch (Exception ignored) {
            return 49321;
        }
    }

    private void connectDiscovered(String name, String host, int port, String tvId) {
        if (EndpointRediscovery.shouldAdopt(
                stateStore.tvId(), stateStore.host(), stateStore.port(), tvId, host, port)) {
            connect(name, host, port);
            stateStore.saveTvId(tvId);
            return;
        }
        if (endpointInUse
                || state.connection == PhoneState.Connection.CONNECTING
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

    private void queueBand(int index, int level) {
        if (bandWrites.record(index, level)) handler.postDelayed(this::flushBand, 16);
    }

    private void flushBand() {
        bandWrites.flush((band, level) -> client.setBand(band, level));
    }

    private void makeCutOnly() {
        try {
            List<PhoneState.Band> shifted = HeadroomGuard.cutOnly(state.bands);
            for (PhoneState.Band band : shifted) {
                PhoneState.Band previous =
                        state.bands.stream()
                                .filter(value -> value.index == band.index)
                                .findFirst()
                                .get();
                if (previous.levelMillibels != band.levelMillibels) {
                    state = state.withBandLevel(band.index, band.levelMillibels);
                    queueBand(band.index, band.levelMillibels);
                }
            }
            flushBand();
            showTune();
        } catch (IllegalArgumentException failure) {
            new AlertDialog.Builder(this)
                    .setTitle("Cannot preserve this curve")
                    .setMessage(failure.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void reset() {
        for (PhoneState.Band band : state.bands) {
            state = state.withBandLevel(band.index, 0);
            queueBand(band.index, 0);
        }
        showTune();
    }

    private void showState(PhoneState replacement) {
        PhoneState previous = state;
        state = replacement;
        stateStore.saveSnapshot(state);
        if (draggingBand
                && state.enabled
                && TvConnectionClient.engineReady(state)
                && sameBandConfiguration(previous, state)) return;
        draggingBand = false;
        switch (currentTab) {
            case "PRESETS":
                showPresets();
                break;
            case "OUTPUTS":
                showOutputs();
                break;
            case "SYSTEM":
                showSystem();
                break;
            default:
                showTune();
        }
    }

    private boolean sameBandConfiguration(PhoneState first, PhoneState second) {
        if (first.bands.size() != second.bands.size()) return false;
        for (int i = 0; i < first.bands.size(); i++) {
            PhoneState.Band a = first.bands.get(i), b = second.bands.get(i);
            if (a.index != b.index
                    || a.centerHz != b.centerHz
                    || a.minMillibels != b.minMillibels
                    || a.maxMillibels != b.maxMillibels) return false;
        }
        return true;
    }

    @Override
    public void onState(PhoneState replacement) {
        showState(replacement);
    }

    @Override
    public void onPairingRequired() {
        state =
                state.withConnection(
                        PhoneState.Connection.PAIRING, "Enter the code shown on the TV");
        if (pairingDialogVisible) return;
        pairingDialogVisible = true;
        EditText code = new EditText(this);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setHint("Six-digit code");
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Pair with TV")
                        .setMessage("Enter the short-lived code on your TV.")
                        .setView(code)
                        .setPositiveButton("Pair", (d, w) -> client.pair(code.getText().toString()))
                        .setNegativeButton("Cancel", null)
                        .create();
        dialog.setOnDismissListener(value -> pairingDialogVisible = false);
        dialog.show();
    }

    @Override
    public void onTvIdentity(String tvId) {
        stateStore.saveTvId(tvId);
    }

    @Override
    public void onPairingAccepted(String token) {
        try {
            tokenStore.save(token);
        } catch (Exception error) {
            onError("Could not securely save the pairing credential");
        }
    }

    @Override
    public void onCommandRejected(String detail) {
        new AlertDialog.Builder(this)
                .setTitle("Could not apply change")
                .setMessage(detail)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onError(String message) {
        showState(state.withConnection(PhoneState.Connection.ERROR, message));
    }

    private LinearLayout card(String title, String body, View extra) {
        LinearLayout card = ui.card();
        card.addView(ui.label(title));
        ui.gap(card, 8);
        card.addView(text(body, 16, PAPER));
        if (extra != null) card.addView(extra);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(10);
        content.addView(card, cp);
        return card;
    }

    private TextView text(String value, int size, int color) {
        return ui.text(value, size, color);
    }

    private Button button(String label) {
        return ui.button(label);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private static String frequency(int hertz) {
        return hertz >= 1000
                ? String.format(java.util.Locale.US, "%.1f kHz", hertz / 1000f)
                : hertz + " Hz";
    }

    private static String decibels(int millibels) {
        return String.format(java.util.Locale.US, "%+.1f dB", millibels / 100f);
    }

    private static List<PhoneState.Band> previewBands() {
        int[] centers = {60, 230, 910, 3600, 14000};
        List<PhoneState.Band> bands = new ArrayList<>();
        for (int i = 0; i < centers.length; i++)
            bands.add(new PhoneState.Band(i, centers[i], -1500, 1500, 0));
        return bands;
    }
}
