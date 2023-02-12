package apps.chocolatecakecodes.bluebeats.taglib;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * holds the list of tags (in terms of BlueBeat, not ID3) the user attached to a file
 */
public final class UserTags{

    @SuppressWarnings ({"unused", "MismatchedReadAndWriteOfArray"})
    private String[] tags;// set by JNI
    private List<String> readonlyTags = null;

    private UserTags(){}// called by JNI

    @NotNull
    public List<String> getTags(){
        if(readonlyTags == null){
            //noinspection Java9CollectionFactory
            readonlyTags = Collections.unmodifiableList(Arrays.asList(tags));
        }

        return readonlyTags;
    }
}
