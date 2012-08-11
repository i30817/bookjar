package i3.decompress;

/**
 *
 * @author fc30817
 */
public interface FileExaminer<T> {

    T examine(Extractor extractor, Object header);

}
