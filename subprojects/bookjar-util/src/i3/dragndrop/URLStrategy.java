package i3.dragndrop;

import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDropEvent;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import i3.util.Call;

public final class URLStrategy implements DropStrategy {

    private Call<List<URL>> urlCallback;

    public URLStrategy(Call<List<URL>> urlCall) {
        urlCallback = urlCall;
    }

    @Override
    public void drop(DropTargetDropEvent drop) throws Exception {
        drop.acceptDrop(DnDConstants.ACTION_LINK);
        DataFlavor current = drop.getCurrentDataFlavors()[0];
        URL url = (URL) drop.getTransferable().getTransferData(current);
        urlCallback.run(Collections.singletonList(url));
    }
}
