package me.beekrbonkr.practicecore.template;

import me.beekrbonkr.practicecore.PracticeCorePlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Unpacks the arena template that ships inside the jar so a fresh install is
 * immediately playable. Installed exactly once: a marker file records that it
 * has happened, so an admin who deletes or renames the arena does not get it
 * silently recreated on the next restart.
 */
public final class BundledTemplate {

    private static final String RESOURCE_DIR = "default_template";
    private static final List<String> FILES = List.of("arena.yml", "arena.schem");
    private static final String MARKER = ".bundled-installed";

    private final PracticeCorePlugin plugin;

    public BundledTemplate(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** @return true when files were written this call. */
    public boolean installIfAbsent(Path templatesDir) {
        if (!plugin.pcConfig().bundledTemplateEnabled()) {
            return false;
        }
        Path marker = plugin.getDataFolder().toPath().resolve(MARKER);
        if (Files.exists(marker)) {
            return false;
        }
        String name = plugin.pcConfig().bundledTemplateName();
        Path target = templatesDir.resolve(name);
        if (TemplateRegistry.findArenaFolder(templatesDir, name) != null) {
            // Someone already has an arena by this name — in a category folder
            // or not — so don't touch it, but don't keep re-checking every
            // boot either.
            writeMarker(marker, name);
            return false;
        }
        try {
            Files.createDirectories(target);
            for (String file : FILES) {
                try (InputStream in = plugin.getResource(RESOURCE_DIR + "/" + file)) {
                    if (in == null) {
                        plugin.getLogger().warning("Bundled template resource missing: " + file);
                        return false;
                    }
                    Files.copy(in, target.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not install the bundled arena template: " + e.getMessage());
            return false;
        }
        writeMarker(marker, name);
        plugin.getLogger().info("Installed the bundled arena template as '" + name
                + "' — /practice join " + name);
        return true;
    }

    private void writeMarker(Path marker, String name) {
        try {
            Files.writeString(marker, """
                    PracticeCore installed its bundled arena template as '%s'.
                    Delete this file to have it installed again on the next start.
                    """.formatted(name));
        } catch (IOException e) {
            // Worst case the check runs again next boot and finds the folder.
            plugin.getLogger().warning("Could not write the bundled-template marker: " + e.getMessage());
        }
    }
}
