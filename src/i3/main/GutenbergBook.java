package i3.main;

import i3.gutenberg.GutenbergSearch;
import java.net.MalformedURLException;
import java.net.URL;
import i3.util.Iterators;
import i3.util.Strings;
import i3.util.Tuples;

public class GutenbergBook implements Book {

    private final String authors, contributors, title, metadata, languages, subjects;
    private String mimetype, extension;
    private long extent;
    private URL url;
    private final static StringBuilder normalizeString = new StringBuilder();

    public GutenbergBook(String authors, String contributors, String title, String languages, String subjects, String metadata) {
        this.languages = languages;
        this.authors = authors;
        this.contributors = contributors;
        this.title = title;
        this.subjects = subjects;
        this.metadata = metadata;
    }

    @Override
    public Tuples.T2<String[], String> authorsAndTitle() {
        String[] names = authors.split(GutenbergSearch.MULTIPLE_DATA_SEPARATOR);
        for (int i = 0; i < names.length; i++) {
            names[i] = normalizeName(names[i]);
        }
        return Tuples.createPair(names, getFirstTitle());
    }

    public String getDisplayLanguages(String seperator) {
        return languages.replace(GutenbergSearch.MULTIPLE_DATA_SEPARATOR, seperator);
    }

    public String getFirstTitle() {
        lazyInit();
        return title;
    }

    public String getFirstLanguage() {
        int i = languages.indexOf(GutenbergSearch.MULTIPLE_DATA_SEPARATOR);
        if (i != -1) {
            return languages.substring(0, i);
        }
        return languages;
    }

    public String getAuthors(String newNameSeperator) {
        if (authors.isEmpty()) {
            return "";
        }
        String oldNameSeperator = GutenbergSearch.MULTIPLE_DATA_SEPARATOR;
        return joinNames(authors, oldNameSeperator, newNameSeperator);
    }

    public String getContributors(String newNameSeperator) {
        if (contributors.isEmpty()) {
            return "";
        }
        String oldNameSeperator = GutenbergSearch.MULTIPLE_DATA_SEPARATOR;
        return joinNames(contributors, oldNameSeperator, newNameSeperator);
    }

    private String joinNames(String names, String nameSeperator, String newSeperator) {
        String[] s = names.split(nameSeperator);
        StringBuilder b = new StringBuilder();
        b.append(normalizeName(s[0]));
        for (String sub : Iterators.iterableFromIndex(s, 1)) {
            b.append(newSeperator).append(normalizeName(sub));
        }
        return b.toString();
    }

    public String getTitle() {
        return title;
    }

    public String getSubjects(String seperator) {
        return subjects.replace(GutenbergSearch.MULTIPLE_DATA_SEPARATOR, seperator);
    }

    //authors, first title and extension
    public String getFileName(String authorSeperator, String author_titleSeperator) {
        lazyInit();
        return getPartialName(authorSeperator, author_titleSeperator) + getExtension();
    }

    //just has the authors and first title
    public String getPartialName(String authorSeperator, String author_titleSeperator) {
        lazyInit();
        return getAuthors(authorSeperator) + author_titleSeperator + title;
    }

    public String getMetadata() {
        return metadata;
    }

    public long getExtent() {
        lazyInit();
        return extent;
    }

    public URL getURL() {
        lazyInit();
        return url;
    }

    public String getExtension() {
        lazyInit();
        return extension;
    }

    public String getMimeType() {
        lazyInit();
        return mimetype;
    }

    private void lazyInit() {
        if (url == null) {
            int i;
            String[] s = metadata.split(GutenbergSearch.MULTIPLE_DATA_SEPARATOR);

            if ((i = s[0].indexOf(';')) == -1) {
                mimetype = s[0];
            } else {
                mimetype = s[0].substring(0, i);
            }
            extent = Long.parseLong(s[1]);
            extension = Strings.subStringFromLast(s[2], '.');
            try {
                url = new URL("http://www.gutenberg.org/dirs/" + s[2]);

            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("corrupted index", e);
            }
        }
    }

    private static String normalizeName(String authorString) {
        //treat last part of the name specially, it can contain [roles] and/or dates.
        //if they do, they only contain them - this test and skips those
        String collaboratorRole = "";
        if (authorString.charAt(authorString.length() - 1) == ']') {
            //project gutenberg "markup" to distingish collaborators from
            //authors, seperate it here, since we are going to invert the names.

            //include space, but skip comma
            int collaboratorRoleIndex = authorString.lastIndexOf('[');
            assert (collaboratorRoleIndex != -1);
            collaboratorRole = authorString.substring(collaboratorRoleIndex - 1);
            authorString = authorString.substring(0, collaboratorRoleIndex - 2);
        }

        String possibleDate = Strings.subStringAfterLast(authorString, ',');
        for (int i = possibleDate.length() - 1; i >= 0; i--) {
            final char charAt = possibleDate.charAt(i);
            if (Character.isDigit(charAt)) {
                //a date, hopefully, so skip it
                return exchangeNames(Strings.subStringBeforeLast(authorString, ','), collaboratorRole);
            }
        }
        //no date to skip, but change the name anyway.
        return exchangeNames(authorString, collaboratorRole);
    }

    private static String exchangeNames(String authorString, String collaboratorRole) {
        normalizeString.setLength(0);
//        System.out.println(">>>>>> "+authorString);
//        System.out.println(">>>>>> "+collaboratorRole);
        exchangeNamesAux(authorString);
        normalizeString.append(collaboratorRole);
        return normalizeString.toString();
    }

    private static void exchangeNamesAux(String authorString) {
        int seperator = authorString.lastIndexOf(',');
        if (seperator == -1) {
            normalizeString.append(authorString);
            return;
        }
        normalizeString.append(authorString.substring(seperator + 2)).append(' ');
        exchangeNamesAux(authorString.substring(0, seperator));
    }
}
