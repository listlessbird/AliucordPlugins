package com.aliucord.plugins;

import android.content.Context;
import android.net.Uri;

import com.aliucord.Constants;
import com.aliucord.Http;
import com.aliucord.Logger;
import com.aliucord.PluginManager;
import com.aliucord.Utils;
import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.api.PatcherAPI;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.Hook;
import com.aliucord.patcher.PreHook;
import com.aliucord.utils.ReflectUtils;
import com.aliucord.wrappers.messages.AttachmentWrapper;
import com.discord.widgets.chat.MessageContent;
import com.discord.widgets.chat.MessageManager;
import com.discord.widgets.chat.input.ChatInputViewModel;
import com.lytefast.flexinput.model.Attachment;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import kotlin.jvm.functions.Function1;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@SuppressWarnings("unused")
@AliucordPlugin
public class AnonymizeAttachmentFilenames extends Plugin {
    private final Logger logger = new Logger("AnonymizeAttachmentFilenames");

    @Override
    public void start(Context ctx) throws NoSuchMethodError, NoSuchMethodException {
        // Patch the sendMessage method in ChatInputViewModel to anonymize attachment
        // filenames

        logger.info("Starting AnonAttachment patch");

        patcher.patch(ChatInputViewModel.class.getDeclaredMethod("sendMessage", Context.class, MessageManager.class,
                MessageContent.class, List.class, boolean.class, Function1.class), new PreHook(cf -> {
                    @SuppressWarnings("unchecked")
                    List<Attachment<?>> attachments = (List<Attachment<?>>) cf.args[3];

                    if (attachments != null && !attachments.isEmpty()) {
                        for (Attachment<?> attachment : attachments) {
                            try {
                                // Get the original filename
                                String originalFilename = attachment.getDisplayName();
                                if (originalFilename == null || originalFilename.isEmpty())
                                    continue;

                                // Extract file extension
                                String extension = "";
                                int lastDotIndex = originalFilename.lastIndexOf('.');
                                if (lastDotIndex > 0) {
                                    extension = originalFilename.substring(lastDotIndex);
                                }

                                // Generate a new UUID for the filename
                                String anonymizedFilename = UUID.randomUUID().toString() + extension;

                                // Log the anonymization
                                logger.info("Anonymizing attachment filename: " + originalFilename + " -> "
                                        + anonymizedFilename);

                                // Use reflection to set the new filename
                                ReflectUtils.setField(attachment, "fileName", anonymizedFilename);
                            } catch (Exception e) {
                                logger.error("Failed to anonymize attachment filename", e);
                            }
                        }
                    }
                }));

        // Patch HTTP requests to catch attachment uploads
        patcher.patch(Http.Request.class.getDeclaredMethod("executeWithBody", String.class), new PreHook(cf -> {
            try {
                Http.Request request = (Http.Request) cf.thisObject;
                String url = request.conn.getURL().toURI().toString();
                String body = (String) cf.args[0];

                // Check if this is an attachment upload request
                if (url != null && url.contains("/attachments") && body != null) {
                    try {
                        JSONObject jsonBody = new JSONObject(body);
                        if (jsonBody.has("filename")) {
                            String originalFilename = jsonBody.getString("filename");

                            // Extract file extension
                            String extension = "";
                            int lastDotIndex = originalFilename.lastIndexOf('.');
                            if (lastDotIndex > 0) {
                                extension = originalFilename.substring(lastDotIndex);
                            }

                            // Generate a new UUID for the filename
                            String anonymizedFilename = UUID.randomUUID().toString() + extension;

                            // Replace the filename in the JSON body
                            jsonBody.put("filename", anonymizedFilename);

                            // Log the anonymization
                            logger.info("Anonymizing API attachment filename: " + originalFilename + " -> "
                                    + anonymizedFilename);

                            // Set the modified body
                            cf.args[0] = jsonBody.toString();
                        }
                    } catch (JSONException e) {
                        logger.error("Failed to parse attachment JSON", e);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in HTTP request patch", e);
            }
        }));
    }

    @Override
    public void stop(Context context) {
        logger.info("Stopping AnonAttachment patch");
        patcher.unpatchAll();
    }
}
