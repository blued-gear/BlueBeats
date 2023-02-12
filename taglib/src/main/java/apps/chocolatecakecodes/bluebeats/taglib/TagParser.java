package apps.chocolatecakecodes.bluebeats.taglib;

import java.util.List;

public class TagParser{

    static{
        System.loadLibrary("tag");
        System.loadLibrary("taglib");
    }

    private final String filepath;
    private boolean parsed = false;
    private TagFields tagFields;// set by JNI
    private UserTags userTags;// set by JNI
    private List<Chapter> chapters;// set by JNI
    private List<Chapter> unmodifiableChapters = null;

    public TagParser(String filepath){
        this.filepath = filepath;
    }

    public void parse() throws ParseException{
        //TODO check file type (and maybe support other formats)
        parseMp3(filepath);
        parsed = true;
    }

    public TagFields getTagFields(){
        return tagFields;
    }

    public UserTags getUserTags(){
        return userTags;
    }

    public List<Chapter> getChapters(){
        if(unmodifiableChapters == null)
            unmodifiableChapters = Collections.unmodifiableList(chapters);
        return unmodifiableChapters;
    }

    public String getFilepath(){
        return filepath;
    }

    public boolean isParsed(){
        return parsed;
    }

    private native void parseMp3(String filepath) throws ParseException;
}
