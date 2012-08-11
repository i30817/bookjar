package i3.dragndrop;

import java.awt.datatransfer.DataFlavor;
import java.net.URL;
import java.util.List;
import i3.util.Call;
import i3.util.Factory;

/**
 * Transforms some common drop types into urls
 * @author Owner
 */
public final class DefaultURLDropFactory extends Factory<DropStrategy, DataFlavor[]> {

    private final Call<List<URL>> urlCall;

    public DefaultURLDropFactory(Call<List<URL>> urlCallback) {
        super();
        urlCall = urlCallback;
    }

    @Override
    public DropStrategy create(DataFlavor[] flavors) {
        //check first for the types that the callback handles
        for (DataFlavor flavor : flavors) {
            if (flavor.isMimeTypeEqual("application/x-java-url")) {
                return new URLStrategy(urlCall);
            } else if (flavor.isFlavorJavaFileListType()) {
                return new FileStrategy(urlCall);
            }
        }
        //won't work because the callback doesn't expect urls that are not files
        //if failed, find the type of the text type if any
//        DataFlavor text = DataFlavor.selectBestTextFlavor(flavors);
//        if(text != null){
//            //transform the stream to a url the callback uses...
//            return new TextDropStrategy(urlCall, text);
//        }
        return new NullStrategy(flavors[0].getHumanPresentableName());
    }
}

