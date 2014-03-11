package i3.ui.data;

import java.awt.image.BufferedImage;
import i3.main.Book;
import i3.swing.component.ImageList;
import i3.thread.Cancelable;

/**
 * Searches for a cover image and returns it if found, or returns a lazy
 * aleatory (per session) image.
 *
 * @author fc30817
 */
public final class CoverSearchOrRandom<E extends Book> extends Cancelable {

    private final Search imageSearch;
    private final ImageList<E> list;
    private final E book;
    private final int imageWidth;
    private final int imageHeight;
    private final String name;
    private boolean enableImageSearch = true;

    public CoverSearchOrRandom(String name, E book, ImageList<E> list, int imageWidth, int imageHeight) {
        this.list = list;
        this.book = book;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.name = name;
        imageSearch = new Search(book);
    }

    @Override
    public void compute() throws Exception {
        if (list.isObjectInvisible(book)) {
            list.deferImage(book);
            return;
        }
        BufferedImage img;
        if (enableImageSearch) {
            img = imageSearch.searchImage();
            if (img != null) {
                img = new ScaleImage(imageWidth, imageHeight).scale(img);
                list.putImage(book, img);
                new WriteImageToCache(name).write(img);
                return;
            }
        }

        img = RandomImage.markAsRandom(book, imageWidth, imageHeight);
        list.putImage(book, img);
    }

    @Override
    protected void cancel(Exception cause) throws Exception {
        imageSearch.cancel(cause);
    }
}
