package i3.dragndrop;

import java.awt.dnd.DropTargetDropEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import i3.io.IoUtils;

/**
 *
 * @author Owner
 */
public final class NullStrategy implements DropStrategy {

    private String flavor;

    public NullStrategy(String humanName) {
        this.flavor = humanName;
    }

    @Override
    public void drop(DropTargetDropEvent drop) throws Exception {
        IoUtils.log.severe("failed to create a dropstrategy for the dataflavor " + flavor);
        drop.rejectDrop();
    }
}
