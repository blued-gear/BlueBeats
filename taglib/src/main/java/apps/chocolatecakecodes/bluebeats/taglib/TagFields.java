package apps.chocolatecakecodes.bluebeats.taglib;

import android.util.Log;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Objects;

/**
 * holds all supported tag-values (in terms of ID3)
 */
@SuppressWarnings ("unused")
@JsonAdapter(TagFields.Serializer.class)
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
