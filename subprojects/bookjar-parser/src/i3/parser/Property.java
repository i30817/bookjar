package i3.parser;

/**
 * Some format metadata used by the
 * loading engine. Different metadata
 * owners might load/override different values
 * for this keys. That's natural (you don't
 * need to set all values in the same place)
 * @author fc30817
 */
public enum Property {
    /**
     * Reformat read files
     * @return boolean or null.
     */
    REFORMAT,
    /**
     * In formats that allow it,
     * the hyperlink color.
     * @return Color or null.
     */
    HYPERLINK_COLOR,
    /**
     * Not used yet
     * @return Color or null.
     */
    VISITED_HYPERLINK_COLOR,
    /**
     * Extracted from compressed file
     * @return file name as a String
     * if extracted from a container file,
     * or null.
     */
    EXTRACTED_FILENAME,
    /**
     * Listener called before and after the parsing
     * @return parsers.ParserListener or null.
     */
    PARSER_LISTENER,
    /**
     * Extracted from open library
     * @return main.BookMetadata or null
     */
    OPENLIBRARY_BOOK_METADATA;
}
