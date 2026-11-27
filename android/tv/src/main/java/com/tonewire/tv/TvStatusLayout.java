package com.tonewire.tv;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tonewire.tv.network.NetworkStatus;
import com.tonewire.tv.network.PairingCodeManager;

/** Reference-pixel layout: a 1080p control surface inside the TV's overscan-safe area. */
final class TvStatusLayout {
    static final int INK = 0xff0d100e,
            PANEL = 0xff1a201c,
            RAISED = 0xff222a24,
            PAPER = 0xffedeadf,
            MUTED = 0xff949a90,
            LINE = 0xff383e36,
            ACID = 0xffd9ff6b,
            CYAN = 0xff78dfd3,
            SIGNAL = 0xffff7854;
    private final Activity activity;
    private final float scale;
    final LinearLayout root;
    final ActionButton pair, forget;
    private final TextView connection, headline, preset, output, health, code, ready, paired;
    private final TextView[] cells = new TextView[4];
    private final View lamp, offBar, onBar;
    private final LinearLayout eqPanel, codeBox, pairedBox;
    private final TextView instructions;
    private final Meter meter;
    private String lastEq = "";
    private boolean eqActive;

    TvStatusLayout(Activity activity, String name) {
        this.activity = activity;
        android.graphics.Rect bounds =
                activity.getWindowManager().getCurrentWindowMetrics().getBounds();
        scale = Math.min(bounds.width() / 1920f, bounds.height() / 1080f);
        root = column();
        root.setBackgroundColor(INK);
        root.setPadding(p(112), p(72), p(112), p(66));
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("TONEWIRE", 22, ACID);
        mark.setLetterSpacing(.62f);
        header.addView(mark);
        View divider = new View(activity);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(p(2), p(44));
        dividerParams.setMargins(p(40), 0, p(40), 0);
        header.addView(divider, dividerParams);
        TextView tvName = text(name, 46, PAPER);
        tvName.setSingleLine();
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(tvName, new LinearLayout.LayoutParams(0, -2, 1));
        lamp = new View(activity);
        lamp.setBackground(shape(CYAN, 7, 0, CYAN));
        LinearLayout.LayoutParams lampParams = new LinearLayout.LayoutParams(p(14), p(14));
        lampParams.setMargins(p(20), 0, p(16), 0);
        header.addView(lamp, lampParams);
        connection = text("Starting…", 28, PAPER);
        connection.setContentDescription("Phone connection and server status");
        header.addView(connection);
        root.addView(header, new LinearLayout.LayoutParams(-1, p(72)));
        line(root);
        gap(root, 28);
        LinearLayout body = row();
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout left = column();
        body.addView(left, new LinearLayout.LayoutParams(0, -1, 1));
        View gutter = new View(activity);
        body.addView(gutter, new LinearLayout.LayoutParams(p(28), 1));
        LinearLayout right = column();
        body.addView(right, new LinearLayout.LayoutParams(p(560), -1));
        eqPanel = panel(40, 36);
        left.addView(eqPanel, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout eqTop = row();
        eqTop.setGravity(Gravity.CENTER_VERTICAL);
        eqTop.addView(label("EQUALIZER"), new LinearLayout.LayoutParams(0, -2, 1));
        offBar = new View(activity);
        onBar = new View(activity);
        eqTop.addView(offBar, new LinearLayout.LayoutParams(p(58), p(14)));
        View barGap = new View(activity);
        eqTop.addView(barGap, new LinearLayout.LayoutParams(p(10), 1));
        eqTop.addView(onBar, new LinearLayout.LayoutParams(p(58), p(14)));
        eqPanel.addView(eqTop);
        headline = text("EQ STARTING", 204, PAPER);
        headline.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        headline.setContentDescription("Equalizer state");
        headline.setGravity(Gravity.CENTER_VERTICAL);
        headline.setMaxLines(1);
        headline.setAutoSizeTextTypeUniformWithConfiguration(
                p(64), p(204), Math.max(1, p(2)), TypedValue.COMPLEX_UNIT_PX);
        eqPanel.addView(headline, new LinearLayout.LayoutParams(-1, 0, 1));
        line(eqPanel);
        gap(eqPanel, 26);
        LinearLayout chain = row();
        chain.setGravity(Gravity.CENTER_VERTICAL);
        preset = module(chain, "PRESET");
        chain.addView(new Arrow(activity), new LinearLayout.LayoutParams(p(110), p(30)));
        output = module(chain, "OUTPUT");
        eqPanel.addView(chain);
        gap(left, 28);
        LinearLayout healthPanel = panel(0, 0);
        left.addView(healthPanel, new LinearLayout.LayoutParams(-1, p(204)));
        LinearLayout summary = row();
        summary.setPadding(p(32), p(20), p(32), p(20));
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(label("HEALTH"));
        health = text("Starting…", 34, PAPER);
        health.setSingleLine();
        health.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams healthParams = new LinearLayout.LayoutParams(0, -2, 1);
        healthParams.leftMargin = p(28);
        summary.addView(health, healthParams);
        healthPanel.addView(summary);
        line(healthPanel);
        LinearLayout cellRow = row();
        String[] titles = {"Service", "Equalizer", "Output", "Control"};
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                View rule = new View(activity);
                rule.setBackgroundColor(LINE);
                cellRow.addView(rule, new LinearLayout.LayoutParams(p(2), -1));
            }
            LinearLayout cell = column();
            cell.setPadding(p(30), p(20), p(20), p(16));
            cells[i] = text("● " + titles[i] + "\nStarting", 26, PAPER);
            cells[i].setContentDescription(titles[i] + " health");
            cells[i].setLineSpacing(p(10), 1);
            cell.addView(cells[i]);
            cellRow.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        }
        healthPanel.addView(cellRow, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout pairing = panel(40, 36);
        right.addView(pairing, new LinearLayout.LayoutParams(-1, 0, 1));
        pairing.addView(label("PAIRING"));
        codeBox = column();
        pairedBox = column();
        gap(codeBox, 36);
        code = text("------", 108, PAPER);
        code.setTypeface(Typeface.MONOSPACE);
        code.setLetterSpacing(.06f);
        code.setMaxLines(1);
        code.setAutoSizeTextTypeUniformWithConfiguration(
                p(60), p(108), Math.max(1, p(2)), TypedValue.COMPLEX_UNIT_PX);
        code.setContentDescription("Pairing code");
        codeBox.addView(code, new LinearLayout.LayoutParams(-1, p(128)));
        gap(codeBox, 20);
        ready = text("", 26, CYAN);
        ready.setContentDescription("Pairing countdown and lockout");
        codeBox.addView(ready);
        gap(codeBox, 20);
        meter = new Meter(activity);
        codeBox.addView(meter, new LinearLayout.LayoutParams(-1, p(10)));
        pairing.addView(codeBox);
        gap(pairedBox, 40);
        paired = text("", 62, PAPER);
        paired.setContentDescription("Paired phone count");
        pairedBox.addView(paired);
        gap(pairedBox, 14);
        pairedBox.addView(text("Pair another below", 26, CYAN));
        pairing.addView(pairedBox);
        pairing.addView(new View(activity), new LinearLayout.LayoutParams(1, 0, 1));
        instructions =
                text("Open Tonewire on a phone on the same Wi-Fi and enter the code.", 26, MUTED);
        instructions.setLineSpacing(0, 1.4f);
        pairing.addView(instructions);
        gap(right, 28);
        pair = new ActionButton("PAIR A PHONE", false);
        pair.setContentDescription("Pair a phone");
        right.addView(pair, new LinearLayout.LayoutParams(-1, p(104)));
        gap(right, 16);
        forget = new ActionButton("FORGET ALL PHONES", true);
        forget.setContentDescription("Forget all paired phones");
        right.addView(forget, new LinearLayout.LayoutParams(-1, p(104)));
        gap(root, 24);
        LinearLayout footer = row();
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(
                text("▲ ▼  Move      [ OK ]  Select      [ BACK ]  Close", 24, MUTED),
                new LinearLayout.LayoutParams(0, -2, 1));
        footer.addView(text("Read-only. Sound is changed from your phone.", 24, MUTED));
        root.addView(footer, new LinearLayout.LayoutParams(-1, p(32)));
        pair.requestFocus();
    }

