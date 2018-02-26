/*
 */
package com.infinityraider.infinitylib.utility;

import java.text.MessageFormat;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;

/**
 * Utility class for messaging players.
 */
public class MessageUtil {

    public static final void messagePlayer(@Nullable EntityPlayer player, @Nonnull String format, Object... args) {
        // If the player is null don't do anything.
        if (player == null) {
            return;
        }
        
        // Holder for formatted message.
        String message;
        
        // Format the message.
        try {
            message = MessageFormat.format(format, args);
        } catch (IllegalArgumentException ex) {
            message = "Message Formatting Error: \"" + format + "\"!";
        }
        
        // Send the message.
        player.sendMessage(new TextComponentString(message));
    }

}
