import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class VortexDrift extends JFrame {

    static final int W = 900, H = 660;
    static final Color C_CYAN    = new Color(0, 245, 255);
    static final Color C_MAGENTA = new Color(255, 0, 168);
    static final Color C_GOLD    = new Color(255, 215, 0);
    static final Color C_ORANGE  = new Color(255, 120, 20);
    static final Color C_WHITE   = new Color(255, 255, 255);
    static final Color C_BG      = new Color(2, 0, 12);
    static final int STAR_COUNT  = 130;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VortexDrift().setVisible(true));
    }

    enum Screen { MENU, PLAYING, PAUSED, DEAD }
    Screen screen = Screen.MENU;

    int score, lives, combo, level, highScore;
    int tick, levelTick, comboTimer, deathPause, levelUpTimer;
    int ringTimer, orbTimer, shardTimer;

    double px, py, pvx, pvy, playerPulse;
    int invTicks;
    List<double[]> trail     = new ArrayList<>();
    List<double[]> rings     = new ArrayList<>();
    List<double[]> orbs      = new ArrayList<>();
    List<double[]> shards    = new ArrayList<>();
    List<double[]> particles = new ArrayList<>();
    List<double[]> menuRings = new ArrayList<>();

    double[] stx, sty, str, stb, stp;
    double starScroll, menuTick;

    String flashText  = "";
    Color  flashColor = C_CYAN;
    double flashAlpha = 0, flashScale = 1;

    boolean kUp, kDown, kLeft, kRight;

    Random rng = new Random();
    BufferedImage buffer;
    Graphics2D bg;

    public VortexDrift() {
        super("VØRTEX DRIFT");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        buffer = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        bg = buffer.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        bg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        bg.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        initStars();
        initMenuRings();

        JPanel canvas = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(buffer, 0, 0, null);
            }
        };
        canvas.setPreferredSize(new Dimension(W, H));
        canvas.setBackground(Color.BLACK);
        add(canvas);
        pack();
        setLocationRelativeTo(null);

        canvas.setFocusable(true);
        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_UP    || k == KeyEvent.VK_W) kUp    = true;
                if (k == KeyEvent.VK_DOWN  || k == KeyEvent.VK_S) kDown  = true;
                if (k == KeyEvent.VK_LEFT  || k == KeyEvent.VK_A) kLeft  = true;
                if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) kRight = true;
                if (k == KeyEvent.VK_P && (screen == Screen.PLAYING || screen == Screen.PAUSED))
                    screen = (screen == Screen.PLAYING) ? Screen.PAUSED : Screen.PLAYING;
                if (k == KeyEvent.VK_ESCAPE && screen != Screen.MENU)
                    screen = Screen.MENU;
                if ((k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) && screen == Screen.MENU)
                    startGame();
            }
            @Override public void keyReleased(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_UP    || k == KeyEvent.VK_W) kUp    = false;
                if (k == KeyEvent.VK_DOWN  || k == KeyEvent.VK_S) kDown  = false;
                if (k == KeyEvent.VK_LEFT  || k == KeyEvent.VK_A) kLeft  = false;
                if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) kRight = false;
            }
        });

        new Timer(16, e -> {
            update();
            render();
            canvas.repaint();
        }).start();
    }

    void initStars() {
        stx = new double[STAR_COUNT]; sty = new double[STAR_COUNT];
        str = new double[STAR_COUNT]; stb = new double[STAR_COUNT]; stp = new double[STAR_COUNT];
        for (int i = 0; i < STAR_COUNT; i++) {
            stx[i] = rng.nextDouble() * W;
            sty[i] = rng.nextDouble() * H;
            str[i] = 0.3 + rng.nextDouble() * 1.4;
            stb[i] = 0.2 + rng.nextDouble() * 0.8;
            stp[i] = rng.nextDouble() * Math.PI * 2;
        }
    }

    void initMenuRings() {
        menuRings.clear();
        for (int i = 0; i < 6; i++)
            menuRings.add(new double[]{
                80 + rng.nextDouble() * (W - 160),
                80 + rng.nextDouble() * (H - 160),
                rng.nextDouble() * 70,
                80 + rng.nextDouble() * 130,
                0.3 + rng.nextDouble() * 0.5,
                160 + rng.nextFloat() * 60
            });
    }

    void startGame() {
        screen = Screen.PLAYING;
        score = 0; lives = 3; combo = 1; level = 1;
        tick = levelTick = comboTimer = deathPause = levelUpTimer = 0;
        ringTimer = orbTimer = shardTimer = 0;
        px = W / 2.0; py = H / 2.0; pvx = pvy = 0; invTicks = 0; playerPulse = 0;
        trail.clear(); rings.clear(); orbs.clear(); shards.clear(); particles.clear();
    }

    void update() {
        starScroll = (starScroll + 0.15) % H;
        for (int i = 0; i < STAR_COUNT; i++) stp[i] += 0.015;
        menuTick += 0.05;
        for (double[] r : menuRings) { r[2] += r[4]; if (r[2] > r[3]) r[2] = 0; }
        if (flashAlpha > 0) { flashAlpha -= 0.032; flashScale = Math.max(1.0, flashScale - 0.04); }

        if (screen == Screen.MENU || screen == Screen.PAUSED) return;

        tick++; levelTick++;

        if (screen == Screen.PLAYING && levelTick >= 800 + level * 400) {
            levelTick = 0; level++; levelUpTimer = 130;
            flash("LEVEL " + level, C_GOLD);
            shockwave(W / 2.0, H / 2.0, C_GOLD);
        }
        if (levelUpTimer > 0) levelUpTimer--;

        if (screen == Screen.DEAD) {
            if (deathPause > 0) deathPause--;
            else {
                if (lives <= 0) {
                    if (score > highScore) highScore = score;
                    screen = Screen.MENU;
                } else {
                    screen = Screen.PLAYING;
                    px = W / 2.0; py = H / 2.0; pvx = pvy = 0; invTicks = 180;
                    combo = 1; comboTimer = 0;
                    rings.clear(); shards.clear();
                }
            }
            updateParticles();
            return;
        }

        if (combo > 1 && --comboTimer <= 0) combo = 1;

        if (kUp)    pvy -= 0.45;
        if (kDown)  pvy += 0.45;
        if (kLeft)  pvx -= 0.45;
        if (kRight) pvx += 0.45;
        double spd = Math.hypot(pvx, pvy);
        if (spd > 5.5) { pvx = pvx / spd * 5.5; pvy = pvy / spd * 5.5; }
        pvx *= 0.84; pvy *= 0.84;
        px += pvx; py += pvy;
        if (px < 10)     { px = 10;     pvx =  Math.abs(pvx) * 0.6; }
        if (px > W - 10) { px = W - 10; pvx = -Math.abs(pvx) * 0.6; }
        if (py < 10)     { py = 10;     pvy =  Math.abs(pvy) * 0.6; }
        if (py > H - 10) { py = H - 10; pvy = -Math.abs(pvy) * 0.6; }
        trail.add(0, new double[]{px, py});
        if (trail.size() > 22) trail.remove(trail.size() - 1);
        if (invTicks > 0) invTicks--;
        playerPulse += 0.1;

        spawnEntities();

        Iterator<double[]> ri = rings.iterator();
        while (ri.hasNext()) {
            double[] r = ri.next();
            r[2] += r[4]; r[7] += r[8]; r[9] += 0.07;
            if (r[2] > r[3]) { ri.remove(); continue; }
            if (invTicks == 0) {
                double d = Math.hypot(px - r[0], py - r[1]);
                if (Math.abs(d - r[2]) < r[5] / 2.0 + 9) { hitPlayer(); break; }
            }
        }

        Iterator<double[]> oi = orbs.iterator();
        while (oi.hasNext()) {
            double[] o = oi.next();
            o[0] += o[2]; o[1] += o[3]; o[5] += 0.08;
            if (o[0] < -30 || o[0] > W + 30 || o[1] < -30 || o[1] > H + 30) {
                oi.remove(); combo = Math.max(1, combo - 1); continue;
            }
            if (Math.hypot(px - o[0], py - o[1]) < o[6] + 10) { collectOrb(o); oi.remove(); }
        }

        Iterator<double[]> si = shards.iterator();
        while (si.hasNext()) {
            double[] s = si.next();
            s[0] += s[2]; s[1] += s[3]; s[6] += s[7]; s[8] += 0.1;
            if (s[0] < -40 || s[0] > W + 40 || s[1] < -40 || s[1] > H + 40) { si.remove(); continue; }
            if (invTicks == 0 && Math.hypot(px - s[0], py - s[1]) < s[4] * 0.85 + 8.5) {
                si.remove(); hitPlayer();
            }
        }

        updateParticles();
    }

    void spawnEntities() {
        int rI = Math.max(90, 200 - level * 10);
        if (++ringTimer >= rI) { ringTimer = 0; spawnRing(); if (level >= 5) spawnRing(); }

        int oI = Math.max(40, 90 - level * 4);
        if (++orbTimer >= oI) {
            orbTimer = 0;
            double roll = rng.nextDouble();
            spawnOrb(roll < 0.08 ? 1 : roll < 0.25 ? 2 : 0);
        }

        int sI = Math.max(50, 160 - level * 12);
        if (++shardTimer >= sI) {
            shardTimer = 0;
            int c = Math.min(3, 1 + level / 3);
            for (int i = 0; i < c; i++) spawnShard();
        }
    }

    void spawnRing() {
        double cx = 80 + rng.nextDouble() * (W - 160);
        double cy = 80 + rng.nextDouble() * (H - 160);
        double maxR = 70 + rng.nextDouble() * 110;
        double spd  = 0.45 + rng.nextDouble() * 0.55 + level * 0.08;
        double thick = 8 + rng.nextDouble() * 12;
        double hue   = 170 + rng.nextFloat() * 50;
        double rotSpd = (rng.nextDouble() - 0.5) * 0.016;
        rings.add(new double[]{cx, cy, 5, maxR, spd, thick, hue, 0, rotSpd, 0});
    }

    void spawnOrb(int type) {
        int e = rng.nextInt(4);
        double x, y;
        if      (e == 0) { x = r(20, W - 20); y = -20; }
        else if (e == 1) { x = W + 20;        y = r(20, H - 20); }
        else if (e == 2) { x = r(20, W - 20); y = H + 20; }
        else             { x = -20;           y = r(20, H - 20); }
        double ang = Math.atan2(H / 2.0 - y, W / 2.0 - x) + (rng.nextDouble() - 0.5) * 1.2;
        double spd = 0.8 + rng.nextDouble() * 1.6 + level * 0.05;
        double rad = (type == 1) ? 10 : 7;
        orbs.add(new double[]{x, y, Math.cos(ang) * spd, Math.sin(ang) * spd, type, rng.nextDouble() * 6, rad});
    }

    void spawnShard() {
        int e = rng.nextInt(4);
        double x, y;
        if      (e == 0) { x = r(0, W); y = -15; }
        else if (e == 1) { x = W + 15;  y = r(0, H); }
        else if (e == 2) { x = r(0, W); y = H + 15; }
        else             { x = -15;     y = r(0, H); }
        double ang    = Math.atan2(H / 2.0 - y, W / 2.0 - x) + (rng.nextDouble() - 0.5) * 1.8;
        double spd    = 1.5 + rng.nextDouble() * 2.5 + level * 0.07;
        double rad    = 4 + rng.nextDouble() * 7;
        int    sides  = 3 + rng.nextInt(4);
        double rotSpd = (rng.nextDouble() - 0.5) * 0.25;
        shards.add(new double[]{x, y, Math.cos(ang) * spd, Math.sin(ang) * spd,
            rad, sides, rng.nextDouble() * Math.PI * 2, rotSpd, 0});
    }

    void collectOrb(double[] o) {
        int type = (int) o[4];
        int base = (type == 1) ? 50 : (type == 2) ? 30 : 10;
        int pts  = base * Math.min(combo, 16);
        score += pts;
        combo = Math.min(combo + 1, 16);
        comboTimer = 220;
        Color c = (type == 1) ? C_GOLD : (type == 2) ? C_MAGENTA : C_CYAN;
        burst(o[0], o[1], c, 16, 4.5);
        if (combo >= 4) flash("COMBO x" + combo, combo >= 8 ? C_GOLD : C_MAGENTA);
        if (type == 1) { flash("BIG SCORE! +" + pts, C_GOLD); shockwave(o[0], o[1], C_GOLD); }
    }

    void hitPlayer() {
        if (invTicks > 0) return;
        lives--; combo = 1; comboTimer = 0; deathPause = 90;
        screen = Screen.DEAD;
        burstMulti(px, py, new Color[]{C_MAGENTA, C_ORANGE, C_WHITE}, 40, 7);
        flash(lives > 0 ? "SYSTEM HIT!" : "NODE LOST", C_MAGENTA);
    }

    void burst(double x, double y, Color c, int count, double maxSpd) {
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = 0.5 + rng.nextDouble() * maxSpd;
            particles.add(new double[]{x, y, Math.cos(a) * s, Math.sin(a) * s,
                1.0, 0.02 + rng.nextDouble() * 0.04, 1.5 + rng.nextDouble() * 4,
                c.getRed(), c.getGreen(), c.getBlue()});
        }
    }

    void burstMulti(double x, double y, Color[] cols, int count, double maxSpd) {
        for (int i = 0; i < count; i++) burst(x, y, cols[rng.nextInt(cols.length)], 1, maxSpd);
    }

    void shockwave(double x, double y, Color c) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            double s = 3 + rng.nextDouble() * 2;
            particles.add(new double[]{x, y, Math.cos(a) * s, Math.sin(a) * s,
                1.0, 0.025, 2.5, c.getRed(), c.getGreen(), c.getBlue()});
        }
    }

    void updateParticles() {
        Iterator<double[]> it = particles.iterator();
        while (it.hasNext()) {
            double[] p = it.next();
            p[0] += p[2]; p[1] += p[3]; p[2] *= 0.93; p[3] *= 0.93; p[4] -= p[5];
            if (p[4] <= 0 || p[6] < 0.3) it.remove();
        }
    }

    void flash(String text, Color c) {
        flashText = text; flashColor = c; flashAlpha = 1.0; flashScale = 1.6;
    }

    void render() {
        bg.setColor(C_BG);
        bg.fillRect(0, 0, W, H);

        bg.setColor(new Color(0, 245, 255, 10));
        bg.setStroke(new BasicStroke(0.7f));
        int off = (int)(starScroll % 45);
        for (int x = 0; x < W; x += 45) bg.draw(new Line2D.Double(x, 0, x, H));
        for (int y = -off; y < H; y += 45) bg.draw(new Line2D.Double(0, y, W, y));

        for (int i = 0; i < STAR_COUNT; i++) {
            double bright = stb[i] * (0.7 + 0.3 * Math.sin(stp[i]));
            bg.setColor(new Color(160, 220, 255, (int)(bright * 180)));
            double sy = (sty[i] + starScroll) % H;
            bg.fill(new Ellipse2D.Double(stx[i] - str[i], sy - str[i], str[i] * 2, str[i] * 2));
        }

        if (screen == Screen.MENU) { renderMenu(); return; }

        renderRings();
        renderOrbs();
        renderShards();
        renderParticles();
        if (screen != Screen.DEAD || deathPause > 45) renderPlayer();
        renderHUD();
        if (levelUpTimer > 0) renderLevelBanner();
        if (screen == Screen.PAUSED) renderPause();
        if (screen == Screen.DEAD && deathPause > 0) {
            float a = (float)(deathPause / 90.0) * 0.5f;
            RadialGradientPaint v = new RadialGradientPaint(W / 2f, H / 2f, W * 0.7f,
                new float[]{0.5f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(200, 0, 50, (int)(a * 200))});
            bg.setPaint(v); bg.fillRect(0, 0, W, H); bg.setPaint(null);
        }
        if (flashAlpha > 0) renderFlash();
    }

    void renderPlayer() {
        if (invTicks > 0 && (invTicks / 4) % 2 == 0) return;
        for (int i = 0; i < trail.size(); i++) {
            double[] t = trail.get(i);
            float a  = (float)(1.0 - (double) i / trail.size()) * 0.45f;
            float tr = (float)(10 * 0.7 * (1 - (double) i / trail.size()));
            if (tr < 1) tr = 1;
            bg.setColor(new Color(0f, 0.96f, 1f, a));
            bg.fill(new Ellipse2D.Double(t[0] - tr, t[1] - tr, tr * 2, tr * 2));
        }
        double glow = 0.7 + 0.3 * Math.sin(playerPulse);
        RadialGradientPaint og = new RadialGradientPaint((float) px, (float) py, 32f,
            new float[]{0f, 0.5f, 1f},
            new Color[]{new Color(0, 245, 255, (int)(60 * glow)), new Color(0, 200, 255, (int)(30 * glow)), new Color(0, 0, 0, 0)});
        bg.setPaint(og);
        bg.fill(new Ellipse2D.Double(px - 32, py - 32, 64, 64));
        RadialGradientPaint core = new RadialGradientPaint((float)(px - 3), (float)(py - 3), 10f,
            new float[]{0f, 0.5f, 1f}, new Color[]{Color.WHITE, new Color(100, 240, 255), new Color(0, 180, 220)});
        bg.setPaint(core);
        bg.fill(new Ellipse2D.Double(px - 10, py - 10, 20, 20));
        bg.setPaint(null);
        bg.setColor(new Color(255, 255, 255, 160));
        bg.fill(new Ellipse2D.Double(px - 4.5, py - 5.5, 5.5, 4.5));
        bg.setColor(new Color(0, 245, 255, (int)(100 * glow)));
        bg.setStroke(new BasicStroke(1.2f));
        double orR = 16.5;
        bg.draw(new Ellipse2D.Double(px - orR, py - orR, orR * 2, orR * 2));
        double da = playerPulse * 1.4;
        bg.setColor(new Color(0, 245, 255, 200));
        bg.fill(new Ellipse2D.Double(px + Math.cos(da) * orR - 2, py + Math.sin(da) * orR - 2, 4, 4));
        bg.setStroke(new BasicStroke(1f));
    }

    void renderRings() {
        for (double[] r : rings) {
            double alpha = Math.min(1.0, Math.max(0.05, (r[3] - r[2]) / 60.0));
            Color base = Color.getHSBColor((float)(r[6] / 360f), 0.7f + 0.3f * (float) Math.sin(r[9]), 1f);
            for (int i = 4; i >= 1; i--) {
                float a = (float)(alpha * 0.15 * (5 - i));
                bg.setColor(new Color(base.getRed() / 255f, base.getGreen() / 255f, base.getBlue() / 255f, a));
                bg.setStroke(new BasicStroke((float)(r[5] + i * 8)));
                double gr = r[2] + i * 4;
                bg.draw(new Ellipse2D.Double(r[0] - gr, r[1] - gr, gr * 2, gr * 2));
            }
            bg.setColor(new Color(base.getRed() / 255f, base.getGreen() / 255f, base.getBlue() / 255f, (float) alpha));
            bg.setStroke(new BasicStroke((float) r[5], BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                10f, new float[]{12f, 8f}, (float)(tick * 0.3)));
            bg.draw(new Ellipse2D.Double(r[0] - r[2], r[1] - r[2], r[2] * 2, r[2] * 2));
            bg.setColor(new Color(1f, 1f, 1f, (float)(alpha * 0.4f)));
            bg.setStroke(new BasicStroke(1.5f));
            bg.draw(new Ellipse2D.Double(r[0] - r[2], r[1] - r[2], r[2] * 2, r[2] * 2));
            bg.setStroke(new BasicStroke(2f));
            for (int i = 0; i < 8; i++) {
                double ang = r[7] + i * Math.PI / 4;
                double x1  = r[0] + Math.cos(ang) * (r[2] - r[5] / 2 - 2);
                double y1  = r[1] + Math.sin(ang) * (r[2] - r[5] / 2 - 2);
                double x2  = r[0] + Math.cos(ang) * (r[2] + r[5] / 2 + 2);
                double y2  = r[1] + Math.sin(ang) * (r[2] + r[5] / 2 + 2);
                bg.setColor(new Color(1f, 1f, 1f, (float)(alpha * 0.5)));
                bg.draw(new Line2D.Double(x1, y1, x2, y2));
            }
            bg.setStroke(new BasicStroke(1f));
        }
    }

    void renderOrbs() {
        for (double[] o : orbs) {
            int type = (int) o[4];
            double pulse = o[5], cx = o[0], cy = o[1], rad = o[6];
            double glow = 0.8 + 0.2 * Math.sin(pulse);
            Color base, bright, glowC;
            if (type == 1) {
                base = new Color(255, 200, 0); bright = new Color(255, 245, 150); glowC = new Color(255, 180, 0);
            } else if (type == 2) {
                base = new Color(255, 60, 180); bright = new Color(255, 180, 230); glowC = new Color(255, 0, 140);
            } else {
                base = new Color(0, 220, 255); bright = new Color(180, 248, 255); glowC = new Color(0, 180, 220);
            }
            for (int i = 4; i >= 1; i--) {
                float a = (float)(0.22 * glow * (1 - i / 5f));
                bg.setColor(new Color(glowC.getRed() / 255f, glowC.getGreen() / 255f, glowC.getBlue() / 255f, a));
                double gr = rad + i * 4;
                bg.fill(new Ellipse2D.Double(cx - gr, cy - gr, gr * 2, gr * 2));
            }
            RadialGradientPaint p = new RadialGradientPaint(
                (float)(cx - rad * 0.3f), (float)(cy - rad * 0.3f), (float) rad,
                new float[]{0f, 0.6f, 1f}, new Color[]{bright, base, glowC});
            bg.setPaint(p);
            bg.fill(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
            bg.setPaint(null);
            bg.setColor(new Color(255, 255, 255, 200));
            bg.fill(new Ellipse2D.Double(cx - rad * 0.4, cy - rad * 0.55, rad * 0.45, rad * 0.35));
            if (type == 1) {
                double rr = rad * 1.8 * glow;
                bg.setColor(new Color(255, 200, 0, (int)(60 * glow)));
                bg.setStroke(new BasicStroke(1.5f));
                bg.draw(new Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
                bg.setStroke(new BasicStroke(1f));
                for (int i = 0; i < 4; i++) {
                    double a = pulse * 1.2 + i * Math.PI / 2;
                    bg.setColor(new Color(255, 220, 80, 130));
                    bg.draw(new Line2D.Double(
                        cx + Math.cos(a) * rad * 1.2, cy + Math.sin(a) * rad * 1.2,
                        cx + Math.cos(a) * rad * 2.0, cy + Math.sin(a) * rad * 2.0));
                }
            }
        }
    }

    void renderShards() {
        for (double[] s : shards) {
            double cx = s[0], cy = s[1], rad = s[4];
            int sides = (int) s[5];
            double rot = s[6], pulse = s[8];
            double glow = 0.75 + 0.25 * Math.sin(pulse);
            for (int i = 3; i >= 1; i--) {
                bg.setColor(new Color(1f, 0.15f, 0.05f, (float)(0.12 * (1 - i / 4f))));
                bg.fill(ngon(cx, cy, rad + i * 5, sides, rot));
            }
            bg.setColor(new Color(220, 50, 10));
            bg.fill(ngon(cx, cy, rad, sides, rot));
            bg.setColor(new Color(255, 140, 20, (int)(200 * glow)));
            bg.setStroke(new BasicStroke(2f));
            bg.draw(ngon(cx, cy, rad, sides, rot));
            bg.setColor(new Color(255, 230, 80, (int)(180 * glow)));
            bg.fill(ngon(cx, cy, rad * 0.45, sides, rot + Math.PI / sides));
            bg.setStroke(new BasicStroke(1f));
            for (int i = 0; i < sides; i++) {
                double a = rot + i * 2 * Math.PI / sides;
                bg.setColor(new Color(255, 160, 20, 120));
                bg.draw(new Line2D.Double(
                    cx + Math.cos(a) * rad, cy + Math.sin(a) * rad,
                    cx + Math.cos(a) * (rad + 8 * glow), cy + Math.sin(a) * (rad + 8 * glow)));
            }
        }
    }

    Polygon ngon(double cx, double cy, double r, int n, double start) {
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            double a = start + i * 2 * Math.PI / n;
            xs[i] = (int)(cx + Math.cos(a) * r);
            ys[i] = (int)(cy + Math.sin(a) * r);
        }
        return new Polygon(xs, ys, n);
    }

    void renderParticles() {
        for (double[] p : particles) {
            float a = (float) Math.max(0, Math.min(1, p[4]));
            bg.setColor(new Color((int) p[7] / 255f, (int) p[8] / 255f, (int) p[9] / 255f, a * 0.85f));
            bg.fill(new Ellipse2D.Double(p[0] - p[6], p[1] - p[6], p[6] * 2, p[6] * 2));
        }
    }

    void renderHUD() {
        GradientPaint tp = new GradientPaint(0, 0, new Color(0, 0, 20, 220), W, 0, new Color(0, 0, 30, 220));
        bg.setPaint(tp); bg.fillRect(0, 0, W, 50); bg.setPaint(null);
        bg.setColor(new Color(0, 245, 255, 45));
        bg.setStroke(new BasicStroke(1f));
        bg.draw(new Line2D.Double(0, 50, W, 50));

        hudBlock(12,    6, "SCORE", String.format("%07d", score), C_CYAN);
        hudBlock(170,   6, "LIVES", "♥ ".repeat(lives) + "♡ ".repeat(3 - lives).trim(), lives == 1 ? C_MAGENTA : C_CYAN);

        bg.setFont(new Font("Monospaced", Font.BOLD, 14));
        FontMetrics fm = bg.getFontMetrics();
        String title = "VØRTEX DRIFT";
        glowText(title, W / 2 - fm.stringWidth(title) / 2, 34, C_MAGENTA, 7);

        Color cc = combo >= 8 ? C_GOLD : combo >= 4 ? C_MAGENTA : C_CYAN;
        hudBlock(W - 215, 6, "COMBO", "x" + combo, cc);
        hudBlock(W - 78,  6, "LEVEL", String.format("%02d", level), C_CYAN);

        bg.setPaint(new GradientPaint(0, H - 33, new Color(0, 0, 20, 200), W, H - 33, new Color(0, 0, 30, 200)));
        bg.fillRect(0, H - 33, W, 33); bg.setPaint(null);
        bg.setColor(new Color(0, 245, 255, 35));
        bg.draw(new Line2D.Double(0, H - 33, W, H - 33));
        bg.setFont(new Font("Monospaced", Font.PLAIN, 11));
        bg.setColor(new Color(0, 245, 255, 80));
        fm = bg.getFontMetrics();
        bg.drawString("ARROWS/WASD: MOVE", 12, H - 12);
        bg.drawString("P: PAUSE | ESC: MENU", W - fm.stringWidth("P: PAUSE | ESC: MENU") - 12, H - 12);
        String hs = "BEST: " + String.format("%07d", highScore);
        bg.drawString(hs, W / 2 - fm.stringWidth(hs) / 2, H - 12);
    }

    void hudBlock(int x, int y, String label, String value, Color c) {
        bg.setFont(new Font("Monospaced", Font.PLAIN, 9));
        bg.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 100));
        bg.drawString(label, x, y + 12);
        bg.setFont(new Font("Monospaced", Font.BOLD, 20));
        bg.setColor(c);
        bg.drawString(value, x, y + 36);
    }

    void glowText(String s, int x, int y, Color c, int rad) {
        for (int i = rad; i >= 1; i--) {
            float a = 0.05f * (rad - i + 1);
            bg.setColor(new Color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, Math.min(1, a)));
            bg.drawString(s, x - i, y); bg.drawString(s, x + i, y);
            bg.drawString(s, x, y - i); bg.drawString(s, x, y + i);
        }
        bg.setColor(c);
        bg.drawString(s, x, y);
    }

    void renderFlash() {
        Font f = new Font("Monospaced", Font.BOLD, (int)(40 * flashScale));
        bg.setFont(f);
        FontMetrics fm = bg.getFontMetrics();
        int tx = W / 2 - fm.stringWidth(flashText) / 2;
        int ty = H / 2 + fm.getAscent() / 2 - 30;
        for (int i = 6; i >= 1; i--) {
            float a = (float)(flashAlpha * 0.1 * (7 - i));
            bg.setColor(new Color(flashColor.getRed() / 255f, flashColor.getGreen() / 255f, flashColor.getBlue() / 255f, a));
            bg.drawString(flashText, tx - i, ty - i);
            bg.drawString(flashText, tx + i, ty + i);
        }
        bg.setColor(new Color(flashColor.getRed() / 255f, flashColor.getGreen() / 255f, flashColor.getBlue() / 255f, (float) flashAlpha));
        bg.drawString(flashText, tx, ty);
    }

    void renderLevelBanner() {
        double t = levelUpTimer / 130.0;
        float a = (t < 0.3f) ? (float)(t / 0.3) : 1f;
        bg.setFont(new Font("Monospaced", Font.BOLD, 34));
        FontMetrics fm = bg.getFontMetrics();
        String msg = "DRIFT INTENSIFIES — LEVEL " + level;
        int mx = W / 2 - fm.stringWidth(msg) / 2;
        int my = H / 2 + fm.getAscent() / 2;
        for (int i = 7; i >= 1; i--) {
            bg.setColor(new Color(1f, 0.84f, 0f, a * 0.07f));
            bg.drawString(msg, mx - i, my - i);
            bg.drawString(msg, mx + i, my + i);
        }
        bg.setColor(new Color(1f, 0.84f, 0f, a));
        bg.drawString(msg, mx, my);
    }

    void renderPause() {
        bg.setColor(new Color(0, 0, 10, 150));
        bg.fillRect(0, 0, W, H);
        bg.setFont(new Font("Monospaced", Font.BOLD, 40));
        FontMetrics fm = bg.getFontMetrics();
        String msg = "DRIFT SUSPENDED";
        glowText(msg, W / 2 - fm.stringWidth(msg) / 2, H / 2 + 14, C_CYAN, 8);
        bg.setFont(new Font("Monospaced", Font.PLAIN, 13));
        bg.setColor(new Color(0, 245, 255, 130));
        fm = bg.getFontMetrics();
        String sub = "Press P to resume";
        bg.drawString(sub, W / 2 - fm.stringWidth(sub) / 2, H / 2 + 52);
    }

    void renderMenu() {
        for (double[] r : menuRings) {
            float a = (float)(0.2 * (r[2] / r[3]));
            bg.setColor(new Color(0f, 0.9f, 1f, Math.max(0, Math.min(1, a))));
            bg.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                10f, new float[]{10f, 8f}, (float)(menuTick * 12)));
            bg.draw(new Ellipse2D.Double(r[0] - r[2], r[1] - r[2], r[2] * 2, r[2] * 2));
        }
        bg.setStroke(new BasicStroke(1f));

        double ga = 0.7 + 0.3 * Math.sin(menuTick);
        bg.setFont(new Font("Monospaced", Font.BOLD, 78));
        FontMetrics fm = bg.getFontMetrics();
        String t1 = "VØRTEX";
        glowText(t1, W / 2 - fm.stringWidth(t1) / 2, 170, new Color(0, 245, 255, (int)(255 * ga)), 20);

        bg.setFont(new Font("Monospaced", Font.BOLD, 36));
        fm = bg.getFontMetrics();
        String t2 = "D R I F T";
        glowText(t2, W / 2 - fm.stringWidth(t2) / 2, 215, new Color(255, 0, 168, (int)(255 * ga)), 12);

        bg.setColor(new Color(0, 245, 255, 55));
        bg.draw(new Line2D.Double(W / 2 - 180, 233, W / 2 + 180, 233));

        bg.setFont(new Font("Monospaced", Font.PLAIN, 12));
        fm = bg.getFontMetrics();
        bg.setColor(new Color(0, 245, 255, 150));
        String tag = "Navigate your quantum node through collapsing vortex rings";
        bg.drawString(tag, W / 2 - fm.stringWidth(tag) / 2, 262);

        bg.setColor(new Color(0, 245, 255, 14));
        bg.fillRoundRect(W / 2 - 270, 284, 540, 220, 8, 8);
        bg.setColor(new Color(0, 245, 255, 38));
        bg.drawRoundRect(W / 2 - 270, 284, 540, 220, 8, 8);
        bg.setFont(new Font("Monospaced", Font.PLAIN, 12));
        String[][] rows = {
            {"↑↓←→ / WASD ", "Move quantum node"},
            {"CYAN ORB   ● ", "+10 pts × combo"},
            {"GOLD ORB   ◆ ", "+50 pts × combo (BIG BONUS)"},
            {"PINK ORB   ● ", "+30 pts × combo"},
            {"VORTEX RING ○", "DANGER — dodge the expanding band"},
            {"SHARD       ◆", "DANGER — spinning entropy projectile"},
            {"P            ", "Pause / Resume"},
        };
        Color[] rc = {C_CYAN, new Color(0, 200, 255), C_GOLD, C_MAGENTA, new Color(80, 180, 255), new Color(255, 100, 20), C_CYAN};
        for (int i = 0; i < rows.length; i++) {
            bg.setColor(rc[i]);
            bg.drawString(rows[i][0], W / 2 - 255, 308 + i * 26);
            bg.setColor(new Color(0, 245, 255, 110));
            bg.drawString("— " + rows[i][1], W / 2 - 90, 308 + i * 26);
        }

        if ((int)(menuTick * 20 / Math.PI) % 2 == 0) {
            bg.setFont(new Font("Monospaced", Font.BOLD, 17));
            fm = bg.getFontMetrics();
            String btn = "[ PRESS ENTER / SPACE TO START ]";
            glowText(btn, W / 2 - fm.stringWidth(btn) / 2, 575, C_CYAN, 7);
        }
        if (highScore > 0) {
            bg.setFont(new Font("Monospaced", Font.PLAIN, 11));
            fm = bg.getFontMetrics();
            String hs = "HIGH SCORE: " + String.format("%07d", highScore);
            bg.setColor(new Color(255, 215, 0, 160));
            bg.drawString(hs, W / 2 - fm.stringWidth(hs) / 2, 610);
        }
        bg.setFont(new Font("Monospaced", Font.PLAIN, 9));
        bg.setColor(new Color(0, 245, 255, 40));
        bg.drawString("VORTEX DRIFT v1.0 | Pure Java Swing", 10, H - 8);
    }

    double r(double a, double b) {
        return a + rng.nextDouble() * (b - a);
    }
}