    void render(
            TvSoundStatus sound,
            NetworkStatus network,
            int pairedCount,
            boolean requested,
            long lock) {
        int phones = network.getConnectedClients();
        set(
                connection,
                (phones == 1 ? "1 phone connected" : phones + " phones connected")
                        + (network.isRunning()
                                ? "  ·  port " + network.getPort()
                                : "  ·  server offline"));
        int lampColor = network.isRunning() ? (phones > 0 ? CYAN : MUTED) : SIGNAL;
        if (!Integer.valueOf(lampColor).equals(lamp.getTag())) {
            lamp.setBackground(shape(lampColor, 7, 0, lampColor));
            lamp.setTag(lampColor);
        }
        boolean on = "EQ on".equals(sound.eq);
        if (!sound.eq.equals(lastEq)) {
            lastEq = sound.eq;
            String word = sound.eq.substring(3).toUpperCase(java.util.Locale.US);
            SpannableString value = new SpannableString("EQ " + word);
            value.setSpan(new RelativeSizeSpan(.42f), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            value.setSpan(new ForegroundColorSpan(MUTED), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            headline.setText(value);
            headline.setTextColor(on ? ACID : PAPER);
            offBar.setBackground(shape(on ? RAISED : PAPER, 7, 2, on ? LINE : PAPER));
            onBar.setBackground(shape(on ? ACID : RAISED, 7, 2, on ? ACID : LINE));
        }
        if (eqActive != on) {
            eqActive = on;
            eqPanel.setBackground(shape(PANEL, 22, 2, on ? 0xff667444 : LINE));
        }
        set(preset, sound.preset);
        set(output, primary(sound.output));
        output.setContentDescription("Output · " + sound.output);
        set(health, sound.health);
        boolean healthy = "Equalizer ready".equals(sound.health),
                controlLost = sound.health.contains("Another app"),
                unclear = sound.health.contains("unclear");
        health.setTextColor(healthy ? PAPER : SIGNAL);
        cell(
                0,
                "Service",
                network.isRunning() ? "Running" : "Offline",
                network.isRunning() ? ACID : SIGNAL);
        cell(
                1,
                "Equalizer",
                healthy
                        ? "Attached"
                        : controlLost ? "No control" : unclear ? "Check output" : "Unavailable",
                healthy ? ACID : SIGNAL);
        cell(
                2,
                "Output",
                primary(sound.output),
                unclear || sound.output.contains("unavailable") ? SIGNAL : ACID);
        cell(
                3,
                "Control",
                controlLost ? "Lost" : phones == 1 ? "1 phone" : phones + " phones",
                controlLost ? SIGNAL : phones > 0 ? ACID : MUTED);
        boolean show = TvSoundStatus.showPairingCode(requested, pairedCount);
        visibility(codeBox, show);
        visibility(pairedBox, !show);
        visibility(instructions, show);
        set(pair, requested ? "HIDE PAIRING CODE" : "PAIR A PHONE");
        if (show) {
            set(code, network.getPairingCode());
            long remaining = network.getPairingRemainingMillis();
            set(
                    ready,
                    lock > 0
                            ? "Pairing locked · try again in " + ((lock + 999) / 1000) + "s"
                            : "READY TO PAIR · refreshes in " + remaining / 1000 + "s");
            ready.setTextColor(lock > 0 ? SIGNAL : CYAN);
            meter.update(
                    lock > 0
                            ? lock / (float) PairingCodeManager.LOCK_MILLIS
                            : remaining / (float) PairingCodeManager.DEFAULT_TTL_MILLIS,
                    lock > 0 ? SIGNAL : CYAN);
        } else set(paired, pairedCount + (pairedCount == 1 ? " phone paired" : " phones paired"));
    }

    void error(String message) {
        set(health, message);
    }

    private void cell(int index, String title, String value, int color) {
        String raw = "● " + title + "\n" + value;
        if (raw.contentEquals(cells[index].getText())
                && Integer.valueOf(color).equals(cells[index].getTag())) return;
        cells[index].setTag(color);
        SpannableString s = new SpannableString(raw);
        s.setSpan(new ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(
                new ForegroundColorSpan(MUTED),
                title.length() + 3,
                raw.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(
                new RelativeSizeSpan(.9f),
                title.length() + 3,
                raw.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        cells[index].setText(s);
    }

    private TextView module(LinearLayout parent, String title) {
        LinearLayout box = row();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(p(24), p(14), p(24), p(14));
        box.setBackground(shape(RAISED, 14, 2, LINE));
        TextView caption = label(title);
        box.addView(caption);
        TextView value = text("", 36, PAPER);
        value.setSingleLine();
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, -2, 1);
        vp.leftMargin = p(18);
        box.addView(value, vp);
        parent.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
        return value;
    }

    private String primary(String value) {
        return value.split(" · ", 2)[0];
    }

    private void visibility(View view, boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != v) view.setVisibility(v);
    }

    private static void set(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }

    private int p(float value) {
        return Math.max(1, Math.round(value * scale));
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(activity);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout row() {
        return new LinearLayout(activity);
    }

    private TextView text(String value, int size, int color) {
        TextView v = new TextView(activity);
        v.setText(value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * scale);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        v.setFocusable(false);
        return v;
    }

    private TextView label(String value) {
        TextView v = text(value, 22, MUTED);
        v.setLetterSpacing(.2f);
        return v;
    }

    private LinearLayout panel(int horizontal, int vertical) {
        LinearLayout v = column();
        v.setPadding(p(horizontal), p(vertical), p(horizontal), p(vertical));
        v.setBackground(shape(PANEL, 22, 2, LINE));
        return v;
    }

    private GradientDrawable shape(int color, int radius, int stroke, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius * scale);
        if (stroke > 0) d.setStroke(p(stroke), border);
        return d;
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(activity), new LinearLayout.LayoutParams(1, p(height)));
    }

    private void line(LinearLayout parent) {
        View v = new View(activity);
        v.setBackgroundColor(LINE);
        parent.addView(v, new LinearLayout.LayoutParams(-1, p(2)));
    }

    final class ActionButton extends Button {
        private final boolean danger;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ActionButton(String title, boolean danger) {
            super(activity);
            this.danger = danger;
            setText(title);
            setAllCaps(false);
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 30 * scale);
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            setLetterSpacing(.10f);
            setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            setPadding(p(34), 0, p(92), 0);
            setFocusable(true);
            setBackgroundTintList(null);
            setOnFocusChangeListener((v, focus) -> style());
            style();
        }

        private void style() {
            boolean focus = isFocused();
            setTextColor(focus ? INK : danger ? SIGNAL : PAPER);
            setBackground(
                    shape(
                            focus ? (danger ? SIGNAL : PAPER) : PANEL,
                            18,
                            focus ? 6 : 2,
                            focus ? (danger ? SIGNAL : ACID) : LINE));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!isFocused()) return;
            paint.setColor(INK);
            paint.setTypeface(Typeface.MONOSPACE);
            paint.setTextSize(20 * scale);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * scale);
            float x = getWidth() - p(84), y = getHeight() / 2f;
            canvas.drawRoundRect(x, y - p(20), x + p(52), y + p(20), p(6), p(6), paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawText("OK", x + p(12), y + p(7), paint);
        }
    }

    private final class Arrow extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Arrow(Activity context) {
            super(context);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas c) {
            float y = getHeight() / 2f;
            paint.setColor(MUTED);
            paint.setStrokeWidth(4 * scale);
            paint.setPathEffect(new DashPathEffect(new float[] {p(12), p(8)}, 0));
            c.drawLine(p(10), y, getWidth() - p(20), y, paint);
            paint.setPathEffect(null);
            Path path = new Path();
            path.moveTo(getWidth() - p(6), y);
            path.lineTo(getWidth() - p(21), y - p(11));
            path.lineTo(getWidth() - p(21), y + p(11));
            path.close();
            c.drawPath(path, paint);
        }
    }

    private final class Meter extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float fraction;
        private int color = CYAN;

        Meter(Activity context) {
            super(context);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void update(float value, int tint) {
            float f = Math.max(0, Math.min(1, value));
            if (fraction != f || color != tint) {
                fraction = f;
                color = tint;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            paint.setColor(RAISED);
            c.drawRoundRect(0, 0, getWidth(), getHeight(), p(5), p(5), paint);
            paint.setColor(color);
            c.drawRoundRect(0, 0, getWidth() * fraction, getHeight(), p(5), p(5), paint);
        }
    }
}
