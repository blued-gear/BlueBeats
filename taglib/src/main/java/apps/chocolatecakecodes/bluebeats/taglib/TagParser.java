package apps.chocolatecakecodes.bluebeats.taglib;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonAdapter(TagParser.Serializer.class)
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
            unmodifiableChapters = chapters != null ? Collections.unmodifiableList(chapters) : Collections.emptyList();// chapters can be null if no ID3v2 tag is present
        return unmodifiableChapters;
    }

    public String getFilepath(){
        return filepath;
    }

    public boolean isParsed(){
        return parsed;
    }

    private native void parseMp3(String filepath) throws ParseException;

    public static final class Serializer extends TypeAdapter<TagParser>{

        public static final Gson GSON;

        private static final int NUMBER_OF_FIELDS = 5;

        static{
            GSON = new GsonBuilder().serializeNulls().create();
        }

        @Override
        public void write(JsonWriter out, TagParser value) throws IOException{
            Objects.requireNonNull(value);

            out.beginObject();
            out.name("path").value(value.filepath);
            out.name("parsed").value(value.parsed);
            out.name("tagFields").jsonValue(GSON.toJson(value.tagFields));
            out.name("userTags").jsonValue(GSON.toJson(value.userTags));
            out.name("chapters").jsonValue(GSON.toJson(value.chapters));
            out.endObject();
        }

        @Override
        public TagParser read(JsonReader in) throws IOException{
            String path = null;
            boolean parsed = false;
            TagFields tagFields = null;
            UserTags userTags = null;
            List<Chapter> chapters = null;

            in.beginObject();
            for(int i = 0; i < NUMBER_OF_FIELDS; i++){
                String property = in.nextName();
                switch (property){
                    case "path":
                        path = in.nextString();
                        break;
                    case "parsed":
                        parsed = in.nextBoolean();
                        break;
                    case "tagFields":
                        tagFields = GSON.fromJson(in, TagFields.class);
                        break;
                    case "userTags":
                        userTags = GSON.fromJson(in, UserTags.class);
                        break;
                    case "chapters":
                        chapters = GSON.fromJson(in, List.class);
                        break;
                    default:
                        Log.w("TagParser/Serializer", "unknown property for TagParser: " + property);
                        i--;// do not count property
                        break;
                }
            }
            in.endObject();

            TagParser value = new TagParser(path);
            value.parsed = parsed;
            value.tagFields = tagFields;
            value.userTags = userTags;
            value.chapters = chapters;
            return value;
        }
    }
}
