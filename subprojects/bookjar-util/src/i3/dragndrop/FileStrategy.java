package i3.dragndrop;

import i3.util.Call;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.net.URL;
import java.util.*;

/**
 *
 * @author Owner
 */
public final class FileStrategy implements DropStrategy {

    private Call<List<URL>> urlCallback;

    public FileStrategy(Call<List<URL>> urlCall) {
        urlCallback = urlCall;
    }

    @Override
    public void drop(DropTargetDropEvent drop) throws Exception {
        drop.acceptDrop(DnDConstants.ACTION_LINK);
        List l = (List) drop.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
        File f = (File) l.get(0);
        URL url = f.toURI().toURL();
        urlCallback.run(Collections.singletonList(url));
    }
}
