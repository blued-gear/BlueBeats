package apps.chocolatecakecodes.bluebeats.taglib;

public final class Chapter{

    private final long start, end;
    private final String name;

    private Chapter(long start, long end, String name){// called by JNI
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
}
