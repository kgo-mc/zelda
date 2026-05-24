package net.kgomc.zelda.configuration.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a ConfigLib {@code @Configuration} class as a Zelda-managed config file.
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Minimal — file lands in baseDir/settings.yml
 * @ZeldaConfig("settings.yml")
 * @Configuration
 * public class SettingsConfig { ... }
 *
 * // Subdirectory — file lands in baseDir/modules/economy/economy.yml
 * @ZeldaConfig(value = "economy.yml", path = "modules/economy")
 * @Configuration
 * public class EconomyConfig { ... }
 *
 * // With a file-level header written at the top of the YAML
 * @ZeldaConfig(value = "messages.yml")
 * @ConfigHeader({
 *     "Messages Configuration",
 *     "Supports MiniMessage formatting: https://docs.advntr.dev/minimessage"
 * })
 * @Configuration
 * public class MessagesConfig { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ZeldaConfig {

    /**
     * The YAML filename. Must end in {@code .yml} or {@code .yaml}.
     *
     * <p>Resolved relative to {@link #path()} if set, otherwise relative to
     * the registry's base directory.</p>
     */
    String value();

    /**
     * Optional subdirectory path relative to the registry's base directory.
     *
     * <p>Defaults to {@code ""} — meaning the file lives directly in the base dir.</p>
     *
     * <p>Example: {@code path = "modules/economy"} → file resolves to
     * {@code baseDir/modules/economy/<value>}</p>
     */
    String path() default "";
}