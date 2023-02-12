package apps.chocolatecakecodes.bluebeats.taglib;

public class TagParser{

    static{
        System.loadLibrary("tag");
        System.loadLibrary("taglib");
    }

    private final String filepath;
    private TagFields tagFields;// set by JNI
    private UserTags userTags;// set by JNI

    public TagParser(String filepath){
        this.filepath = filepath;
    }

    public void parse() throws ParseException{
        //TODO check file type (and maybe support other formats)
        parseMp3(filepath);
    }

    public TagFields getTagFields(){
        return tagFields;
    }

    public UserTags getUserTags(){
        return userTags;
    }

    public String getFilepath(){
        return filepath;
    }

    private native void parseMp3(String filepath) throws ParseException;
}
