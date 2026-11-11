package app.gboard.colemak;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * Visual-only touch effects for Gboard's actual SoftKeyView objects.
 * It draws into ViewOverlay and deliberately never consumes or edits MotionEvent.
 */
public final class KeyboardEffectsHook {
    private static final String KEY_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final String EFFECT_ENABLED = "key_effects_enabled";
    private static final String EFFECT_TYPE = "key_effect_type";
    private static final int DEFAULT_COLOR = Color.rgb(72, 166, 245);

    private KeyboardEffectsHook() { }

    public static void onDispatchTouchEvent(View keyboardRoot, MotionEvent event) {
        if (keyboardRoot == null || event == null) return;
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN) return;

        try {
            Context context = keyboardRoot.getContext();
            SharedPreferences preferences = context.getSharedPreferences(
                    context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
            if (!preferences.getBoolean(EFFECT_ENABLED, true)) return;

            int pointer = event.getActionIndex();
            int[] rootLocation = new int[2];
            keyboardRoot.getLocationOnScreen(rootLocation);
            int screenX = rootLocation[0] + Math.round(event.getX(pointer));
            int screenY = rootLocation[1] + Math.round(event.getY(pointer));

            Class<?> keyClass = Class.forName(
                    KEY_VIEW_CLASS, false, keyboardRoot.getClass().getClassLoader());
            if (!(keyboardRoot instanceof ViewGroup)) return;
            View key = findKeyAt((ViewGroup) keyboardRoot, screenX, screenY, keyClass);
            if (key == null) return;

            int[] keyLocation = new int[2];
            key.getLocationOnScreen(keyLocation);
            Rect bounds = new Rect(
                    keyLocation[0] - rootLocation[0],
                    keyLocation[1] - rootLocation[1],
                    keyLocation[0] - rootLocation[0] + key.getWidth(),
                    keyLocation[1] - rootLocation[1] + key.getHeight());
            if (bounds.width() <= 0 || bounds.height() <= 0) return;

            String type = preferences.getString(EFFECT_TYPE, "spark");
            KeyEffectDrawable effect = new KeyEffectDrawable(
                    keyboardRoot, bounds, type, DEFAULT_COLOR);
            keyboardRoot.getOverlay().add(effect);
            effect.start();
        } catch (Throwable ignored) {
            // Optional animation failure must never affect Gboard's original input path.
        }
    }

    private static View findKeyAt(
            ViewGroup parent, int screenX, int screenY, Class<?> keyClass) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (!contains(child, screenX, screenY)) continue;
            if (keyClass.isInstance(child)) return child;
            if (child instanceof ViewGroup) {
                View nested = findKeyAt((ViewGroup) child, screenX, screenY, keyClass);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static boolean contains(View view, int x, int y) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return x >= location[0] && x < location[0] + view.getWidth()
                && y >= location[1] && y < location[1] + view.getHeight();
    }

    private static final class KeyEffectDrawable extends Drawable {
        private final View root;
        private final RectF key;
        private final String type;
        private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;

        KeyEffectDrawable(View root, Rect bounds, String type, int color) {
            this.root = root;
            this.key = new RectF(bounds);
            this.type = type == null ? "spark" : type;
            this.color = color;
            setBounds(bounds);
        }

        void start() {
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(620L);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(value -> {
                progress = (Float) value.getAnimatedValue();
                invalidateSelf();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    remove();
                }
                @Override public void onAnimationCancel(android.animation.Animator animation) {
                    remove();
                }
            });
            animator.start();
        }

        private void remove() {
            try { root.getOverlay().remove(this); } catch (Throwable ignored) { }
        }

        @Override public void draw(Canvas canvas) {
            int save = canvas.save();
            canvas.translate(key.left, key.top);
            float width = key.width();
            float height = key.height();
            float cx = width / 2f;
            float cy = height / 2f;
            float max = Math.max(width, height);
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);

