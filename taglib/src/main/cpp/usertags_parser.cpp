#include <id3v2synchdata.h>
#include <tzlib.h>
#include "usertags_parser.h"

using namespace TagLib;
using namespace TagLib::ID3v2;
using namespace BlueBeats::Tag;

//region frame_factory
static MyFrameFactory INSTANCE;

MyFrameFactory* MyFrameFactory::instance() {
    return &INSTANCE;
}

MyFrameFactory::MyFrameFactory() {}
MyFrameFactory::~MyFrameFactory() noexcept {}

Frame* MyFrameFactory::createFrame(const ByteVector &data, FactoryExposedFrame::Header *frameHeader, const Header *tagHeader) const{
    if(frameHeader->frameID() == "PRIV"){
        UsertagsFrame* frame = tryParseUsertagsFrame(data, frameHeader);
        if(frame != nullptr)
            return frame;
    }

    return FrameFactory::createFrame(data, frameHeader, tagHeader);
}

UsertagsFrame* MyFrameFactory::tryParseUsertagsFrame(const ByteVector &data, FactoryExposedFrame::Header *frameHeader){
    static ByteVector usertagsIdentifier = UsertagsFrame::OWNER_STRING.data(String::Type::Latin1);

    ByteVector contentData = frameContentData(data, frameHeader);
    if(contentData.startsWith(usertagsIdentifier)) {
        auto frame = new UsertagsFrame(data);//TODO the data might be compressed
        frame->parse(data);
        return frame;
    }

    return nullptr;
}

TagLib::ByteVector MyFrameFactory::frameContentData(const ByteVector& data, FactoryExposedFrame::Header* frameHeader) {
    unsigned int headerSize = FactoryExposedFrame::Header::size(frameHeader->version());
    unsigned int frameDataOffset = headerSize;
    unsigned int frameDataLength = frameHeader->frameSize();

    if(frameHeader->compression() || frameHeader->dataLengthIndicator()) {
        frameDataLength = SynchData::toUInt(data.mid(headerSize, 4));
        frameDataOffset += 4;
    }

    if(zlib::isAvailable() && frameHeader->compression() && !frameHeader->encryption()) {
        if(data.size() <= frameDataOffset) {
            throw std::ios_base::failure("Compressed frame doesn't have enough data to decode");
        }

        const ByteVector outData = zlib::decompress(data.mid(frameDataOffset));

        if(!outData.isEmpty() && frameDataLength != outData.size()) {
            throw std::ios_base::failure("frameDataLength does not match the data length returned by zlib");
        }

        return outData;
    }

    return data.mid(frameDataOffset, frameDataLength);
}
//endregion

//region usertags_frame
const String UsertagsFrame::OWNER_STRING("BlueBeats::Usertags::v1 - chocolatecakecodes@disroot.org");

UsertagsFrame::UsertagsFrame() : PrivateFrame() {
    this->setOwner(OWNER_STRING);
}

UsertagsFrame::UsertagsFrame(const TagLib::ByteVector &data) : PrivateFrame(data) {
    this->setOwner(OWNER_STRING);
}

UsertagsFrame::~UsertagsFrame() noexcept {}

StringList UsertagsFrame::getTags() const {
    return this->tags;
}
void UsertagsFrame::setTags(const TagLib::StringList& tags) {
    this->tags = tags;
    renderUserTags();
}

void UsertagsFrame::parseFields(const ByteVector &data){
    PrivateFrame::parseFields(data);
    parseUserTags();
}

ByteVector UsertagsFrame::renderFields() const {
    return PrivateFrame::renderFields();
}

void UsertagsFrame::parseUserTags() {
    const ByteVector& data = this->data();
    int i = 0;
    int j = 0;
    while((i = data.find('\0', i)) != -1){
        String ut(data.data() + j, String::Type::UTF8);
        this->tags.append(ut);
        i++;
        j = i;
    }
}

void UsertagsFrame::renderUserTags(){
    ByteVector data, tagData;

    for(const String& tag : this->tags){
        tagData = tag.data(String::Type::UTF8);
        data.append(tagData).append('\0');
    }

    this->setData(data);
}
//endregion
