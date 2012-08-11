package i3.ui.data;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.ImageCapabilities;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.TileObserver;
import java.awt.image.WritableRaster;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.WeakHashMap;

/**
 *
 * @author i30817
 */
@SuppressWarnings({"unchecked"})
public class RandomImage {

    private static BufferedImage randomCover, wrappedRandomCover;
    //used to allow gc of the keys that are marked as using the randomcover.
    private static Map usesRandomPicture = new WeakHashMap();

    /**
     * @param key
     * @return a aleatory image if a aleatory image with the object as
     * key was requested before.
     */
    public synchronized static BufferedImage getValue(Object key) {
        if (usesRandomPicture.containsKey(key)) {
            return randomCover;
        } else {
            return null;
        }
    }

    public synchronized static BufferedImage markAsRandom(Object key, int width, int height) throws Exception {
        if (randomCover == null) {
            randomCover = createRandomImage(height, width);
            wrappedRandomCover = new NoFlushImage();
        }
        usesRandomPicture.put(key, null);
        return wrappedRandomCover;
    }

    private static BufferedImage createRandomImage(int height, int width) throws HeadlessException {
        Random r = new Random();
        float golden = 1.6180339887F;
        width = (int) (height / golden);
        int numberRows = 5 + r.nextInt(5);
        int rowHeight = (int) (height * 0.015F);
        int viewPortY = (int) (height * 0.2F);
        int viewPortX = (int) (width * 0.15F);
        int spaceWidth = (int) (width * 0.05F);
        int minimumWordWidth = (int) (width * 0.10F);
        int viewPortMaxY = (int) (height * 0.8F);
        int viewPortMaxX = (int) (width * 0.8F);
        int rowDescent = ((viewPortMaxY - viewPortY) / numberRows) - rowHeight;
        Color bg = new Color(36, 34, 32);
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = environment.getDefaultScreenDevice();
        GraphicsConfiguration config = device.getDefaultConfiguration();
        BufferedImage img = config.createCompatibleImage(width, height, Transparency.OPAQUE);
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, width, height);
        for (int rw = 0; rw < numberRows; rw++) {
            Color rowColor = getRGBColorFrom(r, bg);
            //try again once.
            if (areColorsSimilar(bg, rowColor)) {
                rowColor = getRGBColorFrom(r, bg);
            }


            int x = viewPortX + (r.nextBoolean() && r.nextBoolean() ? r.nextInt(5) * spaceWidth : 0);
            int words = 1 + r.nextInt(3);
            for (int i = 0; i < words; i++) {
                if (r.nextBoolean()) {
                    if (r.nextBoolean()) {
                        rowColor = rowColor.darker();
                    } else {
                        rowColor = rowColor.brighter();
                    }
                }
                int spaceRemaining = viewPortMaxX - x - (words - i - 1) * (minimumWordWidth + spaceWidth);
                int wordWidth = minimumWordWidth + r.nextInt(spaceRemaining);
                g.setColor(rowColor);
                g.fillRoundRect(x, viewPortY, wordWidth, rowHeight, rowHeight / 6, rowHeight / 6);
                x += wordWidth + spaceWidth;
            }
            viewPortY += rowHeight + rowDescent;
        }
        return img;
    }

    private static Color getRGBColorFrom(Random r, Color bg) {
        float golden = 1.6180339887F;
        int multR = (int) ((1 + r.nextInt(10)) * golden);
        int multG = (int) ((1 + r.nextInt(10)) * golden);
        int multB = (int) ((1 + r.nextInt(10)) * golden);
        return new Color(bg.getRed() * multR % 256, bg.getGreen() * multG % 256, bg.getBlue() * multB % 256);
    }

    private static boolean areColorsSimilar(Color bg, Color rowColor) {
        int squareDistance = (bg.getRGB() - rowColor.getRed()) ^ 2
                + (bg.getGreen() - rowColor.getGreen()) ^ 2
                + (bg.getBlue() - rowColor.getBlue()) ^ 2;
        return squareDistance <= 900; //30 distance
    }

    private static final class NoFlushImage extends BufferedImage {

        public NoFlushImage() {
            super(1, 1, TYPE_BYTE_GRAY);
        }

        public void flush() {
//            randomCover.flush();
        }
        public void setAccelerationPriority(float priority) {
            randomCover.setAccelerationPriority(priority);
        }

        public Image getScaledInstance(int width, int height, int hints) {
            return randomCover.getScaledInstance(width, height, hints);
        }

        public ImageCapabilities getCapabilities(GraphicsConfiguration gc) {
            return randomCover.getCapabilities(gc);
        }

        public float getAccelerationPriority() {
            return randomCover.getAccelerationPriority();
        }

        public String toString() {
            return randomCover.toString();
        }

        public void setRGB(int startX, int startY, int w, int h, int[] rgbArray, int offset, int scansize) {
            randomCover.setRGB(startX, startY, w, h, rgbArray, offset, scansize);
        }

        public synchronized void setRGB(int x, int y, int rgb) {
            randomCover.setRGB(x, y, rgb);
        }

        public void setData(Raster r) {
            randomCover.setData(r);
        }

        public void removeTileObserver(TileObserver to) {
            randomCover.removeTileObserver(to);
        }

        public void releaseWritableTile(int tileX, int tileY) {
            randomCover.releaseWritableTile(tileX, tileY);
        }

        public boolean isTileWritable(int tileX, int tileY) {
            return randomCover.isTileWritable(tileX, tileY);
        }

        public boolean isAlphaPremultiplied() {
            return randomCover.isAlphaPremultiplied();
        }

        public boolean hasTileWriters() {
            return randomCover.hasTileWriters();
        }

        public Point[] getWritableTileIndices() {
            return randomCover.getWritableTileIndices();
        }

        public WritableRaster getWritableTile(int tileX, int tileY) {
            return randomCover.getWritableTile(tileX, tileY);
        }

        public int getWidth(ImageObserver observer) {
            return randomCover.getWidth(observer);
        }

        public int getWidth() {
            return randomCover.getWidth();
        }

        public int getType() {
            return randomCover.getType();
        }

        public int getTransparency() {
            return randomCover.getTransparency();
        }

        public int getTileWidth() {
            return randomCover.getTileWidth();
        }

        public int getTileHeight() {
            return randomCover.getTileHeight();
        }

        public int getTileGridYOffset() {
            return randomCover.getTileGridYOffset();
        }

        public int getTileGridXOffset() {
            return randomCover.getTileGridXOffset();
        }

        public Raster getTile(int tileX, int tileY) {
            return randomCover.getTile(tileX, tileY);
        }

        public BufferedImage getSubimage(int x, int y, int w, int h) {
            return randomCover.getSubimage(x, y, w, h);
        }

        public Vector<RenderedImage> getSources() {
            return randomCover.getSources();
        }

        public ImageProducer getSource() {
            return randomCover.getSource();
        }

        public SampleModel getSampleModel() {
            return randomCover.getSampleModel();
        }

        public WritableRaster getRaster() {
            return randomCover.getRaster();
        }

        public int[] getRGB(int startX, int startY, int w, int h, int[] rgbArray, int offset, int scansize) {
            return randomCover.getRGB(startX, startY, w, h, rgbArray, offset, scansize);
        }

        public int getRGB(int x, int y) {
            return randomCover.getRGB(x, y);
        }

        public String[] getPropertyNames() {
            return randomCover.getPropertyNames();
        }

        public Object getProperty(String name) {
            return randomCover.getProperty(name);
        }

        public Object getProperty(String name, ImageObserver observer) {
            return randomCover.getProperty(name, observer);
        }

        public int getNumYTiles() {
            return randomCover.getNumYTiles();
        }

        public int getNumXTiles() {
            return randomCover.getNumXTiles();
        }

        public int getMinY() {
            return randomCover.getMinY();
        }

        public int getMinX() {
            return randomCover.getMinX();
        }

        public int getMinTileY() {
            return randomCover.getMinTileY();
        }

        public int getMinTileX() {
            return randomCover.getMinTileX();
        }

        public int getHeight(ImageObserver observer) {
            return randomCover.getHeight(observer);
        }

        public int getHeight() {
            return randomCover.getHeight();
        }

        public Graphics getGraphics() {
            return randomCover.getGraphics();
        }

        public Raster getData(Rectangle rect) {
            return randomCover.getData(rect);
        }

        public Raster getData() {
            return randomCover.getData();
        }

        public ColorModel getColorModel() {
            return randomCover.getColorModel();
        }

        public WritableRaster getAlphaRaster() {
            return randomCover.getAlphaRaster();
        }

        public Graphics2D createGraphics() {
            return randomCover.createGraphics();
        }

        public WritableRaster copyData(WritableRaster outRaster) {
            return randomCover.copyData(outRaster);
        }

        public void coerceData(boolean isAlphaPremultiplied) {
            randomCover.coerceData(isAlphaPremultiplied);
        }

        public void addTileObserver(TileObserver to) {
            randomCover.addTileObserver(to);
        }
    }
}
