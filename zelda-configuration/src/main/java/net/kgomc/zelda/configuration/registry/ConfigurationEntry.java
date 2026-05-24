package net.kgomc.zelda.configuration.registry;

import de.exlll.configlib.YamlConfigurationStore;

import java.nio.file.Path;

/**
 * Internal holder for a single registered configuration.
 *
 * @param <T> the configuration type
 */
record ConfigurationEntry<T>(
        Class<T>                  type,
        T                         instance,
        YamlConfigurationStore<T> store,
        Path                      path
) {}