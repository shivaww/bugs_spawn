package com.bugspawn.game;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Random;

/**
 * Tiny ephemeral effects: splat droplets, floating score text, confetti
 * and tap rings. Each one decays over its own lifetime and reports when
 * it is spent.
 */
public class Particle {

    public static final int SPLAT = 0;
    public static final int TEXT = 1;
    public static final int CONFETTI = 2;
    public static final int RING = 3;

    static final int[] CONFETTI_COLORS = {
            0xFFF44336, 0xFF2196F3, 0xFFFFEB3B, 0xFF4CAF50,
            0xFFE91E63, 0xFFFF9800, 0xFF9C27B0
    };

    static final Random RANDOM = new Random();

    public final int kind;
    public final String text;
    public float x, y, vx, vy;
    public float life = 1f;
    public float decay, size, rot, vr;
    public int color;

    public Particle(int kind, float x, float y, String text, int color) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
        switch (kind) {
            case SPLAT:
                size = 5f + RANDOM.nextInt(6);
                decay = 1.5f;
                vx = (RANDOM.nextFloat() - 0.5f) * 280f;
                vy = (RANDOM.nextFloat() - 0.5f) * 280f - 80f;
                break;
            case TEXT:
                decay = 0.85f;
                vy = -120f;
                vx = 0f;
                break;
            case CONFETTI:
                size = 5f + RANDOM.nextInt(7);
                decay = 0.55f;
                vx = (RANDOM.nextFloat() - 0.5f) * 340f;
                vy = -190f - RANDOM.nextInt(150);
                rot = RANDOM.nextFloat() * 6.28f;
                vr = (RANDOM.nextFloat() - 0.5f) * 9f;
                color = CONFETTI_COLORS[RANDOM.nextInt(CONFETTI_COLORS.length)];
                break;
            default: // RING
                size = 16f;
                decay = 2.6f;
                vx = 0f;
                vy = 0f;
                break;
        }
    }

    /** @return false when the particle is spent and should be removed. */
    public boolean update(float dt) {
        life -= decay * dt;
        if (life <= 0f) return false;
        x += vx * dt;
        y += vy * dt;
        if (kind == SPLAT) vy += 900f * dt;
        if (kind == CONFETTI) {
            vy += 750f * dt;
            rot += vr * dt;
        }
        return true;
    }

    public void draw(Canvas canvas, Paint p) {
        int a = (int) (255f * Math.max(0f, Math.min(1f, life)));
        p.setAntiAlias(true);
        if (kind == SPLAT) {
            p.setStyle(Paint.Style.FILL);
            p.setColor((a << 24) | (color & 0x00FFFFFF));
            canvas.drawCircle(x, y, size * (0.4f + 0.6f * life), p);
        } else if (kind == TEXT) {
            p.setTextSize(46f);
            p.setFakeBoldText(true);
            p.setTextAlign(Paint.Align.LEFT);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7f);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor((a << 24) | 0x00FFFFFF);
            canvas.drawText(text, x, y, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor((a << 24) | (color & 0x00FFFFFF));
            canvas.drawText(text, x, y, p);
        } else if (kind == CONFETTI) {
            p.setStyle(Paint.Style.FILL);
            p.setColor((a << 24) | (color & 0x00FFFFFF));
            canvas.save();
            canvas.rotate((float) Math.toDegrees(rot), x, y);
            canvas.drawRect(x - size, y - size * 0.45f, x + size, y + size * 0.45f, p);
            canvas.restore();
        } else { // RING
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f + 5f * life);
            p.setColor((a << 24) | (color & 0x00FFFFFF));
            canvas.drawCircle(x, y, size + (1f - life) * 75f, p);
        }
    }
}
