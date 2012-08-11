package i3.dragndrop;

import i3.util.Call;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDropEvent;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Owner
 */
public final class URIStrategy implements DropStrategy {

    private Call<List<URL>> urlCallback;

    public URIStrategy(Call<List<URL>> urlCall) {
        urlCallback = urlCall;
    }

    @Override
    public void drop(DropTargetDropEvent drop) throws Exception {
        drop.acceptDrop(DnDConstants.ACTION_LINK);
        DataFlavor flavor = new DataFlavor("text/uri-list;class=java.lang.String");
        String s = (String) drop.getTransferable().getTransferData(flavor);
        String[] a = s.split("\n");
        //inverted
        URL url = new URL(a[a.length - 1].trim());
        urlCallback.run(Collections.singletonList(url));
    }
}
