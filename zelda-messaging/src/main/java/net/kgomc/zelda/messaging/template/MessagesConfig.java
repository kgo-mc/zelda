package net.kgomc.zelda.messaging.template;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * YAML model for {@code messages.yml}.
 *
 * <p>Each entry is a named message template supporting MiniMessage formatting,
 * palette tags, and {@code {placeholder}} substitution.</p>
 *
 * <p>Example file:</p>
 * <pre>{@code
 * messages:
 *   join:             "<primary>Welcome, <accent>{player_name}</accent>!</primary>"
 *   leave:            "<muted>{player_name} has left.</muted>"
 *   no_permission:    "<error>✗ You don't have permission.</error>"
 *   purchase_success: "<success>✓ Purchased <accent>{item}</accent> for <accent>{price}</accent> coins.</success>"
 *   server_restart:   "<rainbow>Server restarting in {seconds}s!</rainbow>"
 * }</pre>
 */
@Configuration
public class MessagesConfig {

    @Comment({
            "Named message templates.",
            "Supports MiniMessage formatting, palette tags (<primary>, <error>, etc.),",
            "gradients (<rainbow>), and {placeholder} substitution."
    })
    private final Map<String, String> messages = defaultMessages();

    private static Map<String, String> defaultMessages() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("join",          "<primary>Welcome, <accent>{player_name}</accent>!</primary>");
        defaults.put("leave",         "<muted>{player_name} has left.</muted>");
        defaults.put("no_permission", "<error>✗ You don't have permission to do that.</error>");
        defaults.put("error",         "<error>✗ An error occurred. Please try again.</error>");
        return defaults;
    }

    public Map<String, String> messages() {
        return messages;
    }
}