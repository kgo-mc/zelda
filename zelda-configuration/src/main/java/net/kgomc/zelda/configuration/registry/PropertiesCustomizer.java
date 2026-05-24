package net.kgomc.zelda.configuration.registry;

import de.exlll.configlib.YamlConfigurationProperties;

/**
 * Allows customising the {@link YamlConfigurationProperties} for a specific
 * config file at registration time.
 *
 * <p>Receives the registry's shared properties as a starting point (already
 * a builder), so you only override what you need.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * registry.register(EconomyConfig.class, props -> props
 *     .toBuilder()
 *     .header("Economy config — edit with care!")
 *     .outputNulls(true)
 *     .build()
 * );
 * }</pre>
 */
@FunctionalInterface
public interface PropertiesCustomizer {
    YamlConfigurationProperties customize(YamlConfigurationProperties base);
}