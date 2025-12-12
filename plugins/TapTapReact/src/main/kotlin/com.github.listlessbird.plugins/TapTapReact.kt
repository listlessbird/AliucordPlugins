package com.github.listlessbird.plugins

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.*
import com.aliucord.utils.RxUtils.subscribe
import com.aliucord.Utils
import com.discord.models.message.Message
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterEventsHandler
import com.discord.stores.StoreStream
import com.discord.utilities.rest.RestAPI
import com.aliucord.patcher.component1
import com.aliucord.patcher.component2
import java.net.URLEncoder


@Suppress("unused")
@AliucordPlugin
class TapTapReact : Plugin() {

    private var lastTapAt = 0L
    private var lastMessageId = 0L
    private var lastChannelId = 0L


    private val doubleTapWindowMs = 250L

	@OptIn(ExperimentalStdlibApi::class)
    override fun start(ctx: Context) {
        logger.debug("Started the TapTapReact plugin")
        settingsTab = SettingsTab(
            TapTapReactSettings::class.java,
            SettingsTab.Type.BOTTOM_SHEET
        ).withArgs(settings)

        patcher.before<WidgetChatListAdapterEventsHandler>(
            "onMessageClicked",
            Message::class.java,
            Boolean::class.javaPrimitiveType!!
        ) { (param, msg: Message)->

            logger.debug("Message clicked: ${msg.id}")

            if (msg.isEphemeralMessage || msg.isLocal || msg.isFailed || msg.isLoading) return@before

            val now = System.currentTimeMillis()
            val channelId = msg.channelId
            val msgId = msg.id

            val isDoubleTap = channelId == lastChannelId &&
                    msgId == lastMessageId &&
                    (now - lastTapAt) <= doubleTapWindowMs

            if (!isDoubleTap) {
                lastTapAt = now
                lastChannelId = channelId
                lastMessageId = msgId
                return@before
            }
            logger.debug("Double tap detected!")
            param.result = null

            lastTapAt = 0L
            lastChannelId = 0L
            lastMessageId = 0L

            try {
                val emoji = settings.getString("emoji", "\uD83D\uDE02")
                logger.debug("Raw emoji: $emoji")

                if (emoji == null || emoji.isEmpty()) {
                    return@before
                }

                val emojiObj = StoreStream.getEmojis().unicodeEmojiSurrogateMap[emoji]
                if (emojiObj == null) {
                    logger.debug("Could not find emoji object for: $emoji")
                    val encodedEmoji = URLEncoder.encode(emoji, "UTF-8")
                    logger.debug("Using fallback, encoded: $encodedEmoji")
                    Utils.threadPool.execute {
                        RestAPI.getApi().addReaction(channelId, msgId, encodedEmoji).subscribe {
                            logger.debug("Reaction added via fallback!")
                        }
                    }
                    return@before
                }

                val reactionKey = emojiObj.reactionKey
                logger.debug("Emoji object found, reaction key: $reactionKey")

                val encodedKey = URLEncoder.encode(reactionKey, "UTF-8")
                logger.debug("Encoded reaction key: $encodedKey")
                logger.debug("Channel: $channelId, Message: $msgId")

                Utils.threadPool.execute {
                    RestAPI.getApi().addReaction(channelId, msgId, encodedKey).subscribe {
                        logger.debug("Reaction added successfully!")
                    }
                }
            } catch (e: Throwable) {
                logger.error("EXCEPTION: ${e.javaClass.name}: ${e.message}", e)
                logger.debug("Channel: $channelId, Message: $msgId")
            }
        }
	}

	override fun stop(context: Context) {
		patcher.unpatchAll()
		commands.unregisterAll()
	}
}