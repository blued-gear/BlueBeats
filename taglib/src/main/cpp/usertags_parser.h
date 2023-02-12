#ifndef USERTAGS_PARSER_H
#define USERTAGS_PARSER_H

#include "tstringlist.h"
#include "id3v2framefactory.h"
#include "privateframe.h"

namespace BlueBeats::Tag {
    class UsertagsFrame : public TagLib::ID3v2::PrivateFrame {

        friend class MyFrameFactory;

    private:
        TagLib::StringList tags;

    public:

        static const TagLib::String OWNER_STRING;

        UsertagsFrame();

        explicit UsertagsFrame(const TagLib::ByteVector &data);

        ~UsertagsFrame() noexcept;

        TagLib::StringList getTags() const;

        void setTags(const TagLib::StringList &);

    protected:
        virtual void parseFields(const TagLib::ByteVector &data);

        virtual TagLib::ByteVector renderFields() const;

    private:
        void parseUserTags();

        void renderUserTags();
    };

    class MyFrameFactory : public TagLib::ID3v2::FrameFactory {

    private:
        // necessary to access Frame::Header
        class FactoryExposedFrame : public TagLib::ID3v2::Frame {
            friend class MyFrameFactory;
        };

    public:
        static MyFrameFactory *instance();

        MyFrameFactory();

        ~MyFrameFactory() noexcept override;

        TagLib::ID3v2::Frame *createFrame(const TagLib::ByteVector &data, FactoryExposedFrame::Header *frameHeader,
                                          const TagLib::ID3v2::Header *tagHeader) const override;

    private:
        static UsertagsFrame* tryParseUsertagsFrame(const TagLib::ByteVector& data, FactoryExposedFrame::Header* frameHeader);
        static TagLib::ByteVector frameContentData(const TagLib::ByteVector& data, FactoryExposedFrame::Header* frameHeader);
    };
}

#endif
