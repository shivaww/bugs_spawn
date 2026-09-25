package com.bugspawn.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.Random;

/**
 * One bug on screen. Everything about its look — legs, shell, eyes, spots,
 * stripes, wings — is drawn procedurally on the Canvas, so the game ships
 * with zero image assets. Local frame: +x is forward; the bug rotates to
 * face its direction of travel.
 */
public class Bug {

    // ---- types (index into the tuning arrays) ----
    public static final int LADYBUG = 0;
    public static final int BEETLE = 1;
    public static final int GOLD = 2;
    public static final int BUTTERFLY = 3;
    public static final int WASP = 4;

    public static final int[] POINTS = {1, 1, 3, 5, -3};
    static final float[] SIZE = {34, 38, 32, 30, 36};
    static final float[] SPEED = {140, 110, 210, 260, 190};
    static final long[] LIFE = {3400, 3800, 2200, 2600, 3000};

    public final int type;
    public final float size;
    public float x, y;
    public float vx, vy;
    public long lifeLeft;
    public boolean alive = true;
    public boolean dying = false;
    public float deathT = 0f;
    float wiggle, wingPhase, steerTimer, heading;

    private int alphaBits = 0xFF000000;
    private static final Random RANDOM = new Random();

    /**
     * @param speedMul   difficulty scale on base speed (1f = normal)
     * @param sizeMul    body size scale
     * @param lifeFactor difficulty scale on lifetime (1f = normal)
     */
    public Bug(int type, float speedMul, float sizeMul, float lifeFactor, int w, int h) {
        this.type = type;
        this.size = SIZE[type] * sizeMul;
        this.lifeLeft = (long) (LIFE[type] * lifeFactor);
        float speed = SPEED[type] * speedMul;
        float m = 0.12f + RANDOM.nextFloat() * 0.7f;
        this.x = this.size * 2f + m * Math.max(1f, w - this.size * 4f);
        this.y = this.size * 2f + 90f + m * Math.max(1f, h - 180f - this.size * 4f);
        double ang = RANDOM.nextDouble() * Math.PI * 2.0;
        this.vx = (float) Math.cos(ang) * speed;
        this.vy = (float) Math.sin(ang) * speed;
        this.wiggle = RANDOM.nextFloat() * 6f;
        this.wingPhase = RANDOM.nextFloat() * 6f;
    }

    /** Moves, wanders, bounces off walls, animates. topPad keeps bugs below the HUD. */
    public void update(float dt, int w, int h, float topPad) {
        if (dying) {
            deathT += dt * 4f;
            if (deathT >= 1f) alive = false;
            return;
        }
        lifeLeft -= (long) (dt * 1000f);
        if (lifeLeft <= 0L) {
            alive = false;
            return;
        }
        steerTimer -= dt;
        if (steerTimer <= 0f) {
            steerTimer = 0.5f + RANDOM.nextFloat() * 0.9f;
            float rot = (RANDOM.nextFloat() - 0.5f) * 1.8f;
            float cos = (float) Math.cos(rot);
            float sin = (float) Math.sin(rot);
            float nvx = vx * cos - vy * sin;
            float nvy = vx * sin + vy * cos;
            vx = nvx;
            vy = nvy;
        }
        x += vx * dt;
        y += vy * dt;
        float m = size * 1.7f;
        if (x < m && vx < 0f) vx = -vx;
        if (x > w - m && vx > 0f) vx = -vx;
        float top = Math.max(topPad, m);
        if (y < top && vy < 0f) vy = -vy;
        if (y > h - m && vy > 0f) vy = -vy;
        wiggle += dt * 16f;
        wingPhase += dt * 34f;
        heading = (float) Math.atan2(vy, vx);
    }

    /** Generous circular hit test — fingertips are imprecise. */
    public boolean hitTest(float tx, float ty) {
        if (dying || !alive) return false;
        float r = size * 1.6f;
        float dx = tx - x;
        float dy = ty - y;
        return dx * dx + dy * dy <= r * r;
    }

    /** Starts the squash animation; the caller scores it only once. */
    public void squash() {
        if (!dying) {
            dying = true;
            deathT = 0f;
        }
    }

    public int points() {
        return POINTS[type];
    }

