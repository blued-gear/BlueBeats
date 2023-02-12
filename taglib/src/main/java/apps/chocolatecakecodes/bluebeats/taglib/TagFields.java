package apps.chocolatecakecodes.bluebeats.taglib;

/**
 * holds all supported tag-values (in terms of ID3)
 */
@SuppressWarnings ("unused")
public final class TagFields{

    // all fields will be set by JNI
    private String title;
    private String artist;
    private long length;
    //TODO add more

    private TagFields(){}// called by NJI

    public String getTitle(){
        return title;
    }

    public String getArtist(){
        return artist;
    }

    public long getLength(){
        return length;
    }
}
