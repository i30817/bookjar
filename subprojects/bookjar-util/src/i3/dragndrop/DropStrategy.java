package i3.dragndrop;

import java.awt.dnd.DropTargetDropEvent;

public interface DropStrategy {

    /**
     *
     * @param drop
     * @param callback callback to handle drop
     * @throws java.lang.Exception
     */
    void drop(final DropTargetDropEvent drop) throws Exception;
}