    public void draw(Canvas canvas, Paint p) {
        int a = 255;
        if (dying) {
            a = (int) (255f * (1f - deathT));
        } else if (lifeLeft < 800L) {
            a = (Math.abs(lifeLeft % 300L) < 200L) ? 255 : 70;
        }
        if (a <= 0) return;
        alphaBits = a << 24;

        p.setAntiAlias(true);
        if (!dying) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x402E7D32);
            canvas.drawOval(x - size * 0.95f, y + size * 0.45f,
                    x + size * 0.95f, y + size * 0.85f, p);
        }

        canvas.save();
        canvas.rotate((float) Math.toDegrees(heading), x, y);
        canvas.translate(x, y);
        canvas.scale(1f, dying ? 1f - 0.55f * deathT : 1f);
        drawBug(canvas, p);
        canvas.restore();
    }

    /** Applies the current fade alpha to a solid color. */
    private int c(int color) {
        return (color & 0x00FFFFFF) | alphaBits;
    }

    /** Draws the whole bug in its local frame (origin = center, +x = forward). */
    private void drawBug(Canvas canvas, Paint p) {
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, size * 0.09f));
        p.setColor(c(0x37474F));
        drawLegs(canvas, p);
        drawAntennae(canvas, p);
        switch (type) {
            case LADYBUG:
                drawLadybug(canvas, p);
                break;
            case BEETLE:
                drawBeetle(canvas, p);
                break;
            case GOLD:
                drawGold(canvas, p);
                break;
            case BUTTERFLY:
                drawButterfly(canvas, p);
                break;
            default:
                drawWasp(canvas, p);
                break;
        }
    }

    private void drawLegs(Canvas canvas, Paint p) {
        float legLen = size * 1.05f;
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 3; k++) {
                float bx = size * (k - 1) * 0.45f;
                double ph = wiggle + k * 2.1f + (side > 0 ? Math.PI : 0.0);
                float swing = (float) Math.sin(ph);
                float fx = bx + swing * size * 0.28f;
                float fy = side * legLen;
                canvas.drawLine(bx, side * size * 0.4f, fx, fy, p);
                canvas.drawPoint(fx, fy, p);
            }
        }
    }

    private void drawAntennae(Canvas canvas, Paint p) {
        float hx = size * 0.9f;
        p.setColor(c(0x37474F));
        for (int side = -1; side <= 1; side += 2) {
            float ex = hx + size * 0.5f;
            float ey = side * size * 0.42f;
            canvas.drawLine(hx, side * size * 0.14f, ex, ey, p);
            p.setStyle(Paint.Style.FILL);
            canvas.drawCircle(ex, ey, size * 0.09f, p);
            p.setStyle(Paint.Style.STROKE);
        }
    }

    private void drawLadybug(Canvas canvas, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(c(0xE53935));
        canvas.drawOval(-size, -size * 0.8f, size, size * 0.8f, p);
        p.setColor(c(0x37474F));
        canvas.drawCircle(size * 0.75f, 0f, size * 0.42f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, size * 0.06f));
        p.setColor(c(0xB71C1C));
        canvas.drawLine(-size * 0.95f, 0f, size * 0.5f, 0f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(c(0x263238));
        canvas.drawCircle(-size * 0.45f, -size * 0.35f, size * 0.16f, p);
        canvas.drawCircle(-size * 0.05f, size * 0.42f, size * 0.13f, p);
        canvas.drawCircle(size * 0.25f, -size * 0.4f, size * 0.12f, p);
        drawFace(canvas, p, size * 0.75f, 0f, size * 0.42f, true);
    }

    private void drawBeetle(Canvas canvas, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(c(0x5C6BC0));
        canvas.drawOval(-size, -size * 0.75f, size, size * 0.75f, p);
        p.setColor(c(0x37474F));
        canvas.drawCircle(size * 0.8f, 0f, size * 0.38f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, size * 0.05f));
        p.setColor(c(0x303F9F));
        canvas.drawLine(-size * 0.95f, 0f, size * 0.55f, 0f, p);
        p.setStrokeWidth(Math.max(1.5f, size * 0.08f));
        p.setColor(c(0x37474F));
        canvas.drawLine(size * 1.1f, 0f, size * 1.4f, -size * 0.12f, p);
        p.setStrokeWidth(Math.max(2f, size * 0.1f));
        p.setColor(c(0xC5CAE9));
        canvas.drawArc(-size * 0.7f, -size * 0.6f, -size * 0.1f, -size * 0.1f, 200f, 80f, false, p);
        drawFace(canvas, p, size * 0.8f, 0f, size * 0.38f, true);
    }

    private void drawGold(Canvas canvas, Paint p) {
        p.setStyle(Paint.Style.FILL);
        if (!dying) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(wingPhase * 0.4f);
            int ga = (int) (60 + 70 * pulse);
            p.setColor((ga << 24) | 0x00FFF176);
            canvas.drawCircle(0f, 0f, size * 1.55f, p);
        }
        p.setColor(c(0xFFD54F));
        canvas.drawOval(-size, -size * 0.75f, size, size * 0.75f, p);
        p.setColor(c(0x37474F));
        canvas.drawCircle(size * 0.78f, 0f, size * 0.4f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, size * 0.07f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(c(0xFFFFFF));
        float tw = size * 0.32f * (0.6f + 0.4f * Math.abs((float) Math.sin(wingPhase * 0.7f)));
        canvas.drawLine(-size * 0.5f - tw, 0f, -size * 0.5f + tw, 0f, p);
        canvas.drawLine(-size * 0.5f, -tw, -size * 0.5f, tw, p);
        drawFace(canvas, p, size * 0.78f, 0f, size * 0.4f, true);
    }

    private void drawButterfly(Canvas canvas, Paint p) {
        float flap = Math.abs((float) Math.sin(wingPhase));
        float spread = 0.35f + 0.65f * flap;
        p.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.scale(1f, spread);
        p.setColor(c(0xFF7043));
        canvas.drawOval(-size * 0.95f, -size * 1.9f, size * 0.7f, -size * 0.3f, p);
        canvas.drawOval(-size * 0.95f, size * 0.3f, size * 0.7f, size * 1.9f, p);
        p.setColor(c(0x29B6F6));
        canvas.drawOval(-size * 0.85f, -size * 2.7f, size * 0.1f, -size * 1.8f, p);
        canvas.drawOval(-size * 0.85f, size * 1.8f, size * 0.1f, size * 2.7f, p);
        p.setColor(c(0xFFFFFF));
        canvas.drawCircle(-size * 0.15f, -size * 1.1f, size * 0.15f, p);
        canvas.drawCircle(-size * 0.15f, size * 1.1f, size * 0.15f, p);
        canvas.restore();
        p.setColor(c(0x455A64));
        canvas.drawOval(-size * 0.85f, -size * 0.2f, size * 0.6f, size * 0.2f, p);
        canvas.drawCircle(size * 0.7f, 0f, size * 0.3f, p);
        drawFace(canvas, p, size * 0.7f, 0f, size * 0.3f, true);
    }

    private void drawWasp(Canvas canvas, Paint p) {
        float flap = Math.abs((float) Math.sin(wingPhase * 2f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(c(0xB3E1F5FE));
        canvas.save();
        canvas.scale(1f, 0.55f + 0.45f * flap);
        canvas.drawOval(-size * 0.25f, -size * 1.25f, size * 0.45f, -size * 0.3f, p);
        canvas.drawOval(-size * 0.25f, size * 0.3f, size * 0.45f, size * 1.25f, p);
        canvas.restore();
        p.setColor(c(0xFDD835));
        canvas.drawOval(-size, -size * 0.6f, size * 0.55f, size * 0.6f, p);
        p.setColor(c(0x263238));
        for (int i = 0; i < 3; i++) {
            float sx = -size * 0.8f + i * size * 0.42f;
            canvas.drawOval(sx, -size * 0.58f, sx + size * 0.17f, size * 0.58f, p);
        }
        Path stinger = new Path();
        stinger.moveTo(-size * 0.95f, 0f);
        stinger.lineTo(-size * 1.4f, -size * 0.13f);
        stinger.lineTo(-size * 1.4f, size * 0.13f);
        stinger.close();
        canvas.drawPath(stinger, p);
        canvas.drawCircle(size * 0.72f, 0f, size * 0.42f, p);
        drawFace(canvas, p, size * 0.72f, 0f, size * 0.42f, false);
    }

    /**
     * Googly eyes on the head plus a happy mouth, X-eyes when squashed,
     * angry eyebrows when happy == false (wasp).
     */
    private void drawFace(Canvas canvas, Paint p, float cx, float cy, float r, boolean happy) {
        float er = r * 0.4f;
        float ex = cx + r * 0.32f;
        p.setStyle(Paint.Style.FILL);
        for (int side = -1; side <= 1; side += 2) {
            float ey = cy + side * r * 0.38f;
            if (dying) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1.5f, r * 0.16f));
                p.setColor(c(0xFFFFFF));
                canvas.drawLine(ex - er, ey - er, ex + er, ey + er, p);
                canvas.drawLine(ex - er, ey + er, ex + er, ey - er, p);
                p.setStyle(Paint.Style.FILL);
            } else {
                p.setColor(c(0xFFFFFF));
                canvas.drawCircle(ex, ey, er, p);
                p.setColor(c(0x263238));
                canvas.drawCircle(ex + er * 0.35f, ey, er * 0.5f, p);
            }
        }
        if (!dying && happy) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, r * 0.12f));
            p.setColor(c(0xFFFFFF));
            canvas.drawArc(ex - r * 0.05f, cy + r * 0.05f, ex + r * 0.5f, cy + r * 0.55f, 20f, 140f, false, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (!dying && !happy) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, r * 0.14f));
            p.setColor(c(0xFFFFFF));
            for (int side = -1; side <= 1; side += 2) {
                float ey = cy + side * r * 0.38f;
                canvas.drawLine(ex - er * 0.8f, ey - side * er * 1.1f, ex + er * 0.8f, ey - side * er * 1.7f, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
    }
}
