package net.kgomc.zelda.ui.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central placeholder resolution service.
 *
 * <p>Resolution priority (first non-empty wins):</p>
 * <ol>
 *   <li>Handler-local {@code @Placeholder} methods (handled in {@code MenuFactory} before this)</li>
 *   <li>Custom {@link PlaceholderResolver}s registered via {@link #addResolver}</li>
 *   <li>PlaceholderAPI (auto-detected at runtime — no hard dependency)</li>
 * </ol>
 *
 * <p>If none resolve, the placeholder token is left as-is.</p>
 */
public final class PlaceholderService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^{}]+)}");

    private final List<PlaceholderResolver> resolvers = new ArrayList<>();
    private final Logger  logger;

    private final java.lang.reflect.Method papiMethod;

    public PlaceholderService(Logger logger) {
        this.logger     = logger;
        this.papiMethod = resolvePapiMethod();
        if (papiMethod != null) {
            logger.info("[Zelda/UI] PlaceholderAPI detected — PAPI placeholders enabled.");
        }
    }

    /** Adds a custom resolver. Added resolvers are tried before PAPI. */
    public void addResolver(PlaceholderResolver resolver) {
        resolvers.add(resolver);
    }

    /**
     * Replaces all {@code {key}} tokens in the input string using the
     * resolution chain. Unresolved tokens are left unchanged.
     *
     * @param text   input string, may be null
     * @param player the viewing player
     * @return string with placeholders substituted
     */
    public String resolve(String text, Player player) {
        if (text == null || text.isEmpty() || !text.contains("{")) return text;

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key         = matcher.group(1);
            String replacement = resolveKey(key, player).orElse(matcher.group(0)); // keep original if unresolved
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves a list of strings (e.g. lore lines) in one call.
     */
    public List<String> resolveAll(List<String> lines, Player player) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) result.add(resolve(line, player));
        return result;
    }


    private Optional<String> resolveKey(String key, Player player) {
        // 1. Custom resolvers
        for (PlaceholderResolver resolver : resolvers) {
            Optional<String> result = resolver.resolve(player, key);
            if (result.isPresent()) return result;
        }

        // 2. PAPI — use cached method reference, no reflection overhead per call
        if (papiMethod != null) {
            try {
                String papiResult = (String) papiMethod.invoke(null, player, "%" + key + "%");
                if (papiResult != null && !papiResult.equals("%" + key + "%")) {
                    return Optional.of(papiResult);
                }
            } catch (Exception e) {
                // PAPI lookup failed — silently skip
            }
        }

        return Optional.empty();
    }

    /**
     * Resolves and caches the PAPI {@code setPlaceholders} method once at startup.
     * Returns null if PAPI is not installed.
     */
    private static java.lang.reflect.Method resolvePapiMethod() {
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return null;
            java.lang.reflect.Method method = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                    .getMethod("setPlaceholders", Player.class, String.class);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            return null;
        }
    }
}