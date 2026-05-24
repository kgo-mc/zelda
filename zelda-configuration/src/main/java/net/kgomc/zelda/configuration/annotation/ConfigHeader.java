package net.kgomc.zelda.configuration.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a file-level header comment written at the top of the generated YAML file.
 *
 * <p>Each element in {@link #value()} becomes a separate comment line.
 * Applied on top of the registry's shared header — if both are present,
 * this annotation takes priority and replaces it for that specific file.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @ZeldaConfig("messages.yml")
 * @ConfigHeader({
 *     "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
 *     "  Messages Configuration — KGO MC",
 *     "  Supports MiniMessage formatting",
 *     "  Docs: https://docs.advntr.dev/minimessage",
 *     "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
 * })
 * @Configuration
 * public class MessagesConfig { ... }
 * }</pre>
 *
 * <p>Generates:</p>
 * <pre>{@code
 * # ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * #   Messages Configuration — KGO MC
 * #   Supports MiniMessage formatting
 * #   Docs: https://docs.advntr.dev/minimessage
 * # ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigHeader {

    /**
     * Lines of the header comment. Each element becomes one {@code # } prefixed line.
     */
    String[] value();
}