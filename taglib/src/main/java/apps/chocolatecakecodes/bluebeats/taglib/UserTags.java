package apps.chocolatecakecodes.bluebeats.taglib;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * holds the list of tags (in terms of BlueBeat, not ID3) the user attached to a file
 */
@JsonAdapter(UserTags.Serializer.class)
public final class UserTags implements Cloneable{

    private String[] tags;// set by JNI
    private List<String> readonlyTags = null;

    public UserTags(String[] tags){
        this.tags = new String[tags.length];
        System.arraycopy(tags, 0, this.tags, 0, tags.length);
    }

    private UserTags(){}// called by JNI

    @NotNull
    public List<String> getTags(){
        if(readonlyTags == null){
            //noinspection Java9CollectionFactory
            readonlyTags = Collections.unmodifiableList(Arrays.asList(tags));
        }

        return readonlyTags;
    }

    @SuppressWarnings ("MethodDoesntCallSuperMethod")
    @NotNull
    @Override
    public UserTags clone(){
        return new UserTags(tags);
    }

    @Override
    public int hashCode(){
        return Objects.hash(tags, "TagLib::UserTags");
    }

    @Override
    public boolean equals(Object obj){
        if(!(obj instanceof UserTags))
            return false;

        UserTags other = (UserTags) obj;
        return Arrays.equals(other.tags, this.tags);
    }

    @Override
    public String toString(){
        return "UserTags: " + TagParser.Serializer.GSON.toJson(this);
    }

    public static final class Serializer extends TypeAdapter<UserTags>{

        private static final String[] EMPTY_STRING_ARR = new String[0];

        @Override
        public void write(JsonWriter out, UserTags value) throws IOException{
            Objects.requireNonNull(value);

            out.beginArray();
            for(String tag : value.tags)
                out.value(tag);
            out.endArray();
        }

        @Override
        public UserTags read(JsonReader in) throws IOException{
            if(in.peek() == JsonToken.NULL)
                return null;

            var tags = new ArrayList<String>();
            in.beginArray();
            while(in.hasNext())
                tags.add(in.nextString());
            in.endArray();

            UserTags value = new UserTags();
            value.tags = tags.toArray(EMPTY_STRING_ARR);
            return value;
        }
    }
}
