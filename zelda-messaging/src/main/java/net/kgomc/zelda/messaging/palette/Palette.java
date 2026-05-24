package net.kgomc.zelda.messaging.palette;

import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A named palette mapping semantic color names to hex values,
 * plus named gradients resolved as MiniMessage gradient tags.
 *
 * <p>All palette entries — solid colors and gradients — are exposed as
 * MiniMessage {@link TagResolver}s so {@code <primary>}, {@code <rainbow>}
 * etc. resolve directly during parsing with zero per-call overhead.</p>
 */
public final class Palette {

    private final String name;
    private final Map<String, String>        colors;
    private final Map<String, GradientEntry> gradients;

    /** Cached TagResolver covering both solid colors and gradients — built once */
    private final TagResolver resolver;

    public Palette(String name, Map<String, String> colors, Map<String, GradientEntry> gradients) {
        this.name      = name;
        this.colors    = Collections.unmodifiableMap(new HashMap<>(colors));
        this.gradients = Collections.unmodifiableMap(new HashMap<>(gradients));
        this.resolver  = buildResolver(this.colors, this.gradients);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String getName()                           { return name; }
    public Map<String, String> getColors()            { return colors; }
    public Map<String, GradientEntry> getGradients()  { return gradients; }

    public Optional<String> getColor(String name) {
        return Optional.ofNullable(colors.get(name));
    }

    public Optional<GradientEntry> getGradient(String name) {
        return Optional.ofNullable(gradients.get(name));
    }

    /** Cached resolver — pass directly to MiniMessage deserializer. */
    public TagResolver getTagResolver() { return resolver; }

    // -----------------------------------------------------------------------
    // Merge
    // -----------------------------------------------------------------------

    /**
     * Returns a new Palette with color and gradient overrides applied.
     * Used for per-player theme overrides — start from global, override specifics.
     */
    public Palette merge(String newName,
                         Map<String, String> colorOverrides,
                         Map<String, GradientEntry> gradientOverrides) {
        Map<String, String>        mergedColors    = new HashMap<>(colors);
        Map<String, GradientEntry> mergedGradients = new HashMap<>(gradients);
        mergedColors.putAll(colorOverrides);
        mergedGradients.putAll(gradientOverrides);
        return new Palette(newName, mergedColors, mergedGradients);
    }

    private static TagResolver buildResolver(Map<String, String> colors,
                                             Map<String, GradientEntry> gradients) {
        TagResolver.Builder builder = TagResolver.builder();

        // Solid color tags — <primary>, <error>, etc.
        for (Map.Entry<String, String> entry : colors.entrySet()) {
            TextColor color = TextColor.fromHexString(entry.getValue());
            if (color == null) continue;
            String tagName = entry.getKey();
            builder.tag(tagName, Tag.styling(style -> style.color(color)));
        }

        // Gradient tags — <rainbow>, <fire>, etc.
        // Strategy: register each gradient name as a tag that delegates to
        // MiniMessage's built-in <gradient> tag with the configured stops.
        // e.g. <rainbow>text</rainbow> → <gradient:#5865F2:#EB459E>text</gradient>
        net.kyori.adventure.text.minimessage.MiniMessage mm =
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();

        for (Map.Entry<String, GradientEntry> entry : gradients.entrySet()) {
            String tagName    = entry.getKey();
            List<String> stops = entry.getValue().stops();
            if (stops == null || stops.size() < 2) continue;

            // Validate all stops are valid hex — skip the gradient if any are bad
            boolean valid = stops.stream().allMatch(s -> TextColor.fromHexString(s) != null);
            if (!valid) continue;

            // Build the gradient tag string once — reused on every resolution
            String gradientTag = "<gradient:" + String.join(":", stops) + ">";
            String closeTag    = "</gradient>";

            builder.tag(tagName, (argumentQueue, context) ->
                    Tag.selfClosingInserting(
                            // Parse the gradient opening as a component and wrap content in it
                            // We return an inserting tag that opens the gradient styling
                            mm.deserialize(gradientTag + " " + closeTag)
                    )
            );
        }

        return builder.build();
    }
}