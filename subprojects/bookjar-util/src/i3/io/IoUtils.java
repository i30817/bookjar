package i3.io;

import i3.util.Strings;
import i3.util.Factory;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLStreamHandler;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mozilla.universalchardet.UniversalDetector;

/**
 * A static util IO class.
 * @author i30817
 */
public final class IoUtils {

    public static final Logger log = Logger.getAnonymousLogger();

    private IoUtils() {
    }
    private static Pattern whiteList = Pattern.compile("[^\\p{L}&&[^\\p{Digit}&-.\\[\\]]]", Pattern.CANON_EQ | Pattern.UNICODE_CASE);

    /**
     * Returns a smaller than 256 chars file with all characters
     * except the alphanumerics and whitespace and some others
     * turned to whitespace
     * (escapes illegal chars in all platforms).
     * This method assumes the parent is safe, ie, it has
     * no illegal character in the file name, is less than 256 chars, existss
     * and can be writen as a directory.
     * @param parent not null, writable
     * @param name
     * @return
     */
    public static Path getSafeFileSystemFile(Path parent, String name) {

        String parentPath = parent.toAbsolutePath().normalize().toString();
        String childPath = whiteList.matcher(name).replaceAll(" ");

        //-1 is seperator
        int maxLen = 255 - parentPath.length() - 1;

        if (childPath.length() > maxLen) {
            String extension = Strings.subStringFromLast(childPath, '.');
            maxLen -= extension.length();

            int i = maxLen - 1;
            while (childPath.charAt(i) != ' ' && i > 0) {
                i--;
            }

            while (childPath.charAt(i) == ' ' && i > 0) {
                i--;
            }

            if (i == 0) {
                childPath = childPath.substring(0, maxLen - 1);
            } else {
                childPath = childPath.substring(0, i + 1);
            }

            return Paths.get(parentPath, childPath + extension);
        }
        return Paths.get(parentPath, childPath);

    }

    /**
     * Finds the parent directory of the class given
     *  @throws IllegalArgumentException if the class given is not loaded by a local file classloader
     */
    public static Path getApplicationDirectory(Class applicationClass) {
        if (applicationClass == null) {
            throw new NullPointerException();
        }

        String className = applicationClass.getName();
        String resourceName = className.replace('.', '/') + ".class";
        ClassLoader classLoader = applicationClass.getClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        URL url = classLoader.getResource(resourceName);

        String szUrl = url.toString();
        if (szUrl.startsWith("jar:file:")) {
            try {
                szUrl = szUrl.substring("jar:".length(), szUrl.lastIndexOf('!'));
                URI uri = new URI(szUrl);
                return Paths.get(uri).getParent();
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }
        } else if (szUrl.startsWith("file:")) {
            try {
                szUrl = szUrl.substring(0, szUrl.length() - resourceName.length());
                URI uri = new URI(szUrl);
                return Paths.get(uri);
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }
        }
        throw new IllegalArgumentException("Not a local application classloader, can't get the local application directory.");
    }

