import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
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

    public static void main(String[] args) throws Exception {
        BufferedImage board = ImageIO.read(new File("logo/ZabTV.png"));
        if (board.getWidth() != 2560 || board.getHeight() != 1920) {
            throw new IllegalStateException("Tablero inesperado: " + board.getWidth() + "x" + board.getHeight());
        }

        // 1) Iconos legacy: recorte exacto de la pastilla + reescalado por mitades.
        BufferedImage tile = board.getSubimage(400, 1470, 192, 192);
        for (int i = 0; i < DENS.length; i++) {
            BufferedImage scaled = downscale(tile, ICON_SIZES[i]);
            ImageIO.write(scaled, "PNG", new File("app/src/main/res/" + DENS[i] + "/ic_launcher.png"));
            ImageIO.write(circle(scaled), "PNG", new File("app/src/main/res/" + DENS[i] + "/ic_launcher_round.png"));
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

    static BufferedImage downscale(BufferedImage src, int target) {
        BufferedImage img = src;
        int w = img.getWidth();
        while (w / 2 >= target) {
            w /= 2;
            img = scale(img, w, w);
        }
        return (img.getWidth() == target) ? img : scale(img, target, target);
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
