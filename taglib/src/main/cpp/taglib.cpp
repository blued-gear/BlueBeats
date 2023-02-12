#include <jni.h>
#include <string>
#include <cassert>

#include "mpegfile.h"
#include "tag.h"
#include "id3v2tag.h"
#include "chapterframe.h"
#include "textidentificationframe.h"

using namespace TagLib::MPEG;
using TagLib::Tag;
using TagLib::String;
using TagLib::StringList;
using TagLib::List;

constexpr const char* USERTAGS_IDENTIFIER = "_BlueBeats::Usertags::v1";
constexpr const char* USERTAGS_TAG_SEPARATOR = " ; ";

//region forward declarations
namespace {
    /// struct to cache all handles to java classes, fields and methods
    struct JavaIDs_t{

        [[maybe_unused]]
        explicit JavaIDs_t(JNIEnv* jni) : jni(jni) {
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
            f_TagFields_Genre = jni->GetFieldID(c_TagFields, "genre", "Ljava/lang/String;");

            c_Chapter = jni->FindClass("apps/chocolatecakecodes/bluebeats/taglib/Chapter");
            m_Chapter_Constructor  = jni->GetMethodID(c_Chapter, "<init>","(JJLjava/lang/String;)V");

            c_Usertags = jni->FindClass("apps/chocolatecakecodes/bluebeats/taglib/UserTags");
            m_Usertags_Constructor = jni->GetMethodID(c_Usertags, "<init>", "([Ljava/lang/String;)V");

            c_ArrayList = jni->FindClass("java/util/ArrayList");
            m_ArrayList_Constructor_Size = jni->GetMethodID(c_ArrayList, "<init>", "(I)V");
            m_ArrayList_Add = jni->GetMethodID(c_ArrayList, "add", "(Ljava/lang/Object;)Z");

            c_String = jni->FindClass("java/lang/String");
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
        jfieldID f_TagFields_Genre = nullptr;

        jclass c_Chapter = nullptr;
        jmethodID m_Chapter_Constructor = nullptr;

        jclass c_Usertags = nullptr;
        jmethodID m_Usertags_Constructor = nullptr;

        jclass c_ArrayList = nullptr;
        jmethodID m_ArrayList_Constructor_Size = nullptr;
        jmethodID m_ArrayList_Add = nullptr;

        jclass c_String = nullptr;
    };
    typedef struct JavaIDs_t JavaIDs;

    namespace BlueBeats_Parser {
        // aliases for better type-checking
        typedef jobject TagFields;
        typedef jobject UserTags;
        typedef jobject Chapters;

        void throwParseException(const JavaIDs& jid, const char *msg);

        TagFields readTags(const JavaIDs& jid, const File &file);

        void readID3Tags(const JavaIDs& jid, const Tag *tag, const Properties* audioProps, TagFields dest);

        Chapters readChapters(const JavaIDs& jid, File& file, bool& err);

        UserTags readUsertags(const JavaIDs& jid, File& file, bool& err);
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