    /**
     * Finds the location of a given class file on the file system.
     * Throws an IOException if the class cannot be found.
     * <br>
     * If the class is in an archive (JAR, ZIP), then the returned object
     * will point to the archive file.
     * <br>
     * If the class is in a directory, the base directory will be returned
     * with the package directory removed.
     * <br>
     * The <code>File.isDirectory()</code> method can be used to
     * determine which is the case.
     * <br>
     * @param c    a given class
     * @return    a File object
     * @throws IOException
     */
    public static Path getClassLocation(Class c) throws IOException, FileNotFoundException {
        if (c == null) {
            throw new NullPointerException();
        }

        String className = c.getName();
        String resourceName = className.replace('.', '/') + ".class";
        ClassLoader classLoader = c.getClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        URL url = classLoader.getResource(resourceName);

        String szUrl = url.toString();
        if (szUrl.startsWith("jar:file:")) {
            try {
                szUrl = szUrl.substring("jar:".length(), szUrl.lastIndexOf('!'));
                URI uri = new URI(szUrl);
                return Paths.get(uri);
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        } else if (szUrl.startsWith("file:")) {
            try {
                szUrl = szUrl.substring(0, szUrl.length() - resourceName.length());
                URI uri = new URI(szUrl);
                return Paths.get(uri);
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }
        throw new FileNotFoundException(szUrl);
    }

    public static boolean validateOrCreateDir(Path dir, String failureMessage) {
        //create dir, assume program location valid
        boolean success = false;
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            success = Files.isWritable(dir) && Files.isDirectory(dir);
        } catch (IOException | SecurityException ex) {
        } finally {
            if (!success) {
                IoUtils.log.info(failureMessage);
            }
            return success;
        }
    }

    /**
     * Deletes a file hierarchy.
     * If it is a directory, all sub-files and directories will be
     * deleted.
     * @param path
     * @return
     */
    public static boolean deleteFileOrDir(Path path) {
        try {
            Files.walkFileTree(path, new FileVisitors.DeleteTreeVisitor());
        } catch (IOException ex) {
            return false;
        }

        return true;
    }

    /**
     * This method kills this program and starts
     * the given one.
     */
    public static void restart(Class klass, String... args) {
        try {
            Runtime.getRuntime().addShutdownHook(new RestartProcessShutDownHook(klass, args));
            Runtime.getRuntime().exit(0);
        } catch (Exception ex) {
            IoUtils.log.log(Level.SEVERE, "Could not restart the program", ex);
        }
    }

    private static final class RestartProcessShutDownHook extends Thread {

        Class mainKlass;
        String[] arguments;

        public RestartProcessShutDownHook(Class mainKlass, String[] arguments) {
            super();
            this.mainKlass = mainKlass;
            this.arguments = arguments;
        }

        @Override
        public void run() {
            try {
                //allow the other shutdown hooks to start
                Thread.sleep(100);
                Thread[] tds = new Thread[Thread.activeCount()];
                int var = Thread.enumerate(tds);
                //wait for shutdown hooks made by IoUtils.addShutdownHook
                for (int i = 0; i < var; i++) {
                    Thread t = tds[i];
                    if (t != Thread.currentThread()
                            && t.isAlive()
                            && t.getName().equals("IoUtils.ShutdownHook")) {
                        t.join();
                    }
                }
            } catch (Exception ex) {
                IoUtils.log.log(Level.SEVERE, "Couldn't wait for other shutdown hooks", ex);
            }
            try {
                forkJava(mainKlass, arguments);
            } catch (IOException | InterruptedException ex) {
                IoUtils.log.log(Level.SEVERE, "Couldn't fork java", ex);
            }
        }
    }

    /**
     * Adds a shutdown hook that will be run when
     * the program terminates.
     *
     * The hooks will fail on unix when sent a SIGKILL (killl -9)
     * @param r
     */
    public static void addShutdownHook(final Runnable r) {
        Thread thread = new Thread(r, "IoUtils.ShutdownHook");
        thread.setPriority(Thread.MAX_PRIORITY);
        Runtime.getRuntime().addShutdownHook(thread);
    }

    /**
     * This method creates a new process that will run a new jvm
     * on the main of the given class, with the selected arguments.
     * It already flushes the output and inputstream of the forked jvm
     * into the current jvm.
     * The forked jvm uses the same java.exe and classpath as the current
     * one.
     * @param javaClass class with main method
     * @param args jvm properties.
     * @return Process, the jvm process, already started
     */
    public static Process forkJava(Class klass, String... args) throws IOException, InterruptedException {
        String javaExe = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        List<String> l = new ArrayList<>(4 + args.length);
        l.add(javaExe);
        l.add("-cp");
        l.add(classpath);
        l.addAll(Arrays.asList(args));
        l.add(klass.getCanonicalName());
        ProcessBuilder pb = new ProcessBuilder(l);
        pb.redirectErrorStream(true);
        final Process p = pb.start();
        //process builder stupidity (would need 2 threads if redirectErrorStream(false))
        Thread t = new Thread(new ProcessStreamConsumer(p), "ProcessBuilderInputStreamConsumer");
        t.setDaemon(true);
        t.start();
        return p;
    }

    /**
     * This method creates a new process that will run a new jvm
     * on the main of the given class, with the selected arguments.
     * It already flushes the output and inputstream of the forked jvm
     * into the current jvm.
     * The forked jvm uses the same java.exe and classpath as the current
     * one.
     * @param javaClass class with main method
     * @param args jvm properties.
     */
    public static void forkJavaAndWait(Class klass, String... args) throws IOException, InterruptedException {
        String javaExe = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        List<String> l = new ArrayList<>(4 + args.length);
        l.add(javaExe);
        l.add("-cp");
        l.add(classpath);
        l.addAll(Arrays.asList(args));
        l.add(klass.getCanonicalName());
        ProcessBuilder pb = new ProcessBuilder(l);
        pb.redirectErrorStream(true);
        final Process p = pb.start();
        //process builder stupidity (would need 2 threads if redirectErrorStream(false))
        Thread t = new Thread(new ProcessStreamConsumer(p), "ProcessBuilderInputStreamConsumer");
        t.setDaemon(true);
        t.start();
        int e = p.waitFor();
        if (e != 0) {
            p.destroy();
            throw new IllegalStateException("couldnt fork the java process, error code " + e);
        }
    }

    /**
     * This method downloads a file from the url into a local file if the file doesn't exist
     * @param localFile
     * @return the file
     * @throws java.io.IOException
     */
    public static Path downloadToLocalFile(URL localFile) throws IOException {
        Path r = IoUtils.toFile(localFile);
        if (r == null) {
            String name = IoUtils.getName(localFile);
            r = Paths.get(System.getProperty("java.io.tmpdir"), name);
            if (!Files.exists(r)) {
                writeInto(localFile.openStream(), true, new FileOutputStream(r.toFile()), true, 1024);
            }
        }
        return r;
    }

    /**
     * If possible transforms the url to a file
     * @param url given url
     * @return a file from the url, or null if not a file
     */
    public static Path toFile(final URL u) {
        try {
            return Paths.get(u.toURI());
        } catch (Exception ex) {
            return null;
        }
    }

    /*
     * Finds the file part of an URI external form string
     * @url the non-null uri to getIndex the name of.
     */
    public static String getName(final URI uri) {

        String realpath = uri.getPath();
        //avoid directories path seperator indexes.
        int index = realpath.lastIndexOf('/');

        if (index == (realpath.length() - 1)) {//directory
            int index2 = realpath.lastIndexOf('/', realpath.length() - 2);
            return realpath.substring(index2 + 1, index);
        } else {//file
            return realpath.substring(index + 1);
        }
    }

    /*
     * Finds the file part of an URL external form string
     * @url the non-null url to getIndex the name of.
     */
    public static String getName(final URL url) {

        String realpath;
        try {
            realpath = URLDecoder.decode(url.getPath(), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new AssertionError("UTF-8 is always a supported encoding.");
        }
        //avoid directories path seperator indexes.
        int index = realpath.lastIndexOf('/');

        if (index != -1 && index == (realpath.length() - 1)) {//directory
            int index2 = realpath.lastIndexOf('/', realpath.length() - 2);
            return realpath.substring(index2 + 1, index);
        } else {//file
            return realpath.substring(index + 1);
        }
    }

    /**
     * Sees if a url exists and can be read
     */
    public static boolean canRead(final URL url) {
        //as file first
        Path f = toFile(url);
        if (f != null) {
            return Files.isReadable(f);
        }

        InputStream s = null;
        try {
            s = url.openStream();
        } catch (Exception e) {
            return false;
        } finally {
            close(s);
        }
        return true;
    }

    /**
     * Sees if a uri exists and can be read
     */
    public static boolean canRead(final URI uri) {
        boolean exists = uri.isAbsolute();
        if (!exists) {
            return false;
        }

        InputStream s = null;
        try {
            if ("file:".equals(uri.getScheme())) {
                return Files.isReadable(Paths.get(uri));
            }

            s = uri.toURL().openStream();
        } catch (Exception e) {
            return false;
        } finally {
            close(s);
        }
        return true;
    }

    /**
     * Transforms the file into a URL
     * @param file given file
     * @return a url from the file. Not null.
     */
    public static URL toURL(final Path f) {
        try {
            return f.toUri().toURL();
        } catch (MalformedURLException ex) {
            //a file should always be well formed...
            throw new AssertionError("A file can always be transformed into a URL");
        }
    }

    /**
     * Transforms the file into a URL with a given
     * content type
     * @param file given file
     * @param the content type
     * @return a url from the file. Not null.
     */
    public static URL toURL(final Path f, final String contentType) {
        try {
            return new URL("", "", 0, "", new URLStreamHandler() {

                @Override
                protected URLConnection openConnection(URL u) throws IOException {
                    URLConnection urlConn = new URLConnection(u) {

                        @Override
                        public InputStream getInputStream() throws IOException {
                            return Files.newInputStream(f);
                        }

                        @Override
                        public String getContentType() {
                            return contentType;
                        }

                        @Override
                        public void connect() throws IOException {
                            super.connected = true;
                        }
                    };
                    urlConn.setDefaultUseCaches(false);
                    return urlConn;
                }
            });
        } catch (MalformedURLException ex) {
            IoUtils.log.log(Level.SEVERE, "Malformed URL", ex);
        }
        return null;
    }

    /**
     * Transforms a InputStream into a "fake" URL.
     * @param streamFactory InputStream factory not null.
     * @param contentType not null, not checked
     * for validness.
     * @param charset can be null, not checked for validness
     * @return a url from the InputStream. Not null.
     */
    public static URL toFakeURL(final Factory<InputStream, Void> streamFactory, final String contentType, final String charset) {
        if (streamFactory == null || contentType == null) {
            throw new IllegalArgumentException("null arguments");
        }

        URLStreamHandler streamHandler = new URLStreamHandler() {

            @Override
            protected URLConnection openConnection(final URL u) throws IOException {
                String content = contentType;
                if (charset != null && !charset.isEmpty()) {
                    content += ";" + charset;
                }
                URLConnection urlConn = new FakeStreamURLConnection(u, streamFactory, content);
                urlConn.setDefaultUseCaches(false);
                return urlConn;
            }
        };

        try {
            return new URL("", "", 0, "", streamHandler);
        } catch (MalformedURLException ex) {
            throw new AssertionError("This should be impossible", ex);
        }
    }

    private static class FakeStreamURLConnection extends URLConnection {

        private final Factory<InputStream, Void> streamFactory;
        private final String contentType;

        public FakeStreamURLConnection(URL url, Factory<InputStream, Void> streamFactory, String contentType) {
            super(url);
            this.streamFactory = streamFactory;
            this.contentType = contentType;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                return streamFactory.create(null);
            } catch (Exception ex) {
                throw new AssertionError("inputStream factory threw a exception creating a stream", ex);
            }
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public void connect() throws IOException {
            super.connected = true;
        }
    }
    private static Pattern charsetPattern = Pattern.compile("(?:charset|encoding)\\s*=(?:\\s*\n)*(?:[\"\'])??([^\\s;>\"\'\n]+)", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);

    /**
     * Probe the charset using a char matching
     * @param i the inputstream to probe. IF you want
     * to reset it you need to do it externally
     * @param limit use the limit so this method doesn't
     * overflow the reset buffer (maximum size read).
     * @return the charset found or null if none found
     */
    public static String probeCharset(InputStream i, int limit) {
        UniversalDetector detector = new UniversalDetector(null);
        byte[] buf = new byte[limit];
        int nread = -1, tread = 0, innerLimit = 1024;
        try {
            while ((tread + innerLimit) < limit
                    && (nread = i.read(buf, tread, innerLimit)) > 0
                    && !detector.isDone()) {
                tread += nread;
                detector.handleData(buf, 0, nread);
            }
            if (!detector.isDone() && nread > 0) {
                nread = i.read(buf, tread, limit - tread);
                if (nread > 0) {
                    detector.handleData(buf, 0, nread);
                }
            }
        } catch (IOException ioe) {
            IoUtils.log.log(Level.WARNING, "Error probing the charset close from InputStream", ioe);
        }
        detector.dataEnd();
        String charset = detector.getDetectedCharset();
        detector.reset();
        return charset;
    }

    /**
     * Use this to find a html charset sequence in a byte array
     * (translated to utf-8), no attempt is made to rewind or close
     * the inputstream (with a BufferedInputStream). That is the
     * responsibility of the caller.
     * @param i the byte source.
     * @param limit the read limit.
     * @return the charset found or null if none found
     * @throws IOException if there is a problem reading from the inputstream
     */
    public static String parseCharset(InputStream i, int limit) throws IOException {
        //find as utf-8
        Matcher mt;
        try {
            byte[] limitArr = new byte[limit];
            int read = i.read(limitArr, 0, limit);
            if (read == -1) {
                throw new IllegalArgumentException("Stream cant be read for some reason, current limit is " + limit);
            }

            mt = charsetPattern.matcher(new String(limitArr, 0, read, "UTF-8"));
            if (mt.find() && Charset.isSupported(mt.group(1))) {
                return mt.group(1);
            }
            //find as utf-16
            mt = charsetPattern.matcher(new String(limitArr, 0, read, "UTF-16"));
            if (mt.find() && Charset.isSupported(mt.group(1))) {
                return mt.group(1);
            }
        } catch (UnsupportedEncodingException ex) {
            throw new AssertionError("Impossible", ex);
        }
        //give up
        return null;
    }

    /**
     * For html files: a convience method that is a  combination of the
     * parseCharset and probeCharset, to try the hardest to find the valid
     * charset.
     *
     * You shouldn't use the mark/reset, since the method will do that for you.
     * You should also reuse the given stream if you want to read for something
     * else since the underlying stream of the bufferedstream is probably drained.
     *
     * If it fails anyway, it returns the defaultReturnCharset as default
     * @param i inputstream, preferably not buffered
     * (this method creates a buffer to use). Not closed by the method
     * @param limit the number of bytes in the stream to read to try to determine the charset
     * @param defaultReturnCharset returned instead of null if both methods fail
     * should be a valid charset.
     * @return a charset or defaultReturnCharset
     */
    public static String findHtmlCharset(BufferedInputStream i, int limit, String defaulReturnCharset) throws IOException {
        assert i.markSupported();
        i.mark(limit);
        //probecharset is more 'certain' than parseCharset - sometimes people put
        //encoding annotations on html files and save them as utf-8
        String charset = probeCharset(i, limit);
        if (charset == null) {
            i.reset();
            charset = parseCharset(i, limit);
        }
        i.reset();
        return charset == null ? defaulReturnCharset : charset;
    }

    /**
     * @return is html or related mimetype
     */
    public static boolean isHtmlMimeType(String mime) {
        if (mime == null) {
            return false;
        }

        mime = mime.toLowerCase(Locale.ENGLISH);
        return mime.endsWith("html") || mime.endsWith("xml");
    }

    /**
     * @return extension has a mimeType that is text/html or related
     */
    public static boolean isHtmlExtension(String extension) {
        String mime = URLConnection.getFileNameMap().getContentTypeFor(extension);
        //The default property file is fucked. There are types missing or inconsistent
        return isHtmlMimeType(mime) || extension.endsWith(".shtml") || extension.endsWith(".acgi") || extension.endsWith(".htmls") || extension.endsWith(".htx");
    }

    /**
     * @return is rtf or related mimetype
     */
    public static boolean isRtfMimeType(String mime) {
        if (mime == null) {
            return false;
        }

        mime = mime.toLowerCase(Locale.ENGLISH);
        return mime.endsWith("rtf") || mime.endsWith("richtext");
    }

    /**
     * @return extension has a mimeType that is text/rtf or related
     */
    public static boolean isRtfExtension(String extension) {
        String mime = URLConnection.getFileNameMap().getContentTypeFor(extension);
        //In linux rtf is not a mime type. Doh.
        return (extension != null && extension.endsWith("rtf")) || isRtfMimeType(mime);
    }

    /**
     * @return is text or related mimetype
     */
    public static boolean isPlainMimeType(String mime) {
        if (mime == null) {
            return false;
        }

        return mime.toLowerCase(Locale.ENGLISH).endsWith("plain");
    }

    /**
     * @return extension has a mimeType that is text/plain or related
     */
    public static boolean isPlainExtension(String extension) {
        String mime = URLConnection.getFileNameMap().getContentTypeFor(extension);
        return isPlainMimeType(mime);
    }

    /**
     * Close closeables. Use this in a finally clause.
     */
    public static void close(Closeable... closeables) {
        for (Closeable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ex) {
                    IoUtils.log.log(Level.WARNING, "Couldn't close Closeable", ex);
                }
            }
        }
    }

