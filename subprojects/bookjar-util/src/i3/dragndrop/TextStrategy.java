package i3.dragndrop;

import i3.util.Call;
import i3.util.Factory;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDropEvent;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import i3.io.IoUtils;

/**
 *
 * @author Owner
 */
public final class TextStrategy implements DropStrategy {

    private Call<List<URL>> callback;
    private DataFlavor flavor;

    public TextStrategy(Call<List<URL>> call, DataFlavor bestTextFlavor) {
        callback = call;
        flavor = bestTextFlavor;
    }

    @Override
    public void drop(DropTargetDropEvent drop) throws Exception {
        drop.acceptDrop(DnDConstants.ACTION_LINK);
        BufferedReader bReader = null;
        try {
            Reader reader = flavor.getReaderForText(drop.getTransferable());
            bReader = new BufferedReader(reader);
            String s;
            StringBuilder b = new StringBuilder();
            while ((s = bReader.readLine()) != null) {
                b.append(s);
            }
            b.append("\n");

            String type = flavor.getPrimaryType() + "/" + flavor.getSubType();
            byte [] bytes = b.toString().getBytes("UTF-8");
            URL fakeURL = IoUtils.toFakeURL(new BytesToInputStream(bytes), type, "UTF-8");
            callback.run(Collections.singletonList(fakeURL));
        }
        finally {
            bReader.close();
        }
    }

    private static class BytesToInputStream extends Factory<InputStream, Void>{
        private final byte[] bytes;

        private BytesToInputStream(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public InputStream create(Void arg) throws Exception {
            return new ByteArrayInputStream(bytes);
        }

    }
}