    try {
        // create and parse file; copy java-string to cpp-string
        const char *pathChars = env->GetStringUTFChars(filepath, nullptr);
        std::string path(pathChars);
        env->ReleaseStringUTFChars(filepath, pathChars);
        File file(path.c_str(),true, Properties::ReadStyle::Average);

        // ensure the file was opened successful
        if (!file.isOpen()) {
            BlueBeats_Parser::throwParseException(jni, "file could not be opened");
            return;
        }

        BlueBeats_Parser::TagFields tagFields = BlueBeats_Parser::readTags(jni, file);
        if (tagFields == nullptr) return;// an error occurred and so an exception was thrown
        env->SetObjectField(thisRef, jni.f_TagParser_TagFields, tagFields);

        bool readChaptersErr = false;
        BlueBeats_Parser::Chapters chapters = BlueBeats_Parser::readChapters(jni, file,
                                                                             readChaptersErr);
        if (readChaptersErr) return;// an error occurred and so an exception was thrown
        env->SetObjectField(thisRef, jni.f_TagParser_Chapters, chapters);

        bool readUsertagsErr = false;
        BlueBeats_Parser::UserTags usertags = BlueBeats_Parser::readUsertags(jni, file,
                                                                             readUsertagsErr);
        if (readUsertagsErr) return;// an error occurred and so an exception was thrown
        env->SetObjectField(thisRef, jni.f_TagParser_UserTags, usertags);
    }catch(std::exception& e) {
        std::string msg("c++ exception occurred: ");
        msg += e.what();
        BlueBeats_Parser::throwParseException(jni, msg.c_str());
    }
}
//endregion

//region private methods
void BlueBeats_Parser::throwParseException(const JavaIDs& jid, const char* msg) {
    if(jid.jni->ExceptionCheck() == JNI_TRUE) return;// an exception is already being thrown
    jid.jni->ThrowNew(jid.c_ParseException, msg);
}

BlueBeats_Parser::TagFields BlueBeats_Parser::readTags(const JavaIDs& jid, const File &file) {
    // create new TagFields class
    TagFields fields = jid.jni->NewObject(jid.c_TagFields, jid.m_TagFields_Constructor);
    if(fields == nullptr) {
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

void BlueBeats_Parser::readID3Tags(const JavaIDs& jid, const Tag *tag, const Properties* audioProps, TagFields dest) {
    assert(tag != nullptr);
    assert(dest != nullptr);

    String artist = tag->artist();
    if(!artist.isNull()) {
        const char* artistStr = artist.toCString(true);
        jstring artistJStr = jid.jni->NewStringUTF(artistStr);
        jid.jni->SetObjectField(dest, jid.f_TagFields_Artist, artistJStr);
    }

    String title = tag->title();
    if(!title.isNull()) {
        const char* titleStr = title.toCString(true);
        jstring titleJStr = jid.jni->NewStringUTF(titleStr);
        jid.jni->SetObjectField(dest, jid.f_TagFields_Title, titleJStr);
    }

    String genre = tag->genre();
    if(!genre.isNull()) {
        const char* genreStr = genre.toCString(true);
        jstring genreJStr = jid.jni->NewStringUTF(genreStr);
        jid.jni->SetObjectField(dest, jid.f_TagFields_Genre, genreJStr);
    }

    if(audioProps != nullptr) {
        jlong length = audioProps->lengthInMilliseconds();
        jid.jni->SetLongField(dest, jid.f_TagFields_Length, length);
    }
}

BlueBeats_Parser::Chapters BlueBeats_Parser::readChapters(const JavaIDs &jid, File &file, bool &err) {
    // parsing of chapters only is supported for ID3v2
    if(!file.hasID3v2Tag())
        return nullptr;

    const TagLib::ID3v2::Tag* tag = file.ID3v2Tag(false);
    assert(tag != nullptr);
    const TagLib::ID3v2::FrameList& chapterFrames = tag->frameList("CHAP");

    // create java list obj
    jint chapterCount = chapterFrames.size();
    Chapters list = jid.jni->NewObject(jid.c_ArrayList, jid.m_ArrayList_Constructor_Size, chapterCount);
    if(list == nullptr) {
        err = true;
        throwParseException(jid, "unable to alloc object");
        return nullptr;
    }

    // parse chapters
    for(TagLib::ID3v2::Frame* frame : chapterFrames) {
        auto* chapterFrame = dynamic_cast<TagLib::ID3v2::ChapterFrame*>(frame);
        assert(chapterFrame != nullptr);

        jlong start = chapterFrame->startTime();
        jlong end = chapterFrame->endTime();

        // offsets instead of times are not supported
        if((start == 0 && chapterFrame->startOffset() != 0xFFFFFFFF)
            || (end == 0 && chapterFrame->endOffset() != 0xFFFFFFFF))
            continue;

        // search title (the first valid text frame)
        jstring title;
        const List<TagLib::ID3v2::Frame*>* candidate = &chapterFrame->embeddedFrameList("TIT2");
        if(candidate->isEmpty())
            candidate = &chapterFrame->embeddedFrameList("TIT3");
        if(!candidate->isEmpty()) {
            auto textFrame = dynamic_cast<const TagLib::ID3v2::TextIdentificationFrame*>((*candidate)[0]);
            String textStr = textFrame->toString();
            title = jid.jni->NewStringUTF(textStr.toCString(true));
        } else {
            // no title was found -> fallback value
            title = jid.jni->NewStringUTF("");
        }

        if(title == nullptr) {// error in NewStringUTF()
            err = true;
            throwParseException(jid, "unable to alloc string");
            return nullptr;
        }

        // create and add chapter obj
        jobject chap = jid.jni->NewObject(jid.c_Chapter, jid.m_Chapter_Constructor, start, end, title);
        if(chap == nullptr) {
            err = true;
            throwParseException(jid, "unable to alloc object");
            return nullptr;
        }
        jid.jni->CallBooleanMethod(list, jid.m_ArrayList_Add, chap);
    }

    return list;
}

BlueBeats_Parser::UserTags BlueBeats_Parser::readUsertags(const JavaIDs& jid, File& file, bool& err) {
    // parsing of usertags only is supported for ID3v2
    if(!file.hasID3v2Tag())
        return nullptr;

    const TagLib::ID3v2::Tag* tag = file.ID3v2Tag(false);
    assert(tag != nullptr);

    const TagLib::ID3v2::FrameList& userTexts = tag->frameList("TXXX");

    // search usertags-entry
    const String* usertagsString = nullptr;
    for(const auto& f : userTexts) {
        auto userText = dynamic_cast<const TagLib::ID3v2::UserTextIdentificationFrame*>(f);
        assert(userText != nullptr);

        if(userText->description() != USERTAGS_IDENTIFIER)
            continue;

        const auto& entries = userText->fieldList();
        if(entries.size() < 2)
            continue;

        usertagsString = &entries[1];
        break;
    }


    if(usertagsString == nullptr || usertagsString->isEmpty())// no usertags
        return nullptr;

    StringList utEntries = usertagsString->split(USERTAGS_TAG_SEPARATOR);

    jobjectArray tagsArr = jid.jni->NewObjectArray(utEntries.size(), jid.c_String, nullptr);
    if(tagsArr == nullptr) {
        err = true;
        throwParseException(jid, "unable to alloc object");
        return nullptr;
    }

    for(jsize i = 0; i < utEntries.size(); i++) {
        std::string entryStr = utEntries[i].to8Bit(true);
        jobject entryJStr = jid.jni->NewStringUTF(entryStr.c_str());

        if(entryJStr == nullptr) {
            err = true;
            throwParseException(jid, "unable to alloc object");
            return nullptr;
        }

        jid.jni->SetObjectArrayElement(tagsArr, i, entryJStr);
    }

    jobject ret = jid.jni->NewObject(jid.c_Usertags, jid.m_Usertags_Constructor, tagsArr);
    if(ret == nullptr) {
        err = true;
        throwParseException(jid, "unable to alloc object");
        return nullptr;
    }
    return ret;
}
//endregion
