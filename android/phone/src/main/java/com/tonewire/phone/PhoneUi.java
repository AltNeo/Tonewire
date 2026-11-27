package com.tonewire.phone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

/** Small platform-View primitives shared by the four phone panels. */
final class PhoneUi {
    static final int INK = 0xff0d100e,
            PANEL = 0xff1a201c,
            RAISED = 0xff222a24,
            PAPER = 0xffedeadf,
            MUTED = 0xff949a90,
            LINE = 0xff383e36,
            ACID = 0xffd9ff6b,
            CYAN = 0xff78dfd3;
    final Context context;

    PhoneUi(Context context) {
        this.context = context;
    }

    int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), LINE);
        return d;
    }

    LinearLayout column() {
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    LinearLayout row() {
        LinearLayout v = new LinearLayout(context);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    TextView text(String value, float size, int color) {
        TextView v = new TextView(context);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        v.setLineSpacing(0, 1.12f);
        return v;
    }

    TextView label(String value) {
        TextView v = text(value, 10.5f, MUTED);
        v.setLetterSpacing(.18f);
        return v;
    }

    Button button(String label) {
        Button v = new Button(context);
        v.setText(label);
        v.setAllCaps(false);
        v.setTextSize(11);
        v.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        v.setTextColor(PAPER);
        v.setLetterSpacing(.06f);
        v.setMinHeight(0);
        v.setMinimumHeight(0);
        v.setMinWidth(0);
        v.setMinimumWidth(0);
        v.setPadding(dp(8), dp(10), dp(8), dp(10));
        v.setBackgroundTintList(null);
        v.setBackground(shape(RAISED, 10));
        v.setStateListAnimator(null);
        v.setContentDescription(label);
        return v;
    }

    LinearLayout card() {
        LinearLayout v = column();
        v.setPadding(dp(15), dp(14), dp(15), dp(14));
        v.setBackground(shape(PANEL, 20));
        return v;
    }

    void gap(LinearLayout v, int height) {
        v.addView(new View(context), new LinearLayout.LayoutParams(1, dp(height)));
    }

    void line(LinearLayout parent) {
        View v = new View(context);
        v.setBackgroundColor(LINE);
        parent.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    final class TabButton extends Button {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TabButton(String title) {
            super(context);
            setText(title);
            setAllCaps(false);
            setTextSize(11);
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            setLetterSpacing(.1f);
            setPadding(0, 0, 0, 0);
            setMinWidth(0);
            setMinimumWidth(0);
            setStateListAnimator(null);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (isSelected()) {
                paint.setColor(ACID);
                c.drawRoundRect(
                        dp(10),
                        getHeight() - dp(2),
                        getWidth() - dp(10),
                        getHeight(),
                        dp(1),
                        dp(1),
                        paint);
            }
        }
    }

    /** One accessible toggle, with both visual segments and the selected state as its real text. */
    final class EqToggle extends Button {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean on;

        EqToggle(boolean on) {
            super(context);
            this.on = on;
            setText(on ? "EQ ON" : "EQ BYPASSED");
            setContentDescription("Equalizer " + (on ? "enabled" : "bypassed"));
            setBackground(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            setMinWidth(0);
            setMinimumWidth(0);
            setMinHeight(0);
            setMinimumHeight(0);
            setStateListAnimator(null);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    && (event.getX() < getWidth() * .43f) == on) {
                setPressed(false);
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), mid = w * .43f;
            paint.setColor(INK);
            c.drawRoundRect(0, 0, w, h, dp(11), dp(11), paint);
            paint.setColor(on ? ACID : PAPER);
            c.drawRoundRect(
                    on ? dp(3) : mid, dp(3), on ? mid : w - dp(3), h - dp(3), dp(8), dp(8), paint);
            paint.setTextSize(dp(11));
            paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            float baseline = h / 2 - (paint.ascent() + paint.descent()) / 2;
            paint.setColor(on ? INK : MUTED);
            c.drawText("EQ ON", mid / 2, baseline, paint);
            paint.setColor(on ? MUTED : INK);
            c.drawText("BYPASSED", (mid + w) / 2, baseline, paint);
            if (!isEnabled()) {
                paint.setColor(0x660d100e);
                c.drawRoundRect(0, 0, w, h, dp(11), dp(11), paint);
            }
        }
    }

    final class Curve extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<PhoneState.Band> bands;
        private final boolean miniature;
        private boolean dimmed, unknown;
        private String status;

        Curve(List<PhoneState.Band> bands, boolean miniature, boolean dimmed) {
            super(context);
            this.bands = bands;
            this.miniature = miniature;
            this.dimmed = dimmed;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void update(List<PhoneState.Band> value, boolean dim) {
            bands = value;
            dimmed = dim;
            invalidate();
        }

        void status(String value) {
            status = value;
            invalidate();
        }

        void unknown() {
            unknown = true;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), zero = h / 2;
            paint.setColor(0xff151a16);
            c.drawRoundRect(0, 0, w, h, dp(10), dp(10), paint);
            paint.setColor(LINE);
            c.drawRect(0, zero, w, zero + dp(1), paint);
            if (unknown) {
                paint.setColor(MUTED);
                c.drawRect(w * .35f, zero, w * .65f, zero + dp(2), paint);
                return;
            }
            int count = bands.size();
            if (count == 0) return;
            float slot = w / count;
            for (int i = 0; i < count; i++) {
                PhoneState.Band b = bands.get(i);
                float extent =
                        Math.min(
                                h / 2 - dp(miniature ? 3 : 5),
                                Math.abs(b.levelMillibels) / 100f * dp(miniature ? 2.5f : 5.5f));
                float y = b.levelMillibels > 0 ? zero - extent : zero;
                paint.setColor(dimmed ? MUTED : miniature ? CYAN : ACID);
                paint.setAlpha(dimmed ? 140 : 255);
                c.drawRoundRect(
                        slot * (i + .30f),
                        y,
                        slot * (i + .70f),
                        y + Math.max(dp(2), extent),
                        dp(2),
                        dp(2),
                        paint);
                paint.setAlpha(255);
            }
            if (!miniature) {
                paint.setTextAlign(Paint.Align.RIGHT);
                paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                paint.setTextSize(dp(9));
                paint.setColor(MUTED);
                c.drawText(
                        status != null ? status : dimmed ? "EQ BYPASSED · STORED" : "APPLIED",
                        w - dp(10),
                        dp(13),
                        paint);
            }
        }
    }

    /** Retains platform touch, keyboard, range, and accessibility behavior. */
    final class BandSlider extends SeekBar {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int minimum, maximum;
        private final boolean bypassed;

        BandSlider(PhoneState.Band band, boolean bypassed) {
            super(context);
            minimum = band.minMillibels;
            maximum = band.maxMillibels;
            this.bypassed = bypassed;
            setPadding(dp(13), 0, dp(13), 0);
            setSplitTrack(false);
            setProgressDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            GradientDrawable thumb = new GradientDrawable();
            thumb.setShape(GradientDrawable.OVAL);
            thumb.setColor(bypassed ? MUTED : PAPER);
            thumb.setSize(dp(26), dp(26));
            thumb.setStroke(dp(6), PANEL);
            setThumb(thumb);
            setThumbOffset(dp(13));
            setMax(Math.max(1, maximum - minimum));
            setProgress(band.levelMillibels - minimum);
        }

        @Override
        protected synchronized void onDraw(Canvas c) {
            float left = getPaddingLeft(),
                    right = getWidth() - getPaddingRight(),
                    y = getHeight() / 2f;
            float range = Math.max(1, maximum - minimum);
            float zero = left + Math.max(0, Math.min(1, -minimum / range)) * (right - left);
            float x = left + getProgress() / (float) getMax() * (right - left);
            paint.setColor(INK);
            c.drawRoundRect(left, y - dp(3), right, y + dp(3), dp(3), dp(3), paint);
            paint.setColor(bypassed || !isEnabled() ? MUTED : ACID);
            paint.setAlpha(bypassed || !isEnabled() ? 140 : 255);
            c.drawRoundRect(
                    Math.min(zero, x),
                    y - dp(3),
                    Math.max(zero, x),
                    y + dp(3),
                    dp(3),
                    dp(3),
                    paint);
            paint.setAlpha(255);
            paint.setColor(MUTED);
            c.drawRect(zero - dp(.5f), y - dp(14), zero + dp(.5f), y + dp(14), paint);
            super.onDraw(c);
        }
    }
}
