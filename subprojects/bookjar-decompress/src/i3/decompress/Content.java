package i3.decompress;

import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import i3.util.Strings;

/**
 * These are the contents that the Compressed archive
 * can be ordered by (on Selector).
 * They depend on the (unmodifiable) Extractor interface.
 * If you need another implementation you can implement
 * FileComparator yourself.
 * @author i30817
 */
public enum Content implements FileComparator, FileExaminer {

    Name {

        @Override
        public Object examine(Extractor extractor, Object header) {
            return extractor.getFileName(header);
        }

        public Comparator getComparator(final Extractor extractor) {
            return new Comparator() {

                public int compare(Object o1, Object o2) {
                    //natural string order thank you very much Mr Stephen Kelvin Friedrich.
                    return Strings.compareNatural(extractor.getFileName(o1), extractor.getFileName(o2));
                }
            };
        }
    }, IgnoreCaseName {

        @Override
        public Object examine(Extractor extractor, Object header) {
            return extractor.getFileName(header).toLowerCase(Locale.ENGLISH);
        }

        public Comparator getComparator(final Extractor extractor) {
            return new Comparator() {

                public int compare(Object o1, Object o2) {
                    //natural string order thank you very much Mr Stephen Kelvin Friedrich.
                    return Strings.compareNatural(extractor.getFileName(o1).toLowerCase(Locale.ENGLISH), extractor.getFileName(o2).toLowerCase(Locale.ENGLISH));
                }
            };
        }
    }, NameSize {

        @Override
        public Object examine(Extractor extractor, Object header) {
            return extractor.getFileName(header).length();
        }

        public Comparator getComparator(final Extractor extractor) {
            return new Comparator() {

                public int compare(Object o1, Object o2) {
                    String s1 = extractor.getFileName(o1);
                    String s2 = extractor.getFileName(o2);
                    return s1.length() - s2.length();
                }
            };
        }
    }, FileSize {

        @Override
        public Object examine(Extractor extractor, Object header) {
            return extractor.getFileSize(header);
        }

        public Comparator getComparator(final Extractor extractor) {
            return new Comparator() {

                public int compare(Object o1, Object o2) {
                    Long a = extractor.getFileSize(o1);
                    Long b = extractor.getFileSize(o2);
                    return (a == null || b == null) ? 0 : a.compareTo(b);
                }
            };
        }
    }, CompressedFileSize {

        @Override
        public Object examine(Extractor extractor, Object header) {
            return extractor.getCompressedFileSize(header);
        }

        public Comparator getComparator(final Extractor extractor) {
            return new Comparator() {

                public int compare(Object o1, Object o2) {
                    Long a = extractor.getCompressedFileSize(o1);
                    Long b = extractor.getCompressedFileSize(o2);
                    return (a == null || b == null) ? 0 : a.compareTo(b);
                }
            };
        }
    }, CRC32 {

        @Override
        public Object examine(Extractor extractor, Object header) {
            return extractor.getCRC32(header);
        }

        public Comparator getComparator(final Extractor extractor) {
            return new Comparator() {

                public int compare(Object o1, Object o2) {
                    Long a = extractor.getCRC32(o1);
                    Long b = extractor.getCRC32(o2);
                    return (a == null || b == null) ? 0 : a.compareTo(b);
                }
            };
        }
    }, ModificationDate {

        @Override
        public Object examine(Extractor extractor, Object header) {
            return extractor.getModificationDate(header);
        }

        public Comparator getComparator(final Extractor extractor) {
            return new Comparator() {

                public int compare(Object o1, Object o2) {
                    Date a = extractor.getModificationDate(o1);
                    Date b = extractor.getModificationDate(o2);
                    return (a == null || b == null) ? 0 : a.compareTo(b);
                }
            };
        }
    };
}
