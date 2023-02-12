#include <jni.h>
#include <string>

#include "mpegfile.h"
#include "tag.h"

using namespace TagLib::MPEG;
using TagLib::Tag;
using TagLib::String;

//region forward declarations
namespace {
    namespace BlueBeats {
        // aliases for better type-checking
        typedef jobject TagFields;
        typedef jobject UserTags;

        void throwParseException(JNIEnv *jni, const char *msg);

        TagFields readTags(JNIEnv *jni, const File &file);

        void readID3Tags(JNIEnv *jni, const Tag *tag, const Properties* audioProps, TagFields dest);
    }

    /// struct to cache all handles to java classes, fields and methods
    struct JavaIDs_t{
        jclass c_ParseException = nullptr;

        jclass c_TagParser = nullptr;
        jfieldID f_TagParser_TagFields = nullptr;
        jfieldID f_TagParser_UserTags = nullptr;

        jclass c_TagFields = nullptr;
        jmethodID m_TagFields_Constructor = nullptr;
        jfieldID f_TagFields_Artist = nullptr;
        jfieldID f_TagFields_Length = nullptr;
        jfieldID f_TagFields_Title = nullptr;

        void init(JNIEnv* jni){
            c_ParseException = jni->FindClass("apps/chocolatecakecodes/bluebeats/taglib/ParseException");

            c_TagParser = jni->FindClass("apps/chocolatecakecodes/bluebeats/taglib/TagParser");
            f_TagParser_TagFields = jni->GetFieldID(c_TagParser, "tagFields", "Lapps/chocolatecakecodes/bluebeats/taglib/TagFields;");
            f_TagParser_UserTags = jni->GetFieldID(c_TagParser, "userTags", "Lapps/chocolatecakecodes/bluebeats/taglib/UserTags;");

            c_TagFields = jni->FindClass("apps/chocolatecakecodes/bluebeats/taglib/TagFields");
            m_TagFields_Constructor = jni->GetMethodID(c_TagFields, "<init>", "()V");
            f_TagFields_Artist = jni->GetFieldID(c_TagFields, "artist", "Ljava/lang/String;");
            f_TagFields_Length = jni->GetFieldID(c_TagFields, "length", "J");
            f_TagFields_Title = jni->GetFieldID(c_TagFields, "title", "Ljava/lang/String;");
        }
    };
    struct JavaIDs_t JavaIDs;
}
//endregion

// region JNI methods
extern "C" JNIEXPORT void JNICALL
Java_apps_chocolatecakecodes_bluebeats_taglib_TagParser_parseMp3(
        JNIEnv* env,
        jobject thisRef,
        jstring filepath) {
    JavaIDs.init(env);

    // create and parse file; copy java-string to cpp-string
    const char* pathChars = env->GetStringUTFChars(filepath, nullptr);
    std::string path(pathChars);
    env->ReleaseStringUTFChars(filepath, pathChars);
    File file(path.c_str(), true, Properties::ReadStyle::Average);

    // ensure the file was opened successful
    if(!file.isOpen()){
        BlueBeats::throwParseException(env, "file could not be opened");
        return;
    }

    BlueBeats::TagFields tagFields = BlueBeats::readTags(env, file);
    if(tagFields == nullptr) return;// an error occurred and so an exception was thrown
    env->SetObjectField(thisRef, JavaIDs.f_TagParser_TagFields, tagFields);
}
//endregion

//region private methods
void BlueBeats::throwParseException(JNIEnv* jni, const char* msg) {
    jni->ThrowNew(JavaIDs.c_ParseException, msg);
}

BlueBeats::TagFields BlueBeats::readTags(JNIEnv *jni, const File &file) {
    // create new TagFields class
    TagFields fields = jni->NewObject(JavaIDs.c_TagFields, JavaIDs.m_TagFields_Constructor);
    if(fields == nullptr){
        throwParseException(jni, "unable to alloc object");
        return nullptr;
    }

    Properties* audioProperties = file.audioProperties();
    Tag* id3Tag = file.tag();
    if(id3Tag != nullptr)
        readID3Tags(jni, id3Tag, audioProperties, fields);
    //TODO optionally read APE tags

    return fields;
}

void BlueBeats::readID3Tags(JNIEnv *jni, const Tag *tag, const Properties* audioProps, TagFields dest) {
    assert(jni != nullptr);
    assert(tag != nullptr);
    assert(dest != nullptr);

    String artist = tag->artist();
    if(!artist.isNull()){
        const char* artistStr = artist.toCString(true);
        jstring artistJStr = jni->NewStringUTF(artistStr);
        jni->SetObjectField(dest, JavaIDs.f_TagFields_Artist, artistJStr);
    }

    String title = tag->title();
    if(!title.isNull()){
        const char* titleStr = title.toCString(true);
        jstring titleJStr = jni->NewStringUTF(titleStr);
        jni->SetObjectField(dest, JavaIDs.f_TagFields_Title, titleJStr);
    }

    if(audioProps != nullptr){
        jlong length = audioProps->lengthInMilliseconds();
        jni->SetLongField(dest, JavaIDs.f_TagFields_Length, length);
    }
}
//endregion