package i3.swing.component;

import i3.download.Download;

/**
 * Object used as value wrapper in the download list
 * This is not for outer use, and can't be instantiated
 * @author i30817
 */
public final class ModelObject<E> {

    public final E value;
    public final Download download;

    ModelObject(E value, Download download) {
        this.value = value;
        this.download = download;
    }
}
