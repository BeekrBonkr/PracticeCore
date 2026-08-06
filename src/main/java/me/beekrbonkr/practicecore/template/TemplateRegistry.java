package me.beekrbonkr.practicecore.template;

import me.beekrbonkr.practicecore.PracticeCorePlugin;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TemplateRegistry {

    private final PracticeCorePlugin plugin;
    private final File templatesDir;
    private final Map<String, ArenaTemplate> templates = new LinkedHashMap<>();

    public TemplateRegistry(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.templatesDir = new File(plugin.getDataFolder(), "templates");
    }

    public void loadAll() {
        templates.clear();
        if (!templatesDir.exists() && !templatesDir.mkdirs()) {
            plugin.getLogger().warning("Could not create templates directory");
            return;
        }
        File[] dirs = templatesDir.listFiles(File::isDirectory);
        if (dirs == null) {
            return;
        }
        for (File dir : dirs) {
            ArenaTemplate template = ArenaTemplate.load(dir);
            if (!template.schematicFile().exists()) {
                plugin.getLogger().warning("Template '" + template.name() + "' has no arena.schem — skipping");
                continue;
            }
            if (plugin.modes().get(template.mode()).isEmpty()) {
                plugin.getLogger().warning("Template '" + template.name() + "' uses unknown mode '"
                        + template.mode() + "' — skipping");
                continue;
            }
            templates.put(template.name(), template);
        }
        plugin.getLogger().info("Loaded " + templates.size() + " arena template(s), "
                + completeTemplates().size() + " complete");
    }

    public ArenaTemplate get(String name) {
        return templates.get(name);
    }

    public void register(ArenaTemplate template) {
        templates.put(template.name(), template);
    }

    public Collection<ArenaTemplate> all() {
        return templates.values();
    }

    public List<ArenaTemplate> completeTemplates() {
        return templates.values().stream().filter(ArenaTemplate::isComplete).toList();
    }

    public File dirFor(String name) {
        return new File(templatesDir, name);
    }
}
