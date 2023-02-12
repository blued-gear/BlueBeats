#include <jni.h>
#include <string>

#include "mpegfile.h"
#include "tag.h"
#include "id3v2tag.h"
#include "chapterframe.h"
#include "textidentificationframe.h"

using namespace TagLib::MPEG;
using TagLib::Tag;
using TagLib::String;

//region forward declarations
namespace {
    /// struct to cache all handles to java classes, fields and methods
    struct JavaIDs_t{

        explicit JavaIDs_t(JNIEnv* jni) : jni(jni){
            c_ParseException = jni->FindClass("apps/chocolatecakecodes/bluebeats/taglib/ParseException");

            c_TagParser = jni->FindClass("apps/chocolatecakecodes/bluebeats/taglib/TagParser");
            f_TagParser_TagFields = jni->GetFieldID(c_TagParser, "tagFields", "Lapps/chocolatecakecodes/bluebeats/taglib/TagFields;");
            f_TagParser_UserTags = jni->GetFieldID(c_TagParser, "userTags", "Lapps/chocolatecakecodes/bluebeats/taglib/UserTags;");
            f_TagParser_Chapters = jni->GetFieldID(c_TagParser, "chapters", "Ljava/util/List;");

            c_TagFields = jni->FindClass("apps/chocolatecakecodes/bluebeats/taglib/TagFields");
            m_TagFields_Constructor = jni->GetMethodID(c_TagFields, "<init>", "()V");
            f_TagFields_Artist = jni->GetFieldID(c_TagFields, "artist", "Ljava/lang/String;");
            f_TagFields_Length = jni->GetFieldID(c_TagFields, "length", "J");
            f_TagFields_Title = jni->GetFieldID(c_TagFields, "title", "Ljava/lang/String;");

            c_Chapter = jni->FindClass("apps/chocolatecakecodes/bluebeats/taglib/Chapter");
            m_Chapter_Constructor  = jni->GetMethodID(c_Chapter, "<init>","(JJLjava/lang/String;)V");

            c_ArrayList = jni->FindClass("java/util/ArrayList");
            m_ArrayList_Constructor_Size = jni->GetMethodID(c_ArrayList, "<init>", "(I)V");
            m_ArrayList_Add = jni->GetMethodID(c_ArrayList, "add", "(Ljava/lang/Object;)Z");
        }

        JNIEnv* jni;

        jclass c_ParseException = nullptr;

        jclass c_TagParser = nullptr;
        jfieldID f_TagParser_TagFields = nullptr;
        jfieldID f_TagParser_UserTags = nullptr;
        jfieldID f_TagParser_Chapters = nullptr;

        jclass c_TagFields = nullptr;
        jmethodID m_TagFields_Constructor = nullptr;
        jfieldID f_TagFields_Artist = nullptr;
        jfieldID f_TagFields_Length = nullptr;
        jfieldID f_TagFields_Title = nullptr;

        jclass c_Chapter = nullptr;
        jmethodID m_Chapter_Constructor = nullptr;

        jclass c_ArrayList = nullptr;
        jmethodID m_ArrayList_Constructor_Size = nullptr;
        jmethodID m_ArrayList_Add = nullptr;
    };
    typedef struct JavaIDs_t JavaIDs;

    namespace BlueBeats {
        // aliases for better type-checking
        typedef jobject TagFields;
        typedef jobject UserTags;
        typedef jobject Chapters;

        void throwParseException(const JavaIDs& jid, const char *msg);

        TagFields readTags(const JavaIDs& jid, const File &file);

        void readID3Tags(const JavaIDs& jid, const Tag *tag, const Properties* audioProps, TagFields dest);

        Chapters readChapters(const JavaIDs& jid, File& file, bool& err);
    }
}
//endregion

// region JNI methods
extern "C" JNIEXPORT void JNICALL
Java_apps_chocolatecakecodes_bluebeats_taglib_TagParser_parseMp3(
        JNIEnv* env,
        jobject thisRef,
        jstring filepath) {
    JavaIDs jni(env);

    // create and parse file; copy java-string to cpp-string
    const char* pathChars = env->GetStringUTFChars(filepath, nullptr);
    std::string path(pathChars);
    env->ReleaseStringUTFChars(filepath, pathChars);
    File file(path.c_str(), true, Properties::ReadStyle::Average);

    // ensure the file was opened successful
    if(!file.isOpen()){
        BlueBeats::throwParseException(jni, "file could not be opened");
        return;
    }

    BlueBeats::TagFields tagFields = BlueBeats::readTags(jni, file);
    if(tagFields == nullptr) return;// an error occurred and so an exception was thrown
    env->SetObjectField(thisRef, jni.f_TagParser_TagFields, tagFields);

    bool readChaptersErr = false;
    BlueBeats::Chapters chapters = BlueBeats::readChapters(jni, file, readChaptersErr);
    if(readChaptersErr) return;// an error occurred and so an exception was thrown
    env->SetObjectField(thisRef, jni.f_TagParser_Chapters, chapters);
}
//endregion

