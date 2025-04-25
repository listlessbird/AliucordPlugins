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
import com.aliucord.utils.ReflectUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import kotlin.jvm.functions.Function1;

@SuppressWarnings("unused")
@AliucordPlugin
public class AnonymizeAttachmentFilenames extends Plugin {
    private static final Logger logger = new Logger("AnonymizeAttachmentFilenames");

    @Override
    public void start(Context context) throws Throwable {
        patcher.patch("com.discord.stores.StoreUpload$uploadFiles$1", // Class name
                "invoke", // Method name
                new Class<?>[] { Object.class }, // Argument types (adjust if needed)
                new Hook(param -> {
                    try {
                        // The argument might be a List<Uploadable>, Attachment, or similar depending on
                        // Discord version.
                        // We need to inspect param.args[0] or relevant fields in param.thisObject
                        Object arg = param.args[0];
                        logger.info("Hook triggered. Argument type: %s", arg.getClass().getName());

                        // This part requires inspecting the actual objects at runtime or decompiled
                        // source.
                        // Let's assume the argument holds or gives access to the file URI/path/name.
                        // Example: If arg is a list of objects having a 'uri' or 'filename' field.
                        if (arg instanceof List) {
                            List<?> items = (List<?>) arg;
                            for (Object item : items) {
                                try {
                                    // Attempt to find and modify filename/URI fields
                                    // Common fields might be 'filename', 'name', 'uri', 'path'
                                    Uri uri = (Uri) ReflectUtils.getField(item, "uri"); // Example field name
                                    String oldFilename = (String) ReflectUtils.getField(item, "filename"); // Example
                                                                                                           // field name

                                    if (oldFilename != null) {
                                        String extension = "";
                                        int dotIndex = oldFilename.lastIndexOf('.');
                                        if (dotIndex > 0 && dotIndex < oldFilename.length() - 1) {
                                            extension = oldFilename.substring(dotIndex); // Keep extension (e.g.,
                                                                                         // ".png")
                                        }
                                        String newFilename = UUID.randomUUID().toString() + extension;

                                        logger.info("Anonymizing filename: '%s' -> '%s'", oldFilename, newFilename);
                                        ReflectUtils.setField(item, "filename", newFilename);

                                        // If the name is derived from the URI path, we might need to adjust that too,
                                        // but that's more complex and potentially risky. Let's start with the filename
                                        // field.

                                    } else {
                                        logger.info("Could not find 'filename' field in item: %s",
                                                item.getClass().getName());
                                    }
                                } catch (NoSuchFieldException | IllegalAccessException e) {
                                    logger.error(
                                            "Failed to access/modify fields for item: " + item.getClass().getName(), e);
                                }
                            }
                        } else {
                            // Handle cases where the argument is not a list or has a different structure
                            logger.warn("Argument is not a List: %s. Need to inspect structure.",
                                    arg.getClass().getName());
                            // Add logic here based on inspecting the actual `arg` structure
                        }

                    } catch (Throwable e) {
                        logger.error("Error in AnonymizeAttachmentFilenames patch", e);
                    }
                }));
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
    }
}
