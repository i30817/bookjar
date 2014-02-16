package i3.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.logging.Level;

/**
 * This class reads objects from a file. It also needs to be close()-ed. It has
 * a static method providing the inverse operation, writing a set of objects to
 * a file. It shouldn't be read after writing. Create a new one.
 */
public final class ObjectsReader implements Closeable {

    private ObjectInputStream stream;
    private FileChannel innerChannel;
    private long fileSize;

    /**
     * This constructor ignores IOExceptions from the file to allow the read
     * objects to return defaults (except read() that throws IOException too if
     * it can read)
     *
     * @param file contains the objects or doesn't exist.
     * @throws NullPointerException if the file is null.
     */
    public ObjectsReader(Path file) {
        try {
            File rFile = file.toFile();
            fileSize = rFile.length();
            FileInputStream innerStream = new FileInputStream(rFile);
            innerChannel = innerStream.getChannel();
            stream = new ObjectInputStream(new BufferedInputStream(innerStream));
        } catch (IOException ex) {
            IoUtils.log.log(Level.WARNING, "Couldn''t init the ObjectReader, maybe there is no file {0} yet?", file);
        }
    }

    public boolean hasNext() {
        try {
            return innerChannel != null && stream != null && innerChannel.position() < fileSize;
        } catch (IOException ex) {
            IoUtils.log.log(Level.SEVERE, "Couldn't instantiate object from factory", ex);
            return false;
        }
    }

    /**
     * Use this method if you want to use the a constructor other than the
     * no-args one or don't want to use reflection. Can use laziness or not
     * according to the ClassCallable implementation.
     *
     * @param <T> return type
     * @param factory the factory of the return lazy or not, in case of read
     * error.
     * @throws NullPointerException if the factory given is null.
     * @throws IllegalStateException if the expected type is not on the stream
     * and the factory throws a exception.
     * @return a object of the return type
     */
    @SuppressWarnings({"unchecked", "cast"})
    public <T> T readOrLazyCreate(ClassCallable<T> factory) {
        Class<T> expectedClass = factory.getReturnClass();
        Object raw = readObjectQuietly();
        if (raw != null && expectedClass.isInstance(raw)) {
            return (T) raw;
        }
        try {
            return factory.call();
        } catch (Exception ex) {
            IoUtils.log.log(Level.SEVERE, "Couldn't instantiate object from factory", ex);
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Use this method if you want to use the no arg constructor and don't mind
     * using reflection.
     *
     * @param <T> return type
     * @param factory the class of the lazy return in case of read error.
     * @throws NullPointerException if the class given is null.
     * @throws IllegalStateException if the expected type is not on the stream
     * and there is no public no-args constructor.
     * @return a object of the return type
     */
    @SuppressWarnings({"unchecked", "cast"})
    public <T> T readOrLazyCreate(Class<T> factory) {
        Object raw = readObjectQuietly();
        if (raw != null && factory.isInstance(raw)) {
            return (T) raw;
        }
        //try with the class no args constructor instead.
        try {
            return (T) factory.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            IoUtils.log.log(Level.SEVERE, "Couldn't instantiate object from factory", ex);
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Use this method if you don't want laziness for the return value in case
     * of read error.
     *
     * @param <T> argument and return type
     * @param instance the value of the return in case of read error.
     * @return a serialized object of the return type. If it can't be found, or
     * the wrong type is found, return instance. If instance is null, a
     * assertion is thrown (because the type is used to prevent class cast
     * exception at the use-site, which can't be checked inside the method due
     * to erasure).
     */
    @SuppressWarnings({"unchecked", "cast"})
    public <T> T readOrReturn(T instance) {
        assert instance != null : "argument can't be null to prevent classcastexceptions";
        Object raw = readObjectQuietly();
        if (raw != null && instance.getClass().isInstance(raw)) {
            return (T) raw;
        } else if (raw != null) {
            IoUtils.log.severe("Saved object was of the wrong class " + raw.getClass());
        }
        return instance;
    }

    /**
     * Unchecked cast to T of the object read.
     *
     * @param <T>
     * @return
     * @throws IOException if it can't be read
     */
    @SuppressWarnings({"unchecked", "cast"})
    public <T> T read() throws IOException {
        if (stream == null) {
            throw new IOException("Can not read file");
        }
        try {
            return (T) stream.readObject();
        } catch (ClassNotFoundException ex) {
            throw new IOException(ex);
        }
    }

    private Object readObjectQuietly() {
        try {
            return (stream == null) ? null : stream.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            IoUtils.log.log(Level.SEVERE, "Couldn't read saved object", ex);
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (stream != null) {
            innerChannel = null;
            stream.close();
            stream = null;
        }
    }

    /**
     * If possible writes objects. Allows null objects
     */
    public static void writeObjects(Path objectLocation, Serializable... obj) throws IOException {
        FileOutputStream st = new FileOutputStream(objectLocation.toFile());
        try (ObjectOutputStream s = new ObjectOutputStream(new BufferedOutputStream(st, 40000))) {
            for (Serializable a : obj) {
                s.writeObject(a);
            }
            s.flush();
        }
    }

    public static abstract class ClassCallable<T> implements Callable<T> {

        Class<T> c;

        public ClassCallable(Class<T> given) {
            if (given == null) {
                throw new IllegalArgumentException("given class cannot be null");
            }
            c = given;
        }

        public Class<T> getReturnClass() {
            return c;
        }
    }
}
