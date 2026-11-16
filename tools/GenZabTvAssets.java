import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Genera los PNG de la marca ZabTV recortando logo/ZabTV.png (tablero 2560x1920 = SVG x2).
 * Sin dependencias: solo JDK (ImageIO escribe PNG).
 *
 * Regiones (unidades PNG = SVG x2):
 * - Pastilla app: SVG rect(200,735,96,96) -> PNG (400,1470,192,192).
 * - Lockup principal: SVG (300,240,620,220) -> PNG (600,480,1240,440).
 *
 * Uso: java tools/GenZabTvAssets.java  (desde la raiz del repo)
 */
public class GenZabTvAssets {

    static final String[] DENS = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
    static final int[] ICON_SIZES = {48, 72, 96, 144, 192};
    static final String[] BDENS = {"drawable-mdpi", "drawable-hdpi", "drawable-xhdpi", "drawable-xxhdpi", "drawable-xxxhdpi"};
    static final int[][] BANNER_SIZES = {{160, 90}, {240, 135}, {320, 180}, {480, 270}, {640, 360}};
    static final int BG = 0xFF06080B; // fondo del tablero / banner TV
    static final Color BRAND_BLUE = new Color(0x0788FF);
    static final Color BRAND_INK = new Color(0xF7F9FC);
    /** Z exacta de logo/ZabTV.svg trasladada a viewport 256 (ver vectores). */
    static final double GLYPH_SCALE = 0.66; // aire para que el circulo no corte
    static final String Z = "M48,51.2H208C211.2,51.2 212.8,52.8 212.8,56V80.8C212.8,82.93 212,84.8 210.4,86.4L155.2,129.6H171.2C174.4,129.6 174.93,130.93 172.8,133.6L131.2,169.6H208C211.2,169.6 212.8,171.2 212.8,174.4V200C212.8,203.2 211.2,204.8 208,204.8H48C44.8,204.8 43.2,203.2 43.2,200V175.2C43.2,173.07 44,171.2 45.6,169.6L152,86.4H112V116C112,118.67 110.67,120 108,120H84C81.33,120 80,118.67 80,116V86.4H48C44.8,86.4 43.2,84.8 43.2,81.6V56C43.2,52.8 44.8,51.2 48,51.2Z";

    public static void main(String[] args) throws Exception {
        BufferedImage board = ImageIO.read(new File("logo/ZabTV.png"));
        if (board.getWidth() != 2560 || board.getHeight() != 1920) {
            throw new IllegalStateException("Tablero inesperado: " + board.getWidth() + "x" + board.getHeight());
        }

        // 1) Iconos legacy: pastilla azul a sangre + Z exacta al 66% (igual que
        // los vectores). El recorte a sangre se salia de la mascara circular.
        for (int i = 0; i < DENS.length; i++) {
            BufferedImage composed = renderIcon(ICON_SIZES[i]);
            ImageIO.write(composed, "PNG", new File("app/src/main/res/" + DENS[i] + "/ic_launcher.png"));
            ImageIO.write(circle(composed), "PNG", new File("app/src/main/res/" + DENS[i] + "/ic_launcher_round.png"));
        }

        // 2) Banners TV 16:9: lockup centrado sobre fondo #06080B.
        BufferedImage lockup = board.getSubimage(600, 480, 1240, 440);
        for (int i = 0; i < BDENS.length; i++) {
            int w = BANNER_SIZES[i][0], h = BANNER_SIZES[i][1];
            int lw = w, lh = Math.max(1, Math.round(w * 440f / 1240f));
            BufferedImage art = scale(lockup, lw, lh);
            BufferedImage banner = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = banner.createGraphics();
            g.setColor(new java.awt.Color(BG));
            g.fillRect(0, 0, w, h);
            g.drawImage(art, 0, (h - lh) / 2, null);
            g.dispose();
            ImageIO.write(banner, "PNG", new File("app/src/main/res/" + BDENS[i] + "/banner.png"));
        }
        System.out.println("OK: launcher + round x5 + banner x5");
    }

    /** Icono compuesto: pastilla azul + Z escalada y centrada. */
    static BufferedImage renderIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double u = size / 256.0;
        g.scale(u, u);
        g.setColor(BRAND_BLUE);
        g.fill(new RoundRectangle2D.Double(0, 0, 256, 256, 128, 128));
        g.translate(128, 128);
        g.scale(GLYPH_SCALE, GLYPH_SCALE);
        g.translate(-128, -128);
        g.setColor(BRAND_INK);
        g.fill(parsePath(Z));
        g.dispose();
        return img;
    }

    /** Parser minimo de pathData absoluto (M,L,H,V,C,Z): misma gramatica que VectorDrawable. */
    static Path2D parsePath(String d) {
        List<String> toks = new ArrayList<>();
        Matcher m = Pattern.compile("[A-Za-z]|-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?").matcher(d);
        while (m.find()) toks.add(m.group());
        Path2D p = new Path2D.Double();
        String cmd = "";
        double cx = 0, cy = 0;
        int i = 0;
        while (i < toks.size()) {
            String t = toks.get(i);
            if (t.matches("[A-Za-z]")) {
                cmd = t;
                i++;
                if (cmd.equals("Z")) p.closePath();
                continue;
            }
            switch (cmd) {
                case "M" -> {
                    double x = Double.parseDouble(toks.get(i)), y = Double.parseDouble(toks.get(i + 1));
                    p.moveTo(x, y);
                    cx = x; cy = y; cmd = "L"; i += 2;
                }
                case "L" -> {
                    double x = Double.parseDouble(toks.get(i)), y = Double.parseDouble(toks.get(i + 1));
                    p.lineTo(x, y);
                    cx = x; cy = y; i += 2;
                }
                case "H" -> {
                    double x = Double.parseDouble(toks.get(i));
                    p.lineTo(x, cy);
                    cx = x; i++;
                }
                case "V" -> {
                    double y = Double.parseDouble(toks.get(i));
                    p.lineTo(cx, y);
                    cy = y; i++;
                }
                case "C" -> {
                    p.curveTo(Double.parseDouble(toks.get(i)), Double.parseDouble(toks.get(i + 1)),
                            Double.parseDouble(toks.get(i + 2)), Double.parseDouble(toks.get(i + 3)),
                            Double.parseDouble(toks.get(i + 4)), Double.parseDouble(toks.get(i + 5)));
                    cx = Double.parseDouble(toks.get(i + 4)); cy = Double.parseDouble(toks.get(i + 5));
                    i += 6;
                }
                default -> throw new IllegalStateException("Comando no soportado: " + cmd);
            }
        }
        return p;
    }

    static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    static BufferedImage circle(BufferedImage src) {
        int s = src.getWidth();
        BufferedImage out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(new Ellipse2D.Float(0, 0, s, s));
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
}
