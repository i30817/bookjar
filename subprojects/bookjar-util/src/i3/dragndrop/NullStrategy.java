package i3.dragndrop;

import java.awt.dnd.DropTargetDropEvent;
import org.apache.logging.log4j.LogManager;

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
        LogManager.getLogger().error("failed to create a dropstrategy for the dataflavor " + flavor);
        drop.rejectDrop();
    }
}
