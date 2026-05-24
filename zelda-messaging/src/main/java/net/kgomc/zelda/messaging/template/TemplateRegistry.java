package net.kgomc.zelda.messaging.template;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds all named message templates.
 *
 * <p>Templates are loaded from {@link MessagesConfig} (YAML) and can be
 * overridden or extended programmatically. Programmatic entries always
 * take priority over YAML-defined ones.</p>
 *
 * <pre>{@code
 * // YAML templates loaded automatically by MessagingModule
 * registry.get("join"); // → "<primary>Welcome, <accent>{player_name}</accent>!</primary>"
 *
 * // Programmatic override — takes priority over YAML
 * registry.register("join", "<rainbow>Welcome {player_name}!</rainbow>");
 *
 * // Programmatic-only entry
 * registry.register("vip_welcome", "<accent>Welcome back, VIP {player_name}!</accent>");
 * }</pre>
 */
public final class TemplateRegistry {

    /** YAML-loaded templates — base layer */
    private final ConcurrentHashMap<String, String> yamlTemplates        = new ConcurrentHashMap<>();

    /** Programmatic overrides — always win over YAML */
    private final ConcurrentHashMap<String, String> programmaticTemplates = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Load from YAML
    // -----------------------------------------------------------------------

    /**
     * Loads templates from a {@link MessagesConfig}.
     * Called on enable and on reload — replaces existing YAML templates.
     * Programmatic overrides are preserved.
     */
    public void load(MessagesConfig config) {
        yamlTemplates.clear();
        yamlTemplates.putAll(config.messages());
    }

    // -----------------------------------------------------------------------
    // Programmatic registration
    // -----------------------------------------------------------------------

    /**
     * Registers or overrides a template programmatically.
     * Takes priority over any YAML-defined template with the same key.
     */
    public void register(String key, String template) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        if (template == null)             throw new IllegalArgumentException("template must not be null");
        programmaticTemplates.put(key, template);
    }

    /** Removes a programmatic override, falling back to the YAML template if present. */
    public void unregister(String key) {
        programmaticTemplates.remove(key);
    }

    // -----------------------------------------------------------------------
    // Retrieval
    // -----------------------------------------------------------------------

    /**
     * Returns the raw template string for a key.
     * Programmatic templates take priority over YAML.
     *
     * @return the template string, or empty if not found in either source
     */
    public Optional<String> get(String key) {
        String programmatic = programmaticTemplates.get(key);
        if (programmatic != null) return Optional.of(programmatic);
        return Optional.ofNullable(yamlTemplates.get(key));
    }

    /**
     * Returns the raw template string, throwing if not found.
     *
     * @throws IllegalArgumentException if the key is not registered
     */
    public String getOrThrow(String key) {
        return get(key).orElseThrow(() ->
                new IllegalArgumentException("No message template registered for key: '" + key + "'"));
    }

    public boolean contains(String key) {
        return programmaticTemplates.containsKey(key) || yamlTemplates.containsKey(key);
    }

    /** Returns all registered keys from both sources. */
    public java.util.Set<String> keys() {
        java.util.Set<String> keys = new java.util.HashSet<>(yamlTemplates.keySet());
        keys.addAll(programmaticTemplates.keySet());
        return java.util.Collections.unmodifiableSet(keys);
    }
}