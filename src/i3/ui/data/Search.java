package i3.ui.data;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.books.Books;
import com.google.api.services.books.BooksRequestInitializer;
import com.google.api.services.books.model.Volume;
import com.google.api.services.books.model.Volume.VolumeInfo;
import com.google.api.services.books.model.Volume.VolumeInfo.ImageLinks;
import com.google.api.services.books.model.Volume.VolumeInfo.IndustryIdentifiers;
import i3.main.Book;
import i3.util.Tuples.T2;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.logging.log4j.LogManager;

public class Search {

    private final Book bookField;
    private HttpResponse currentlyDownloading;
    private static volatile int failedDownloading;
    private static final int TIMEOUT = 1000 * 15; //15 seconds
    private static final int MAXIMUM_DOWNLOAD_FAILURES = 30;

    private static class GoogleNet {

        static Books client;
        final static Map<Book, Boolean> noImg
                = Collections.synchronizedMap(new IdentityHashMap<Book, Boolean>());

        static {
            try {
                HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

                BooksRequestInitializer key = new BooksRequestInitializer("AIzaSyC96zafy6K9uCvjRbxEkl142zP5Je_EIvk");
                HttpRequestInitializer timeout = new HttpRequestInitializer() {

                    @Override
                    public void initialize(HttpRequest request) throws IOException {
                        request.setConnectTimeout(TIMEOUT);
                        request.setReadTimeout(TIMEOUT);
                        request.setNumberOfRetries(0);//lol it's 10 by default
                    }
                };

                client = new Books.Builder(httpTransport, GsonFactory.getDefaultInstance(), null)
                        .setHttpRequestInitializer(timeout)
                        .setBooksRequestInitializer(key)
                        .setApplicationName("bookjar-google").build();
            } catch (Exception ex) {
                LogManager.getLogger().error("google api not available, will not download images", ex);
            }
        }
    }

    public Search(Book book) {
        super();
        this.bookField = book;
    }

//    Charles Stross - [Laundry SS] - Overtime
    public BufferedImage searchImage() {
        if (GoogleNet.client == null || failedDownloading >= MAXIMUM_DOWNLOAD_FAILURES
                || GoogleNet.noImg.containsKey(bookField)) {
            return null;
        }
        T2<String[], String> localTuple = bookField.authorsAndTitle();
        String book = localTuple.getSecond();
        if ("".equals(book) || book == null) {
            return null;
        }
        String authorsQuery = createQuery(localTuple.getFirst());
        try {
            List<Volume> vols = GoogleNet.client.volumes()
                    .list("intitle:\"" + book + "\"" + authorsQuery)
                    .setMaxResults(1L)
                    .setShowPreorders(true)
                    //.setPrettyPrint(true)
                    .setFields("items(volumeInfo(imageLinks/*,industryIdentifiers/*))")
                    .execute().getItems();
//            System.out.println(book + " : " + authorsQuery + "\n" + vols);
            if (vols == null) {
                return null;
            }
            VolumeInfo v = vols.get(0).getVolumeInfo();
            ImageLinks images = v.getImageLinks();
//            System.out.prinln(v);
            String image = null;
            if (images != null) {
                image = images.getMedium();
                if (image == null) {
                    image = images.getSmall();
                }
                if (image == null) {
                    image = images.getLarge();
                }
                if (image == null) {
                    image = images.getExtraLarge();
                }
            }
            if (image != null) {
                return streamCover(image.replace("&edge=curl", ""));//lame 'open curl' effect
            } else if (images != null && (image = images.getThumbnail()) != null) {
                //although some images have a larger version if you remove the
                //&zoom=1 parameter, sometimes it's not a cover.
                return streamCover(image.replace("&edge=curl", ""));
            } else if (v.getIndustryIdentifiers() != null) {//try open library
                for (IndustryIdentifiers id : v.getIndustryIdentifiers()) {
                    if ("ISBN_13".equals(id.getType())) {
                        String possibleCover = "http://covers.openlibrary.org/b/isbn/" + id.getIdentifier() + "-M.jpg?default=false";
                        return streamCover(possibleCover);
                    }
                }
            }
        } catch (IOException blah) {
            LogManager.getLogger().warn("failed to connect to cover webservices: " + blah.getMessage());
            GoogleNet.noImg.put(bookField, Boolean.TRUE);
            failedDownloading++;
        } finally {
            if (failedDownloading >= MAXIMUM_DOWNLOAD_FAILURES) {
                String msg = "image download disabled because of maximum amount("
                        + failedDownloading
                        + ") of network failures";
                LogManager.getLogger().error(msg);
            }
        }
        return null;
    }

    private BufferedImage streamCover(String cover) throws IOException {
        GenericUrl coverURL = new GenericUrl(cover);
        HttpRequest req = GoogleNet.client.getRequestFactory().buildGetRequest(coverURL);
        InputStream image;
        synchronized (this) {
            currentlyDownloading = req.execute();
            image = currentlyDownloading.getContent();
        }
        return ImageIO.read(new BufferedInputStream(image));
    }

    private String createQuery(String[] authors) {
        String authorsQuery = "";
        for (String author : authors) {
            for (String name : author.split("\\s+")) {
                authorsQuery += " inauthor:\"" + name.trim() + "\"";
            }
        }
        return authorsQuery;
    }

    public synchronized void cancel(Exception cause) throws Exception {
        if (currentlyDownloading != null) {
            currentlyDownloading.disconnect();
            LogManager.getLogger().warn("disconnecting image connection for book " + bookField.toString());
        }
    }
}
