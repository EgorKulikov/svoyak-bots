package net.egork.telegram

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author egor@egork.net
 */
data class User(val id: Int, val firstName: String, val lastName: String?, val username: String?) {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is User) {
            return false
        }
        return id == other.id
    }
}

enum class ChatType {
    @JsonProperty("private") PRIVATE,
    @JsonProperty("group") GROUP,
    @JsonProperty("supergroup") SUPERGROUP,
    @JsonProperty("channel") CHANNEL;
}

data class Chat(val id: Long, val type: ChatType, val title: String?, val username: String?, val firstName: String?,
                val lastName: String?)

data class Message(val messageId: Int, val from: User?, val date: Int, val chat: Chat, val forwardFrom: User?,
                   val forwardFromChat: Chat?, val forwardDate: Int?, val replyToMessage: Message?, val editDate: Int?,
                   val text: String?, val entities: Array<MessageEntity>?, val audio: Audio?, val document: Document?,
                   val photo: Array<PhotoSize>?, val sticker: Sticker?, val video: Video?, val voice: Voice?,
                   val caption: String?, val contact: Contact?, val location: Location?, val venue: Venue?,
                   val newChatMember: User?, val leftChatMemeber: User?, val newChatTitle: String?,
                   val newChatPhoto: Array<PhotoSize>?, val deleteChatPhoto: Boolean?, val groupChatCreated: Boolean?,
                   val supergroupChatCreated: Boolean?, val channelChatCreated: Boolean?, val migrateChatId: Long?,
                   val migrateFromChatId: Long?, val pinnedMessage: Message?)

enum class MessageEntityType {
    @JsonProperty("mention") MENTION,
    @JsonProperty("hashtag") HASHTAG,
    @JsonProperty("bot_command") BOT_COMMAND,
    @JsonProperty("url") URL,
    @JsonProperty("email") EMAIL,
    @JsonProperty("bold") BOLD,
    @JsonProperty("italic") ITALIC,
    @JsonProperty("code") CODE,
    @JsonProperty("pre") PRE,
    @JsonProperty("text_link") TEXT_LINK,
    @JsonProperty("text_mention") TEXT_MENTION;
}

data class MessageEntity(val type: MessageEntityType, val offset: Int, val length: Int, val url: String?,
                         val user: User?)

data class PhotoSize(val fileId: String, val width: Int, val height: Int, val fileSize: Int?)

data class Audio(val fileId: String, val duration: Int, val performer: String?, val title: String?,
                 val mimeType: String?, val fileSize: Int?)

data class Document(val fileId: String, val thumb: PhotoSize?, val fileName: String?, val mimeType: String?,
                    val fileSize: Int?)

data class Sticker(val fileId: String, val width: Int, val height: Int, val thumb: PhotoSize?, val emoji: String?,
                   val fileSize: Int?)

data class Video(val fileId: String, val width: Int, val height: Int, val duration: Int, val thumb: PhotoSize?,
                 val mimeType: String?, val fileSize: Int?)

data class Voice(val fileId: String, val duration: Int, val mimeType: String?, val fileSize: Int?)

data class Contact(val phoneNumber: String, val firstName: String, val lastName: String?, val userId: Int?)

data class Location(val longitude: Float, val latitude: Float)

data class Venue(val location: Location, val title: String, val address: String, val foursquareId: String?)

data class UserProfilePhotos(val totalCount: Int, val photos: Array<Array<PhotoSize>>)

data class File(val fileId: String, val fileSize: Int?, val filePath: String?)

data class ReplyKeyboardMarkup(val keyboard: Array<Array<String>>?, val resizeKeyboard: Boolean?,
                               val oneTimeKeyboard: Boolean?, val selective: Boolean?)

data class ReplyKeyboardHide(val hideKeyboard: Boolean, val selective: Boolean?)

data class InlineKeyboardMarkup(val inlineKeyboard: Array<Array<InlineKeyboardButton>>)

data class InlineKeyboardButton(val text: String, val url: String?, val callbackData: String?,
                                val switchInlineQuery: String?)

data class CallbackQuery(val id: String, val from: User, val message: Message?, val inlineMessageId: String?,
                         val data: String)

data class ForceReply(val forceReply: Boolean, val selective: Boolean?)

enum class Status {
    @JsonProperty("creator") CREATOR,
    @JsonProperty("administartor") ADMINISTRATOR,
    @JsonProperty("member") MEMBER,
    @JsonProperty("left") LEFT,
    @JsonProperty("kicked") KICKED;
}

data class ChatMember(val user: User, val status: Status)

data class Update(val updateId: Int, val message: Message?, val editedMessage: Message?, val inineQuery: InlineQuery?,
                  val chosenInlineResult: ChosenInlineResult?, val callbackQuery: CallbackQuery?)

data class InlineQuery(val id: String, val from: User, val location: Location?, val query: String, val offset: String)

data class ChosenInlineResult(val resultId: String, val from: User, val location: Location?,
                              val inlineMessageId: String?, val query: String)

data class GetUpdatesArgs(val offset: Int?, val limit: Int?, val timeout: Int?)

data class GetFileArgs(val fileId: String)

enum class ParseMode {
    @JsonProperty("Markdown") MARKDOWN,
    @JsonProperty("HTML") HTML;
}

data class SendMessageArgs(val chatId: Long, val text: String, val parseMode: ParseMode?,
                           val replyMarkup: ReplyKeyboardMarkup?)

data class KickArgs(val chatId: Long, val userId: Int)
