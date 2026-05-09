package com.mdt.help;

import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.CommandHandler.Command;
import arc.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import mindustry.Vars;
import mindustry.mod.Plugin;

public final class HelpClassificationStrategyPlugin extends Plugin {
    private static final String CONFIG_DIR_NAME = "mdt-help-classification-strategy";
    private static final String CONFIG_FILE_NAME = "help-classification-strategy.properties";
    private static final String NOTES_FILE_NAME = "native-help-zh.properties";
    private static final String DISPLAY_VERSION = "V1.1.0 - 157X";

    private static final Set<String> BUILT_IN_COMMANDS = new LinkedHashSet<String>(Arrays.asList(
        "help", "version", "exit", "stop", "host", "maps", "reloadpatches", "reloadmaps", "status",
        "mods", "mod", "js", "say", "pause", "rules", "fillitems", "playerlimit", "config",
        "subnet-ban", "name-ban", "whitelist", "shuffle", "nextmap", "kick", "ban", "bans",
        "unban", "pardon", "admin", "admins", "players", "runwave", "loadautosave", "load",
        "save", "saves", "gameover", "info", "search", "gc", "yes", "dos-ban", "mdtreload"
    ));

    private File dataRoot;
    private HelpConfig config;
    private final Map<String, String> commandNotes = new LinkedHashMap<String, String>();

