package com.aliucord.plugins;
import android.content.Context;
import com.aliucord.Logger;
import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.PreHook;
import com.aliucord.utils.ReflectUtils;
import com.discord.widgets.chat.MessageContent;
import com.discord.widgets.chat.MessageManager;
import com.discord.widgets.chat.input.ChatInputViewModel;
import com.lytefast.flexinput.model.Attachment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.List;
import java.util.UUID;

import kotlin.jvm.functions.Function1;


@SuppressWarnings("unused")
@AliucordPlugin
public class AnonymizeAttachmentFilenames extends Plugin {
    private final Logger logger = new Logger("AnonymizeAttachmentFilenames");

    @Override
    public void start(Context ctx) throws NoSuchMethodError, NoSuchMethodException {
        logger.info("Starting AnonAttachment patch");

        patcher.patch(ChatInputViewModel.class.getDeclaredMethod("sendMessage", Context.class, MessageManager.class,
                MessageContent.class, List.class, boolean.class, Function1.class), new PreHook(cf -> {
                    @SuppressWarnings("unchecked")
                    List<Attachment<?>> attachments = (List<Attachment<?>>) cf.args[3];

                    if (attachments != null && !attachments.isEmpty()) {
                        for (Attachment<?> attachment : attachments) {
                            try {
                                String originalFilename = attachment.getDisplayName();
                                if (originalFilename == null || originalFilename.isEmpty())
                                    continue;

                                String extension = "";
                                int lastDotIndex = originalFilename.lastIndexOf('.');
                                if (lastDotIndex > 0) {
                                    extension = originalFilename.substring(lastDotIndex);
                                }

                                String anonymizedFilename = UUID.randomUUID().toString() + extension;

                                logger.info("Anonymizing attachment filename: " + originalFilename + " -> "
                                        + anonymizedFilename + " (Class: " + attachment.getClass().getName() + ")");

                                try {
                                    Method setDisplayNameMethod = attachment.getClass()
                                            .getDeclaredMethod("setDisplayName", String.class);
                                    setDisplayNameMethod.setAccessible(true);
                                    setDisplayNameMethod.invoke(attachment, anonymizedFilename);
                                    logger.info("Used setter method to set display name");
                                } catch (NoSuchMethodException e) {
                                    logger.info("No setter method, trying direct field access");
                                    try {
                                        ReflectUtils.setField(attachment, "displayName", anonymizedFilename);
                                        logger.info("Set displayName field directly");
                                    } catch (NoSuchFieldException nsfe) {
                                        logger.info("Field not found in direct class, trying superclass");
                                        Field field = findFieldInClassHierarchy(attachment.getClass(), "displayName");
                                        if (field != null) {
                                            field.setAccessible(true);
                                            field.set(attachment, anonymizedFilename);
                                            logger.info("Set displayName field via hierarchy search");
                                        } else {
                                            throw new NoSuchFieldException(
                                                    "Could not find displayName field in class hierarchy");
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                logger.error("Failed to anonymize attachment filename", e);
                                logger.info(e.toString());
                            }
                        }
                    }
                }));

    }

    private Field findFieldInClassHierarchy(Class<?> startClass, String fieldName) {
        Class<?> currentClass = startClass;
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                return field;
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    @Override
    public void stop(Context context) {
        logger.info("Stopping AnonAttachment patch");
        patcher.unpatchAll();
    }
}
