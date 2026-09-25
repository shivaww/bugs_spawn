package com.bugspawn.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * The whole game: a SurfaceView running its own render thread.
 * States: TITLE -> PLAYING -> GAME_OVER, with PAUSED frozen on top of PLAYING.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    static final long GAME_TIME = 60_000L;
    static final long LEVEL_TIME = 10_000L;
    static final int SQUASHES_PER_LEVEL = 15;

    static final int TITLE = 0;
    static final int PLAYING = 1;
    static final int GAME_OVER = 2;
    static final int PAUSED = 3;

    /** Splat and score-text colors per bug type (matches Bug type indexes). */
    static final int[] SPLAT_COLOR = {0xE53935, 0x5C6BC0, 0xFFD54F, 0xFF7043, 0xFDD835};

    final SurfaceHolder holder;
    final Paint paint = new Paint();
    final Random random = new Random();
    final ArrayList<Bug> bugs = new ArrayList<>();
    final ArrayList<Bug> titleBugs = new ArrayList<>();
    final ArrayList<Particle> particles = new ArrayList<>();

    volatile boolean running;
    volatile int state = TITLE;
    volatile int w, h;
    Thread thread;

    float sc = 1.5f;
    float topPad = 100f;
    float bgT;

    int score, squashes, level = 1, best, finalLevel, squashesSinceLevel;
    boolean newBest;
    long timeLeft = GAME_TIME, levelTimer, spawnTimer;
    float levelFlashT;

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        best = prefs().getInt("best", 0);
        sc = getResources().getDisplayMetrics().density;
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("bug_squash", Context.MODE_PRIVATE);
    }

    // ---- lifecycle: the render thread lives while the surface does ----

    @Override
    public void surfaceCreated(SurfaceHolder sh) {
        startThread();
    }

    @Override
    public void surfaceChanged(SurfaceHolder sh, int format, int width, int height) {
        w = width;
        h = height;
        topPad = 70f * sc + 8f;
        if (titleBugs.isEmpty() && w > 0) seedTitleBugs();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder sh) {
        running = false;
        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    private synchronized void startThread() {
        if (thread != null) return;
        running = true;
        thread = new Thread(this, "GameLoop");
        thread.start();
    }

    /** Called from MainActivity.onPause - freezes a running game. */
    public void pauseGame() {
        if (state == PLAYING) state = PAUSED;
    }

    @Override
    public void run() {
        long prev = System.nanoTime();
        while (running) {
            Canvas c = null;
            try {
                c = holder.lockCanvas();
            } catch (Exception ignored) {
            }
            if (c == null) {
                try {
                    Thread.sleep(16L);
                } catch (InterruptedException ignored) {
                }
                continue;
            }
            long now = System.nanoTime();
            float dt = (now - prev) / 1_000_000_000f;
            prev = now;
            if (dt > 0.05f) dt = 0.05f;
            if (dt < 0f) dt = 0f;
            update(dt);
            draw(c);
            try {
                holder.unlockCanvasAndPost(c);
            } catch (Exception ignored) {
            }
        }
    }

    // ---- simulation ----

    void update(float dt) {
        updateParticles(dt);
        if (state == PAUSED) return;
        bgT += dt;
        if (state == TITLE) {
            updateTitleBugs(dt);
            return;
        }
        if (state == GAME_OVER) {
            updateBugList(dt, 40f * sc);
            return;
        }
        timeLeft -= (long) (dt * 1000f);
        if (timeLeft <= 0L) {
            timeLeft = 0L;
            endGame();
            return;
        }
        levelTimer += (long) (dt * 1000f);
        if (levelTimer >= LEVEL_TIME || squashesSinceLevel >= SQUASHES_PER_LEVEL) levelUp();
        spawnTimer -= (long) (dt * 1000f);
        if (spawnTimer <= 0L) {
            spawnTimer = (long) (spawnInterval() * (0.7f + random.nextFloat() * 0.6f));
            trySpawn();
        }
        if (levelFlashT > 0f) levelFlashT -= dt;
        updateBugList(dt, topPad);
    }

    private void updateBugList(float dt, float pad) {
        Iterator<Bug> it = bugs.iterator();
        while (it.hasNext()) {
            Bug b = it.next();
            b.update(dt, w, h, pad);
            if (!b.alive) it.remove();
        }
    }

    private void updateTitleBugs(float dt) {
        Iterator<Bug> it = titleBugs.iterator();
        while (it.hasNext()) {
            Bug b = it.next();
            b.update(dt, w, h, 40f * sc);
            if (!b.alive) it.remove();
        }
        while (titleBugs.size() < 6) {
            titleBugs.add(new Bug(random.nextInt(5), 0.8f, 1.2f, 999f, w, h));
        }
    }

    private void updateParticles(float dt) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            if (!it.next().update(dt)) it.remove();
        }
    }

    // ---- spawning & difficulty ----

    private long spawnInterval() {
        double f = Math.pow(0.9, level - 1);
        long ms = (long) (900 * f);
        return Math.max(240L, ms);
    }

    private void trySpawn() {
        if (bugs.size() >= Math.min(14, 5 + level)) return;
        bugs.add(makeBug(pickType()));
    }

    private int pickType() {
        int waspW = level >= 2 ? Math.min(26, 8 + level * 2) : 0;
        int total = 40 + 22 + 8 + 7 + waspW;
        int roll = random.nextInt(total);
        roll -= 40;
        if (roll < 0) return Bug.LADYBUG;
        roll -= 22;
        if (roll < 0) return Bug.BEETLE;
        roll -= 8;
        if (roll < 0) return Bug.GOLD;
        roll -= 7;
        if (roll < 0) return Bug.BUTTERFLY;
        return Bug.WASP;
    }

    private Bug makeBug(int type) {
        float speedMul = Math.min(1.9f, 1f + 0.09f * (level - 1));
        float lifeFactor = Math.max(0.5f, 1f - 0.05f * (level - 1));
        float sizeMul = Math.max(0.7f, 1f - 0.03f * (level - 1));
        return new Bug(type, speedMul, sizeMul, lifeFactor, w, h);
    }

    // ---- game flow ----

    private void levelUp() {
        level++;
        levelTimer = 0L;
        squashesSinceLevel = 0;
        levelFlashT = 1.6f;
        for (int i = 0; i < 14; i++) {
            particles.add(new Particle(Particle.CONFETTI,
                    w * (0.3f + random.nextFloat() * 0.4f),
                    h * (0.25f + random.nextFloat() * 0.15f), null, 0));
        }
    }

    private void endGame() {
        state = GAME_OVER;
        finalLevel = level;
        newBest = score > best;
        if (newBest) {
            best = score;
            prefs().edit().putInt("best", best).commit();
        }
        for (int i = 0; i < 90; i++) {
            particles.add(new Particle(Particle.CONFETTI,
                    w * (0.08f + random.nextFloat() * 0.84f),
                    h * (0.12f + random.nextFloat() * 0.3f), null, 0));
        }
    }

    private void startGame() {
        score = 0;
        squashes = 0;
        squashesSinceLevel = 0;
        level = 1;
        levelTimer = 0L;
        spawnTimer = 350L;
        timeLeft = GAME_TIME;
        levelFlashT = 1.3f;
        newBest = false;
        bugs.clear();
        particles.clear();
        state = PLAYING;
    }

    private void seedTitleBugs() {
        for (int i = 0; i < 6; i++) {
            titleBugs.add(new Bug(random.nextInt(5), 0.8f, 1.2f, 999f, w, h));
        }
    }

    // ---- input ----

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int idx = (e.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
                    >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            if (idx < 0 || idx >= e.getPointerCount()) idx = 0;
            tap(e.getX(idx), e.getY(idx));
        }
        return true;
    }

    private void tap(float x, float y) {
        if (state == PAUSED) {
            state = PLAYING;
            return;
        }
        if (state == TITLE) {
            if (playButtonRect().contains(x, y)) {
                startGame();
                return;
            }
            for (int i = titleBugs.size() - 1; i >= 0; i--) {
                Bug b = titleBugs.get(i);
                if (b.hitTest(x, y)) {
                    squashBug(b, x, y);
                    return;
                }
            }
            return;
        }
        if (state == GAME_OVER) {
            if (againButtonRect().contains(x, y)) startGame();
            return;
        }
        for (int i = bugs.size() - 1; i >= 0; i--) {
            Bug b = bugs.get(i);
            if (b.hitTest(x, y)) {
                squashBug(b, x, y);
                return;
            }
        }
        particles.add(new Particle(Particle.RING, x, y, null, 0xFF78909C));
    }

    private void squashBug(Bug b, float x, float y) {
        b.squash();
        int pts = b.points();
        score = Math.max(0, score + pts);
        if (pts > 0) {
            squashes++;
            squashesSinceLevel++;
        }
        int col = SPLAT_COLOR[b.type];
        for (int i = 0; i < 10; i++) {
            particles.add(new Particle(Particle.SPLAT, b.x, b.y, null, col));
        }
        particles.add(new Particle(Particle.RING, b.x, b.y, null,
                pts >= 0 ? 0xFFFFFFFF : 0xFFFF5252));
        particles.add(new Particle(Particle.TEXT, b.x, b.y - b.size,
                (pts > 0 ? "+" : "") + pts, pts > 0 ? col : 0xFFFF5252));
    }

    // ---- button geometry (shared by draw and hit-test) ----

    private RectF playButtonRect() {
        float bw = Math.min(w * 0.62f, 340f * sc);
        float bh = 64f * sc;
        float cy = h * 0.52f;
        return new RectF(w / 2f - bw / 2f, cy - bh / 2f, w / 2f + bw / 2f, cy + bh / 2f);
    }

    private RectF againButtonRect() {
        float bw = Math.min(w * 0.62f, 340f * sc);
        float bh = 64f * sc;
        float cy = h * 0.78f - 70f * sc;
        return new RectF(w / 2f - bw / 2f, cy - bh / 2f, w / 2f + bw / 2f, cy + bh / 2f);
    }

    // ---- rendering ----

    void draw(Canvas c) {
        drawBackground(c);
        if (state == TITLE) {
            for (int i = 0; i < titleBugs.size(); i++) titleBugs.get(i).draw(c, paint);
            drawTitle(c);
        } else {
            for (int i = 0; i < bugs.size(); i++) bugs.get(i).draw(c, paint);
            if (state == PLAYING || state == PAUSED) drawHud(c);
            if (state == GAME_OVER) {
                drawDim(c);
                drawGameOver(c);
            } else if (state == PAUSED) {
                drawDim(c);
                drawPaused(c);
            }
            if (state == PLAYING) drawBanner(c);
        }
        for (int i = 0; i < particles.size(); i++) particles.get(i).draw(c, paint);
    }

    private void drawBackground(Canvas c) {
        float horizon = h * 0.66f;
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF64B5F6);
        c.drawRect(0f, 0f, w, horizon, paint);
        float sx = w * 0.82f;
        float sy = h * 0.12f;
        paint.setColor(0x33FFF176);
        c.drawCircle(sx, sy, 58f * sc, paint);
        paint.setColor(0x66FFD54F);
        c.drawCircle(sx, sy, 46f * sc, paint);
        paint.setColor(0xFFFFEB3B);
        c.drawCircle(sx, sy, 34f * sc, paint);
        paint.setColor(0xEBFFFFFF);
        float span = w + 240f * sc;
        for (int i = 0; i < 4; i++) {
            float speed = 14f + i * 6f;
            float cx = (bgT * speed + i * w * 0.31f) % span - 120f * sc;
            if (cx < -140f * sc) cx += span;
            float cy = h * (0.07f + 0.045f * (i % 3));
            c.drawCircle(cx, cy, 26f * sc, paint);
            c.drawCircle(cx + 24f * sc, cy + 6f * sc, 20f * sc, paint);
            c.drawCircle(cx - 24f * sc, cy + 7f * sc, 18f * sc, paint);
        }
        paint.setColor(0xFF558B2F);
        c.drawOval(-w * 0.25f, horizon - 46f * sc, w * 0.62f, horizon + 60f * sc, paint);
        paint.setColor(0xFF4E9C33);
        c.drawOval(w * 0.38f, horizon - 60f * sc, w * 1.25f, horizon + 70f * sc, paint);
        paint.setColor(0xFF7CB342);
        c.drawRect(0f, horizon, w, h, paint);
        int[] petals = {0xFFFFFFFF, 0xFFF8BBD0, 0xFFFFF59D};
        for (int i = 0; i < 12; i++) {
            float fx = (i * w / 12f + (i * 53 % 41)) % w;
            float fy = horizon + 30f * sc + ((i * 97 % 100) / 100f) * (h - horizon - 60f * sc);
            paint.setColor(0xFF2E7D32);
            c.drawLine(fx, fy + 10f * sc, fx, fy - 6f * sc, paint);
            paint.setColor(petals[i % 3]);
            c.drawCircle(fx, fy - 8f * sc, 5.5f * sc, paint);
            paint.setColor(0xFFFFC107);
            c.drawCircle(fx, fy - 8f * sc, 2.5f * sc, paint);
        }
    }

    private void drawHud(Canvas c) {
        textOut(c, "SCORE " + score, 16f * sc, 44f * sc, 24f * sc,
                0xFFFFFFFF, 0xFF33691E, Paint.Align.LEFT);
        boolean rush = timeLeft < 10_000L;
        float ts = rush ? 28f * sc * (1f + 0.06f * (float) Math.sin(bgT * 6f)) : 24f * sc;
        textOut(c, ((timeLeft + 999L) / 1000L) + "s", w - 16f * sc, 44f * sc, ts,
                rush ? 0xFFFF5252 : 0xFFFFFFFF, 0xFF33691E, Paint.Align.RIGHT);
        RectF pill = new RectF(w / 2f - 52f * sc, 12f * sc, w / 2f + 52f * sc, 46f * sc);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCC37474F);
        c.drawRoundRect(pill, 17f * sc, 17f * sc, paint);
        textOut(c, "LV " + level, w / 2f, 35f * sc, 16f * sc,
                0xFFFFFFFF, 0xCC37474F, Paint.Align.CENTER);
        float frac = Math.max(0f, Math.min(1f, timeLeft / (float) GAME_TIME));
        int barColor = frac > 0.5f ? 0xFF66BB6A : (frac > 0.25f ? 0xFFFFCA28 : 0xFFEF5350);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x5537474F);
        c.drawRect(16f * sc, topPad - 12f * sc, w - 16f * sc, topPad - 4f * sc, paint);
        paint.setColor(barColor);
        c.drawRect(16f * sc, topPad - 12f * sc,
                16f * sc + (w - 32f * sc) * frac, topPad - 4f * sc, paint);
    }

    private void drawBanner(Canvas c) {
        if (levelFlashT <= 0f) return;
        float a = Math.min(1f, levelFlashT / 0.4f);
        textOut(c, "LEVEL " + level + "!", w / 2f, h * 0.36f,
                40f * sc * (1.15f - 0.15f * a), 0xFFFF9800, 0xFFFFFFFF, Paint.Align.CENTER);
    }

    private void drawTitle(Canvas c) {
        float bob = 1f + 0.04f * (float) Math.sin(bgT * 2.4f);
        textOut(c, "BUG", w / 2f, h * 0.19f, 52f * sc * bob,
                0xFFFFFFFF, 0xFFD84315, Paint.Align.CENTER);
        textOut(c, "SQUASH!", w / 2f, h * 0.19f + 52f * sc * bob * 0.9f, 52f * sc * bob,
                0xFFFFD54F, 0xFFD84315, Paint.Align.CENTER);
        textOut(c, "Squash the bugs, spare the wasps!", w / 2f, h * 0.30f, 15f * sc,
                0xFFFFFFFF, 0xFF37474F, Paint.Align.CENTER);
        button(c, playButtonRect(), 0xFFFF7043, 0xFFD84315, "PLAY");
        textOut(c, "BEST: " + best, w / 2f, h * 0.66f, 18f * sc,
                0xFFFFFFFF, 0xFF37474F, Paint.Align.CENTER);
        textOut(c, "60 seconds - endless levels - +1 / +3 / +5 / -3",
                w / 2f, h * 0.90f, 12.5f * sc, 0xFFFFFFFF, 0xFF37474F, Paint.Align.CENTER);
    }

    private void drawDim(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x88000000);
        c.drawRect(0f, 0f, w, h, paint);
    }

    private void drawGameOver(Canvas c) {
        RectF panel = new RectF(w * 0.08f, h * 0.14f, w * 0.92f, h * 0.78f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xF2FFFFFF);
        c.drawRoundRect(panel, 24f * sc, 24f * sc, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f * sc);
        paint.setColor(0xFF43A047);
        c.drawRoundRect(panel, 24f * sc, 24f * sc, paint);
        float cx = w / 2f;
        textOut(c, "TIME'S UP!", cx, h * 0.24f, 30f * sc, 0xFFD84315, 0xFFFFFFFF, Paint.Align.CENTER);
        textOut(c, "SCORE", cx, h * 0.315f, 14f * sc, 0xFF78909C, 0xFFFFFFFF, Paint.Align.CENTER);
        textOut(c, String.valueOf(score), cx, h * 0.40f, 56f * sc, 0xFF37474F, 0xFFFFFFFF, Paint.Align.CENTER);
        if (newBest) {
            textOut(c, "NEW BEST!", cx, h * 0.465f, 17f * sc, 0xFFFFB300, 0xFF37474F, Paint.Align.CENTER);
        } else {
            textOut(c, "Best: " + best, cx, h * 0.465f, 16f * sc, 0xFF546E7A, 0xFFFFFFFF, Paint.Align.CENTER);
        }
        textOut(c, "Level reached: " + finalLevel + "   Bugs squashed: " + squashes,
                cx, h * 0.53f, 14.5f * sc, 0xFF546E7A, 0xFFFFFFFF, Paint.Align.CENTER);
        button(c, againButtonRect(), 0xFF43A047, 0xFF2E7D32, "PLAY AGAIN");
    }

    private void drawPaused(Canvas c) {
        textOut(c, "PAUSED", w / 2f, h * 0.45f, 34f * sc, 0xFFFFFFFF, 0xFF37474F, Paint.Align.CENTER);
        textOut(c, "tap to resume", w / 2f, h * 0.51f, 15f * sc, 0xFFFFFFFF, 0xFF37474F, Paint.Align.CENTER);
    }

    // ---- shared draw helpers ----

    void textOut(Canvas c, String s, float x, float y, float size,
                 int fill, int stroke, Paint.Align align) {
        paint.setAntiAlias(true);
        paint.setTextAlign(align);
        paint.setTextSize(size);
        paint.setFakeBoldText(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, size * 0.18f));
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(stroke);
        c.drawText(s, x, y, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        c.drawText(s, x, y, paint);
    }

    void button(Canvas c, RectF r, int fill, int border, String label) {
        paint.setAntiAlias(true);
        float rad = r.height() * 0.42f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        c.drawRoundRect(r, rad, rad, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f * sc);
        paint.setColor(border);
        c.drawRoundRect(r, rad, rad, paint);
        float pulse = 0.5f + 0.5f * (float) Math.sin(bgT * 3.2f);
        paint.setStrokeWidth(3f * sc);
        paint.setColor(((int) (90f * pulse) << 24) | (border & 0x00FFFFFF));
        RectF halo = new RectF(r.left - 5f * sc, r.top - 5f * sc,
                r.right + 5f * sc, r.bottom + 5f * sc);
        c.drawRoundRect(halo, rad + 5f * sc, rad + 5f * sc, paint);
        textOut(c, label, r.centerX(), r.centerY() + 8f * sc, 21f * sc,
                0xFFFFFFFF, border, Paint.Align.CENTER);
    }
}