    @Override
    public void init() {
        try {
            dataRoot = resolveDataRoot();
            ensureDefaultFiles();
            reloadInternal();
            Log.info("MDT Help Classification loaded. version=@", DISPLAY_VERSION);
            Log.info("Config directory: @", new File(dataRoot, CONFIG_FILE_NAME).getAbsolutePath());
        } catch (IOException exception) {
            throw new RuntimeException("Failed to initialize MDT Help Classification.", exception);
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("help-ui-reload", "\u91cd\u65b0\u52a0\u8f7d help \u5206\u7c7b\u914d\u7f6e\u3002", args -> {
            try {
                reloadInternal();
                Log.info("\u5df2\u91cd\u65b0\u52a0\u8f7d help \u5206\u7c7b\u3002\u6bcf\u9875=@ \u7ec4\u6570=@", config.pageSize, config.pluginGroups.size());
            } catch (IOException exception) {
                Log.err("\u91cd\u65b0\u52a0\u8f7d help \u5206\u7c7b\u5931\u8d25: @", exception.getMessage());
            }
        });

        handler.register("help-ui-preview", "[help|mod|plugin] [page]", "\u5728\u670d\u52a1\u7aef\u9884\u89c8 help \u5206\u9875\u3002", args -> {
            preview(handler, args);
        });

        handler.register("help", "[page]", "\u67e5\u770b\u539f\u751f\u670d\u52a1\u7aef\u547d\u4ee4\u3002", args -> {
            int page = args.length > 0 ? parsePositiveInt(args[0], 1) : 1;
            printBuiltInPage(handler, page);
        });

        handler.register("mod", "[plugin|page] [page]", "\u67e5\u770b\u63d2\u4ef6\u547d\u4ee4\u5e2e\u52a9\u3002", args -> {
            printPluginView(handler, args);
        });
    }

    private void preview(CommandHandler handler, String[] args) {
        if (args.length == 0) {
            printBuiltInPage(handler, 1);
            return;
        }

        String mode = args[0].trim().toLowerCase();
        int page = args.length > 1 ? parsePositiveInt(args[1], 1) : 1;
        if ("help".equals(mode)) {
            printBuiltInPage(handler, page);
            return;
        }
        if ("mod".equals(mode) || "plugin".equals(mode) || "plugins".equals(mode)) {
            printPluginOverview(handler, page);
            return;
        }

        PluginCommandGroup group = findGroup(mode);
        if (group == null) {
            Log.info("\u672a\u627e\u5230\u63d2\u4ef6\u5206\u7ec4: @", args[0]);
            return;
        }
        printPluginGroupPage(handler, group, page);
    }

    private void printBuiltInPage(CommandHandler handler, int page) {
        logHeader(config.builtInTitle);
        Log.info("\u7528\u6cd5: help [\u9875\u7801]");
        printCategory(config.builtInTitle, collectBuiltInCommands(handler), page);
    }

    private void printPluginView(CommandHandler handler, String[] args) {
        logHeader(config.pluginTitle);
        Log.info("\u7528\u6cd5: mod [\u9875\u7801] | mod <\u63d2\u4ef6\u5206\u7ec4> [\u9875\u7801]");
        if (args.length == 0) {
            printPluginOverview(handler, 1);
            return;
        }

        Integer pageCandidate = tryParsePositiveInt(args[0]);
        if (pageCandidate != null) {
            printPluginOverview(handler, pageCandidate.intValue());
            return;
        }

        PluginCommandGroup group = findGroup(args[0]);
        if (group == null) {
            Log.info("\u672a\u77e5\u63d2\u4ef6\u5206\u7ec4: @", args[0]);
            Log.info("\u53ef\u7528\u5206\u7ec4: @", availableGroupIds());
            return;
        }
        int page = args.length > 1 ? parsePositiveInt(args[1], 1) : 1;
        printPluginGroupPage(handler, group, page);
    }

    private void printPluginOverview(CommandHandler handler, int page) {
        printCategory(config.pluginTitle, collectPluginCommands(handler), page);
    }

    private void printPluginGroupPage(CommandHandler handler, PluginCommandGroup group, int page) {
        printCategory(group.displayName + " [" + group.id + "]", collectPluginGroupCommands(handler, group), page);
    }

    private void printCategory(String title, List<Command> commands, int page) {
        int safePage = Math.max(page, 1);
        int totalPages = Math.max(1, (commands.size() + config.pageSize - 1) / config.pageSize);
        if (safePage > totalPages) {
            safePage = totalPages;
        }

        Log.info("==== @ | \u7b2c @/@ \u9875 | \u5171 @ \u6761 ====", title, safePage, totalPages, commands.size());
        if (commands.isEmpty()) {
            Log.info("  \u6682\u65e0\u53ef\u7528\u547d\u4ee4\u3002");
            return;
        }

        int start = (safePage - 1) * config.pageSize;
        int end = Math.min(start + config.pageSize, commands.size());
        for (int index = start; index < end; index++) {
            Command command = commands.get(index);
            String params = normalize(command.paramText);
            String description = resolveDescription(command);
            String segment = params.isEmpty() ? command.text : command.text + " " + params;
            Log.info("  @ - @", segment, description);
        }
    }

    private String resolveDescription(Command command) {
        String note = config.showNotes ? normalize(commandNotes.get(command.text)) : "";
        if (!note.isEmpty()) {
            return note;
        }
        String description = normalize(command.description);
        return description.isEmpty() ? "\u6682\u65e0\u4e2d\u6587\u8bf4\u660e" : description;
    }

    private List<Command> collectBuiltInCommands(CommandHandler handler) {
        List<Command> result = new ArrayList<Command>();
        for (Command command : copyCommands(handler.getCommandList())) {
            if (BUILT_IN_COMMANDS.contains(command.text)) {
                result.add(command);
            }
        }
        return result;
    }

    private List<Command> collectPluginCommands(CommandHandler handler) {
        List<Command> result = new ArrayList<Command>();
        for (PluginCommandGroup group : config.pluginGroups.values()) {
            result.addAll(collectPluginGroupCommands(handler, group));
        }
        return result;
    }

    private List<Command> collectPluginGroupCommands(CommandHandler handler, PluginCommandGroup group) {
        List<Command> result = new ArrayList<Command>();
        for (Command command : copyCommands(handler.getCommandList())) {
            if (!BUILT_IN_COMMANDS.contains(command.text) && group.matches(command.text)) {
                result.add(command);
            }
        }
        return result;
    }

    private List<Command> copyCommands(Seq<Command> source) {
        List<Command> result = new ArrayList<Command>(source.size);
        for (Command command : source) {
            result.add(command);
        }
        return result;
    }

    private PluginCommandGroup findGroup(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        for (PluginCommandGroup group : config.pluginGroups.values()) {
            if (group.matchesIdentifier(normalized)) {
                return group;
            }
        }
        return null;
    }

    private String availableGroupIds() {
        List<String> ids = new ArrayList<String>(config.pluginGroups.keySet());
        return ids.toString();
    }

    private void reloadInternal() throws IOException {
        config = loadConfig(new File(dataRoot, CONFIG_FILE_NAME));
        commandNotes.clear();
        commandNotes.putAll(loadNotes(new File(dataRoot, NOTES_FILE_NAME)));
    }

    private HelpConfig loadConfig(File file) throws IOException {
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        int pageSize = parsePositiveInt(properties.getProperty("help.pageSize"), 15);
        boolean showNotes = Boolean.parseBoolean(properties.getProperty("help.showNotes", "true"));
        String builtInTitle = properties.getProperty("help.builtInTitle", "\u539f\u751f\u670d\u52a1\u7aef\u547d\u4ee4").trim();
        String pluginTitle = properties.getProperty("help.pluginTitle", "\u63d2\u4ef6\u547d\u4ee4").trim();

        LinkedHashMap<String, PluginCommandGroup> groups = new LinkedHashMap<String, PluginCommandGroup>();
        String rawGroups = properties.getProperty("plugin.groups", "").trim();
        if (!rawGroups.isEmpty()) {
            for (String id : rawGroups.split(",")) {
                String normalizedId = id.trim().toLowerCase();
                if (normalizedId.isEmpty()) {
                    continue;
                }
                String display = properties.getProperty("plugin.group." + normalizedId + ".display", normalizedId).trim();
                String prefixesRaw = properties.getProperty("plugin.group." + normalizedId + ".prefixes", "").trim();
                List<String> prefixes = new ArrayList<String>();
                for (String prefix : prefixesRaw.split(",")) {
                    String value = prefix.trim();
                    if (!value.isEmpty()) {
                        prefixes.add(value);
                    }
                }
                groups.put(normalizedId, new PluginCommandGroup(normalizedId, display, prefixes));
            }
        }

        return new HelpConfig(pageSize, showNotes, builtInTitle, pluginTitle, groups);
    }

    private Map<String, String> loadNotes(File file) throws IOException {
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        Map<String, String> notes = new LinkedHashMap<String, String>();
        for (String name : new TreeSet<String>(properties.stringPropertyNames())) {
            notes.put(name, normalize(properties.getProperty(name)));
        }
        return notes;
    }

    private void ensureDefaultFiles() throws IOException {
        if (!dataRoot.exists() && !dataRoot.mkdirs() && !dataRoot.isDirectory()) {
            throw new IOException("Unable to create config directory: " + dataRoot.getAbsolutePath());
        }
        copyIfMissing(CONFIG_FILE_NAME);
        copyIfMissing(NOTES_FILE_NAME);
    }

    private void copyIfMissing(String resourceName) throws IOException {
        File target = new File(dataRoot, resourceName);
        if (target.exists()) {
            return;
        }
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Missing default resource: " + resourceName);
            }
            Files.copy(inputStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private File resolveDataRoot() {
        File modsRoot = new File(Vars.dataDirectory.absolutePath(), "mods");
        return new File(new File(modsRoot, "config"), CONFIG_DIR_NAME);
    }

    private void logHeader(String mode) {
        Log.info("MDT Help Classification @ | @ | \u6bcf\u9875=@", DISPLAY_VERSION, mode, config.pageSize);
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? "" : value.trim();
    }

    private int parsePositiveInt(String raw, int fallback) {
        Integer parsed = tryParsePositiveInt(raw);
        return parsed == null ? fallback : parsed.intValue();
    }

    private Integer tryParsePositiveInt(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? Integer.valueOf(value) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static final class HelpConfig {
        private final int pageSize;
        private final boolean showNotes;
        private final String builtInTitle;
        private final String pluginTitle;
        private final LinkedHashMap<String, PluginCommandGroup> pluginGroups;

        private HelpConfig(int pageSize, boolean showNotes, String builtInTitle, String pluginTitle, LinkedHashMap<String, PluginCommandGroup> pluginGroups) {
            this.pageSize = Math.max(1, pageSize);
            this.showNotes = showNotes;
            this.builtInTitle = builtInTitle;
            this.pluginTitle = pluginTitle;
            this.pluginGroups = pluginGroups;
        }
    }

    private static final class PluginCommandGroup {
        private final String id;
        private final String displayName;
        private final List<String> prefixes;

        private PluginCommandGroup(String id, String displayName, List<String> prefixes) {
            this.id = id;
            this.displayName = displayName;
            this.prefixes = prefixes;
        }

        private boolean matches(String commandText) {
            if (commandText == null) {
                return false;
            }
            for (String prefix : prefixes) {
                if (commandText.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesIdentifier(String value) {
            return id.equalsIgnoreCase(value);
        }
    }
}