//region private methods
void BlueBeats::throwParseException(const JavaIDs& jid, const char* msg) {
    if(jid.jni->ExceptionCheck() == JNI_TRUE) return;// an exception is already being thrown
    jid.jni->ThrowNew(jid.c_ParseException, msg);
}

BlueBeats::TagFields BlueBeats::readTags(const JavaIDs& jid, const File &file) {
    // create new TagFields class
    TagFields fields = jid.jni->NewObject(jid.c_TagFields, jid.m_TagFields_Constructor);
    if(fields == nullptr){
        throwParseException(jid, "unable to alloc object");
        return nullptr;
    }

    Properties* audioProperties = file.audioProperties();
    Tag* id3Tag = file.tag();
    if(id3Tag != nullptr)
        readID3Tags(jid, id3Tag, audioProperties, fields);
    //TODO optionally read APE tags

    return fields;
}

void BlueBeats::readID3Tags(const JavaIDs& jid, const Tag *tag, const Properties* audioProps, TagFields dest) {
    assert(tag != nullptr);
    assert(dest != nullptr);

    String artist = tag->artist();
    if(!artist.isNull()){
        const char* artistStr = artist.toCString(true);
        jstring artistJStr = jid.jni->NewStringUTF(artistStr);
        jid.jni->SetObjectField(dest, jid.f_TagFields_Artist, artistJStr);
    }

    String title = tag->title();
    if(!title.isNull()){
        const char* titleStr = title.toCString(true);
        jstring titleJStr = jid.jni->NewStringUTF(titleStr);
        jid.jni->SetObjectField(dest, jid.f_TagFields_Title, titleJStr);
    }

    if(audioProps != nullptr){
        jlong length = audioProps->lengthInMilliseconds();
        jid.jni->SetLongField(dest, jid.f_TagFields_Length, length);
    }
}

BlueBeats::Chapters BlueBeats::readChapters(const JavaIDs &jid, File &file, bool &err) {
    // parsing of chapters only supported for ID3v2
    if(!file.hasID3v2Tag())
        return nullptr;

    const TagLib::ID3v2::Tag* tag = file.ID3v2Tag(false);
    assert(tag != nullptr);
    const TagLib::ID3v2::FrameList& chapterFrames = tag->frameList("CHAP");

    // create java list obj
    jint chapterCount = chapterFrames.size();
    Chapters list = jid.jni->NewObject(jid.c_ArrayList, jid.m_ArrayList_Constructor_Size, chapterCount);
    if(list == nullptr){
        err = true;
        throwParseException(jid, "unable to alloc object");
        return nullptr;
    }

    // parse chapters
    for(TagLib::ID3v2::Frame* frame : chapterFrames){
        auto* chapterFrame = dynamic_cast<TagLib::ID3v2::ChapterFrame*>(frame);
        assert(chapterFrame != nullptr);

        jlong start = chapterFrame->startTime();
        jlong end = chapterFrame->endTime();

        // search title (the first valid text frame)
        jstring title = nullptr;
        const TagLib::ID3v2::FrameList& subFrames = chapterFrame->embeddedFrameList();
        for(const TagLib::ID3v2::Frame* subFrame : subFrames){
            if(subFrame->frameID() == "TIT2" || subFrame->frameID() == "TIT3"){
                auto textFrame = dynamic_cast<const TagLib::ID3v2::TextIdentificationFrame*>(subFrame);
                if(textFrame != nullptr){
                    String textStr = textFrame->toString();
                    title = jid.jni->NewStringUTF(textStr.toCString(true));
                    break;
                }
            }
        }
        if(title == nullptr)// no title was found -> fallback value
            title = jid.jni->NewStringUTF("");
        if(title == nullptr){// error in NewStringUTF()
            err = true;
            throwParseException(jid, "unable to alloc string");
            return nullptr;
        }

        // create and add chapter obj
        jobject chap = jid.jni->NewObject(jid.c_Chapter, jid.m_Chapter_Constructor, start, end, title);
        if(chap == nullptr){
            err = true;
            throwParseException(jid, "unable to alloc object");
            return nullptr;
        }
        jid.jni->CallBooleanMethod(list, jid.m_ArrayList_Add, chap);
    }

    return list;
}
//endregion