    /**
     * Sockets are not closeable... wtf
     */
    public static void close(Socket socket, Closeable... closeables) {
        for (Closeable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ex) {
                    IoUtils.log.log(Level.WARNING, "Couldn't close Closeable.", ex);
                }
            }
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ex) {
                IoUtils.log.log(Level.WARNING, "Couldn't close Socket.", ex);
            }
        }
    }

    /**
     * Creates a ThreadFactory
     * @param deamon
     */
    public static ThreadFactory createThreadFactory(final boolean deamon) {
        return new ThreadFactoryImpl(deamon, null);
    }

    /**
     * Creates a ThreadFactory
     * @param deamon
     */
    public static ThreadFactory createThreadFactory(final boolean deamon, final String name) {
        return new ThreadFactoryImpl(deamon, name);
    }

    /**
     * Given a input, pattern
     * and String factory
     * attempts to replace all the occurrences of matched pattern
     * with the result of the string factory.
     * Equivalent of a replaceAll(in,p,0,factory) call
     * @param in
     * @param p
     * @param factory - if an exception is thrown an error is raised
     * @returns a copy CharSequence with the selected group replaced.
     */
    public static CharSequence replaceAll(CharSequence in, Pattern p, Factory<String, Matcher> replacement) {
        return replaceAll(in, p, 0, replacement);
    }

    /**
     * Given a input, pattern, pattern group number to replace,
     * and String factory
     * attempts to replace all the occurrences of the given group
     * inside the matched pattern with the result of the string factory.
     * @param in
     * @param p
     * @param groupToReplace
     * @param factory - if an exception is thrown an error is raised
     * @returns a copy CharSequence with the selected group replaced.
     */
    public static CharSequence replaceAll(CharSequence in, Pattern p, int groupToReplace, Factory<String, Matcher> replacement) {
        try {
            StringBuilder out = new StringBuilder(in.length() + 16);
            Matcher m = p.matcher(in);
            int counter = 0;
            while (m.find(counter)) {
                out.append(in, counter, m.start(groupToReplace));
                out.append(replacement.create(m));
                counter = m.end(groupToReplace);
            }
            out.append(in, counter, in.length());
            return out;
        } catch (Exception ex) {
            throw new AssertionError("given factory shouldn't throw exception", ex);
        }
    }

    /**
     * Given a input, pattern, pattern group number to replace,
     * and String
     * attempts to replace all the occurrences of the given group
     * with the string.
     * @param in
     * @param p
     * @param replacement the copied StringBuilder without the given
     * group in the pattern.
     * @return a copy CharSequence with the selected group replaced.
     */
    public static CharSequence replaceAll(CharSequence in, Pattern p, int groupToReplace, String replacement) {
        StringBuilder out = new StringBuilder(in.length() + 16);
        Matcher m = p.matcher(in);
        int counter = 0;
        while (m.find(counter)) {
            out.append(in, counter, m.start(groupToReplace));
            out.append(replacement);
            counter = m.end(groupToReplace);
        }
        out.append(in, counter, in.length());
        return out;
    }

    /**
     * Reads inputstream r into array a until it can't read
     * no more or the array is full. Its the calle responsability
     * to call inputstream mark / reset if trying to use this
     * as a transient operation.
     * @param r
     * @param a
     * @param close close the reader r
     * @return the number of bytees read.
     * @throws java.io.IOException
     */
    public static int readInto(InputStream r, byte[] arr, boolean close) throws IOException {
        try {
            int increment = 0, index = 0;
            while (increment != -1 && index < arr.length) {
                increment = r.read(arr, index, arr.length - index);
                index += increment;
            }
            return index;
        } finally {
            if (close) {
                close(r);
            }
        }
    }

    /**
     * Reads inputstream i into a String with the UTF-8 charset
     * until the inputstream is finished (don't use with infinite streams).
     * @param inputStream to read into a string
     * @param close if true, close the inputstream
     * @return a string
     * @throws java.io.IOException if thrown on reading the stream
     * @throws java.lang.NullPointerException if the given inputstream is null
     */
    public static String toString(InputStream inputStream, boolean close) throws IOException {
        if (inputStream == null) {
            throw new NullPointerException("null inputstream");
        }
        String string;
        StringBuilder outputBuilder = new StringBuilder();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            while (null != (string = reader.readLine())) {
                outputBuilder.append(string).append('\n');
            }
        } finally {
            if (close) {
                close(inputStream);
            }
        }
        return outputBuilder.toString();
    }

    /**
     * Reads CharSequence a into writer w.
     * Don't use this with a buffered writer, since it also
     * buffers...
     * @param input
     * @param output
     * @param closeOutput
     * @throws java.io.IOException
     */
    public static void writeInto(CharSequence input, Writer output, boolean closeOutput) throws IOException {
        try {
            int start = 0, preEnd = input.length() - 1000;
            //This is done to avoid allocating a long array in
            //the toString. Better yet would be not to copy,
            //but Writer can't write CharSequences because they have no
            //*buffered* access. I'd prefer then to avoid the new in the
            //subsequence, wrapping it, but that is also not performant
            //because toString would have the same problem as Writer.
            while (start < preEnd) {
                CharSequence sub = input.subSequence(start, start + 1000);
                output.write(sub.toString());
                start += 1000;


            }
            output.write(input.subSequence(start, input.length()).toString());
            output.flush();


        } finally {
            if (closeOutput) {
                close(output);
            }
        }
    }

    /**
     * This function reads a input into a output and optionally
     * closes the streams.
     * Don't use this with a buffered output stream, since it also
     * buffers...
     *
     * @param input
     * @param closeInput
     * @param output
     * @param closeOutput
     * @param bufferSize
     * @throws IOException
     */
    public static void writeInto(final InputStream input, boolean closeInput, final OutputStream output, boolean closeOutput, final int bufferSize) throws IOException {
        final byte[] buffer = new byte[bufferSize];
        int n = 0;
        try {
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
            }
        } finally {
            if (closeInput) {
                close(input);
            }
            if (closeOutput) {
                close(output);
            }
        }
    }

    /**
     * This function reads a input into a output and optionally
     * closes the streams.
     * Don't use this with a buffered output stream, since it also
     * buffers...
     * You can pass a runnable to run on each iteration of
     * a read-write pass, continuation style. You don't control
     * the period of the writes though.
     *
     * @param input
     * @param closeInput
     * @param output
     * @param closeOutput
     * @param bufferSize
     * @param continuation code to run on each read-write
     * @throws IOException
     */
    public static void writeInto(final InputStream input, boolean closeInput, final OutputStream output, boolean closeOutput, final int bufferSize, final Runnable continuation) throws IOException {
        final byte[] buffer = new byte[bufferSize];
        int n = 0;
        try {
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
                continuation.run();
            }
        } finally {
            if (closeInput) {
                close(input);
            }
            if (closeOutput) {
                close(output);
            }
        }
    }

    static class ThreadFactoryImpl implements ThreadFactory {

        private final boolean deamon;
        private final String name;

        public ThreadFactoryImpl(final boolean deamon, final String name) {
            this.deamon = deamon;
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            if (name != null) {
                thread.setName(name);
            }
            thread.setDaemon(deamon);
            thread.setUncaughtExceptionHandler(new UncaughtExceptionHandlerImpl());
            return thread;
        }

        private static class UncaughtExceptionHandlerImpl implements UncaughtExceptionHandler {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                System.err.print("Exception in thread \"" + t.getName() + "\"");
                e.printStackTrace();
            }
        }
    }

    static class ProcessStreamConsumer implements Runnable {

        private final Process p;

        public ProcessStreamConsumer(Process p) {
            this.p = p;
        }

        @Override
        public void run() {
            String line;
            BufferedReader bufferedStderr = null;
            try {
                bufferedStderr = new BufferedReader(new InputStreamReader(p.getInputStream()));
                while ((line = bufferedStderr.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException ex) {
                IoUtils.log.log(Level.SEVERE, "Exception consuming process outputstream", ex);
            } finally {
                close(bufferedStderr);
            }
        }
    }
}