            switch (type) {
                case "flash":
                    paint.setStyle(Paint.Style.FILL);
                    paint.setAlpha(alpha(150f * (1f - progress)));
                    canvas.drawRoundRect(new RectF(1, 1, width - 1, height - 1),
                            9f, 9f, paint);
                    break;
                case "outline":
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(2f, max * 0.045f));
                    paint.setAlpha(alpha(235f * (1f - progress)));
                    float inset = Math.max(1f, progress * max * 0.08f);
                    canvas.drawRoundRect(new RectF(inset, inset, width - inset, height - inset),
                            8f + progress * 8f, 8f + progress * 8f, paint);
                    break;
                case "blob":
                    paint.setStyle(Paint.Style.FILL);
                    paint.setAlpha(alpha(110f * (1f - progress)));
                    float blob = max * (0.12f + progress * 0.40f);
                    canvas.drawCircle(cx, cy, blob, paint);
                    canvas.drawCircle(cx - blob * 0.30f, cy - blob * 0.12f, blob * 0.58f, paint);
                    canvas.drawCircle(cx + blob * 0.28f, cy + blob * 0.16f, blob * 0.50f, paint);
                    break;
                case "blob_spread":
                    paint.setStyle(Paint.Style.FILL);
                    paint.setAlpha(alpha(82f * (1f - progress)));
                    float spread = max * (0.10f + progress * 0.48f);
                    canvas.drawCircle(cx, cy, spread, paint);
                    canvas.drawCircle(cx - spread * 0.30f, cy, spread * 0.58f, paint);
                    canvas.drawCircle(cx + spread * 0.32f, cy, spread * 0.55f, paint);
                    break;
                case "footprint":
                    paint.setStyle(Paint.Style.FILL);
                    paint.setAlpha(alpha(220f * (1f - progress)));
                    float dot = Math.max(2f, max * 0.035f);
                    for (int i = 0; i < 5; i++) {
                        float x = cx + (i - 2) * dot * 2.3f;
                        float y = cy + ((i % 2 == 0) ? -dot : dot);
                        canvas.drawCircle(x, y, dot * (1f - progress * 0.45f), paint);
                    }
                    break;
                case "firecracker":
                    drawSparks(canvas, cx, cy, max, 14, 0.18f);
                    drawSparks(canvas, cx, cy, max, 9, 0.48f);
                    break;
                case "bounce":
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(2f, max * 0.06f));
                    paint.setAlpha(alpha(220f * (1f - progress)));
                    float pulse = 0.20f + progress * 0.85f;
                    canvas.drawRoundRect(new RectF(
                                    cx - width * pulse * 0.48f,
                                    cy - height * pulse * 0.48f,
                                    cx + width * pulse * 0.48f,
                                    cy + height * pulse * 0.48f),
                            8f, 8f, paint);
                    break;
                case "ring":
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(2f, max * 0.035f));
                    paint.setAlpha(alpha(220f * (1f - progress)));
                    canvas.drawCircle(cx, cy, max * (0.08f + progress * 0.72f), paint);
                    paint.setAlpha(alpha(120f * (1f - progress)));
                    canvas.drawCircle(cx, cy, max * (0.03f + progress * 0.48f), paint);
                    break;
                case "spark":
                default:
                    drawSparks(canvas, cx, cy, max, 10, 0.30f);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(1.5f, max * 0.025f));
                    paint.setAlpha(alpha(130f * (1f - progress)));
                    canvas.drawCircle(cx, cy, max * (0.10f + progress * 0.34f), paint);
                    break;
            }
            canvas.restoreToCount(save);
        }

        private void drawSparks(Canvas canvas, float cx, float cy, float max,
                int count, float phase) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, max * 0.035f));
            float fade = 1f - progress;
            for (int i = 0; i < count; i++) {
                double angle = (Math.PI * 2d * i / count) + phase;
                float inner = max * (0.08f + progress * 0.20f);
                float outer = max * (0.22f + progress * 0.32f);
                float x1 = cx + (float) Math.cos(angle) * inner;
                float y1 = cy + (float) Math.sin(angle) * inner;
                float x2 = cx + (float) Math.cos(angle) * outer;
                float y2 = cy + (float) Math.sin(angle) * outer;
                paint.setAlpha(alpha(230f * fade));
                canvas.drawLine(x1, y1, x2, y2, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(alpha(200f * fade));
                canvas.drawCircle(x2, y2, Math.max(1.5f, max * 0.025f), paint);
                paint.setStyle(Paint.Style.STROKE);
            }
        }

        private static int alpha(float value) {
            return Math.max(0, Math.min(255, Math.round(value)));
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
