package apps.chocolatecakecodes.bluebeats.taglib;

import android.util.Log;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

@JsonAdapter(Chapter.Serializer.class)
public final class Chapter implements Cloneable{

    private final long start, end;
    private final String name;

    public Chapter(long start, long end, String name){// called by JNI
        this.start = start;
        this.end = end;
        this.name = name;
    }

    public long getStart(){
        return start;
    }

    public long getEnd(){
        return end;
    }

    public String getName(){
        return name;
    }

    @SuppressWarnings ("MethodDoesntCallSuperMethod")
    @NotNull
    @Override
    public Chapter clone(){
        return new Chapter(start, end, name);
    }

    @Override
    public int hashCode(){
        return Objects.hash(start, end, name, "TagLib::CHAP");
    }

    @Override
    public boolean equals(Object obj){
        if(!(obj instanceof Chapter)) return false;

        Chapter other = (Chapter) obj;
        return other.start == this.start
                && other.end == this.end
                && Objects.equals(other.name, this.name);
    }

    @Override
    public String toString(){
        return String.format(Locale.ENGLISH, "Chapter: %s [%d - %d]", name, start, end);
    }

    public static final class Serializer extends TypeAdapter<Chapter>{

        private static final int NUMBER_OF_FIELDS = 3;

        @Override
        public void write(JsonWriter out, Chapter value) throws IOException{
            Objects.requireNonNull(value);

            out.beginObject();
            out.name("start").value(value.start);
            out.name("end").value(value.end);
            out.name("name").value(value.name);
            out.endObject();
        }

        @Override
        public Chapter read(JsonReader in) throws IOException{
            if(in.peek() == JsonToken.NULL)
                return null;

            long start = -1;
            long end = -1;
            String name = null;

            in.beginObject();
            for(int i = 0; i < NUMBER_OF_FIELDS; i++){
                String property = in.nextName();
                switch(property){
                    case "start":
                        start = in.nextLong();
                        break;
                    case "end":
                        end = in.nextLong();
                        break;
                    case "name":
                        name = in.nextString();
                        break;
                    default:
                        Log.w("TagParser/Serializer", "unknown property for Chapter: " + property);
                        i--;// do not count property
                        break;
                }
            }
            in.endObject();

            return new Chapter(start, end, name);
        }
    }
}
