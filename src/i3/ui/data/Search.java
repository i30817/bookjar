package i3.ui.data;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import i3.main.Book;
import i3.main.Bookjar;
import i3.util.Tuples.T2;
import i3.io.FastURLEncoder;
import i3.io.IoUtils;

public class Search {
    public static final int READ_TIMEOUT = 5000;
    private volatile HttpURLConnection interruptableConn;
    private final Book bookField;
    private static final String SEARCH_SERVICE = "http://openlibrary.org/api/search?q=";
    private static final String IMAGE_SERVICE = "http://covers.openlibrary.org/b/olid/";
    private static final Pattern SEARCH_PATTERN = Pattern.compile("\"/b/(.+?)\"");
    //warning : shared boolean for exceptions...
    private static volatile int failedDownloading;
    private static final int MAXIMUM_DOWNLOAD_FAILURES = 15;

    public Search(Book book) {
        super();
        this.bookField = book;
    }

    public BufferedImage searchImage() {
        if (failedDownloading >= MAXIMUM_DOWNLOAD_FAILURES) {
            return null;
        }
        T2<String[], String> localTuple = bookField.authorsAndTitle();
        String book = localTuple.getSecond();
        if ("".equals(book) || book == null) {
            return null;
        }
        String authorsQuery = createQuery(localTuple.getFirst());
        if ("".equals(authorsQuery)) {
            return null;
        }
        String response = query(book, authorsQuery);
        if (response == null) {
            return null;
        }

        try {
            return searchImage(SEARCH_PATTERN.matcher(response));
        } catch (IOException blah) {
            Bookjar.log.log(Level.CONFIG, "Image service failure", blah);
            failedDownloading++;
        }
        return null;
    }

    private String createQuery(String[] authors) {
        String authorsQuery = "";
        for (String author : authors) {
            if ("".equals(authorsQuery)) {
                authorsQuery += "authors:(" + author + ")";
            } else {
                authorsQuery += " OR authors:(" + author + ")";
            }
        }
        return authorsQuery;
    }

    private String query(String book, String authorsQuery) {
        String query = "{\"query\":\" title:(" + book + ") AND (" + authorsQuery + ") NOT (audio OR Audio)\"}";
        try {
            URL url = new URL(SEARCH_SERVICE + FastURLEncoder.encode(query));
            interruptableConn = (HttpURLConnection) url.openConnection();
            return IoUtils.toString(interruptableConn.getInputStream(), true);
        } catch (Exception blah) {
            Bookjar.log.log(Level.CONFIG, "Image service failure", blah);
            failedDownloading++;
            return null;
        }
    }

    private BufferedImage searchImage(Matcher m) throws IOException {
        InputStream stream = null;
        while (m.find()) {
            try {
                URL url = new URL(IMAGE_SERVICE + m.group(1) + "-M.jpg?default=false");
                interruptableConn = (HttpURLConnection) url.openConnection();
                interruptableConn.setUseCaches(false);
                interruptableConn.setReadTimeout(READ_TIMEOUT);
                stream = interruptableConn.getInputStream();
                return ImageIO.read(new BufferedInputStream(stream));
            } catch (FileNotFoundException notFound) {
                //normal
                continue;
            } finally {
                IoUtils.close(stream);
            }
        }
        return null;
    }

    public void cancel(Exception cause) throws Exception {
        if (interruptableConn != null) {
            interruptableConn.setConnectTimeout(1);
            interruptableConn.setReadTimeout(1);
            interruptableConn.disconnect();
            Bookjar.log.log(Level.WARNING, "Disconnecting url connection {0}", interruptableConn);
        }
    }
}
