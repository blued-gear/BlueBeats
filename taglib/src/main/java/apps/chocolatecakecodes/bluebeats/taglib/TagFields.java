package apps.chocolatecakecodes.bluebeats.taglib;

import android.util.Log;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

/**
 * holds all supported tag-values (in terms of ID3)
 */
@SuppressWarnings ("unused")
@JsonAdapter(TagFields.Serializer.class)
public final class TagFields implements Cloneable{

    // all fields will be set by JNI
    private String title;
    private String artist;
    private long length;
    //TODO add more

    public TagFields(){}// called by NJI

    public String getTitle(){
        return title;
    }

    public String getArtist(){
        return artist;
    }

    public long getLength(){
        return length;
    }

    @SuppressWarnings ("MethodDoesntCallSuperMethod")
    @NotNull
    @Override
    public TagFields clone(){
        var clone = new TagFields();
        clone.title = this.title;
        clone.artist = this.artist;
        clone.length = this.length;
        return clone;
    }

    @Override
    public int hashCode(){
        return Objects.hash(title, artist, length, "TagLib::TAGS");
    }

    @Override
    public boolean equals(Object obj){
        if(!(obj instanceof TagFields))
            return false;

        TagFields other = (TagFields) obj;
        return Objects.equals(other.title, this.title)
                && Objects.equals(other.artist, this.artist)
                && other.length == this.length;
    }

    @Override
    public String toString(){
        return "TagFields: " + TagParser.Serializer.GSON.toJson(this);
    }

    public static final class Serializer extends TypeAdapter<TagFields>{

        private static final int NUMBER_OF_FIELDS = 3;

        @Override
        public void write(JsonWriter out, TagFields value) throws IOException{
            Objects.requireNonNull(value);

            out.beginObject();
            out.name("title").value(value.title);
            out.name("artist").value(value.artist);
            out.name("length").value(value.length);
            out.endObject();
        }

        @Override
        public TagFields read(JsonReader in) throws IOException{
            if(in.peek() == JsonToken.NULL)
                return null;

            TagFields value = new TagFields();

            in.beginObject();
            for(int i = 0; i < NUMBER_OF_FIELDS; i++){
                final String property = in.nextName();
                switch(property){
                    case "title":
                        value.title = in.nextString();
                        break;
                    case "artist":
                        value.artist = in.nextString();
                        break;
                    case "length":
                        value.length = in.nextLong();
                        break;
                    default:
                        Log.w("TagParser/Serializer", "unknown property for TagFields: " + property);
                        i--;// do not count property
                        break;
                }
            }
            in.endObject();

            return value;
        }
    }
}
