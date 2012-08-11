package i3.main;

import java.io.Serializable;

public class BookMetadata implements Serializable{

    public final String subjects;
    public final String description;

    public BookMetadata(String subjects, String description) {
        this.subjects = subjects;
        this.description = description;
    }

}
