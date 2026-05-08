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
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import mindustry.Vars;
import mindustry.mod.Plugin;

public final class HelpClassificationStrategyPlugin extends Plugin {
    private static final String CONFIG_DIR_NAME = "mdt-help-classification-strategy";
    private static final String CONFIG_FILE_NAME = "help-classification-strategy.properties";
    private static final String NATIVE_HELP_FILE_NAME = "native-help-zh.properties";
    private static final String DISPLAY_VERSION = "V1.0.0 - 157X";
    private static final String MARKET_COMPATIBILITY_VERSION = "v1";

    private static final Set<String> BUILT_IN_COMMANDS = new LinkedHashSet<String>(Arrays.asList(
        "help",
        "version",
        "exit",
        "stop",
        "host",
        "maps",
        "reloadpatches",
        "reloadmaps",
        "status",
        "mods",
        "mod",
        "js",
        "say",
        "pause",
        "rules",
        "fillitems",
        "playerlimit",
        "config",
        "subnet-ban",
        "name-ban",
        "whitelist",
        "shuffle",
        "nextmap",
        "kick",
        "ban",
        "bans",
        "unban",
        "pardon",
        "admin",
        "admins",
        "players",
        "runwave",
        "loadautosave",
        "load",
        "save",
        "saves",
        "gameover",
        "info",
        "search",
        "gc",
        "yes",
        "dos-ban",
        "mdtreload"
    ));

    private static final PluginCommandGroup[] PLUGIN_GROUPS = new PluginCommandGroup[] {
        new PluginCommandGroup("core-module-of-the-plugin-market", "插件市场核心模块", new String[] {"market-"}, new String[0]),
        new PluginCommandGroup("mdt-jump-plugin-native", "MDT 跳转插件原版显示模块", new String[] {"comid-display-"}, new String[0]),
        new PluginCommandGroup("mdt-jump-plugin", "MDT 跳转插件", new String[] {"jump-comid-"}, new String[0]),
        new PluginCommandGroup("mdt-help-classification-strategy", "MDT Help分类策略", new String[] {"help-ui-"}, new String[] {"help"}),
        new PluginCommandGroup("mdt-list-data-system", "MDT 列表数据系统", new String[] {"list-data-"}, new String[] {"listdata"}),
        new PluginCommandGroup("mdt-api-interface-call", "MDT API接口调用", new String[] {"api-call-"}, new String[] {"apicall"}),
        new PluginCommandGroup("mdt-bound-unbound", "MDT 绑定与未绑定", new String[] {"bind-state-"}, new String[] {"bindstate"}),
        new PluginCommandGroup("mdt-player-economy-system", "MDT 玩家经济系统", new String[] {"economy-"}, new String[] {"economy"}),
        new PluginCommandGroup("mdt-player-leaderboard", "MDT 玩家排行榜", new String[] {"leaderboard-"}, new String[] {"leaderboard"}),
        new PluginCommandGroup("mdt-player-level-system", "MDT 玩家等级系统", new String[] {"level-"}, new String[] {"level"}),
        new PluginCommandGroup("mdt-chat-access", "MDT 聊天出入", new String[] {"chat-access-"}, new String[0]),
        new PluginCommandGroup("mdt-anonymous-plugin", "MDT 匿名插件", new String[] {"anonymous-"}, new String[] {"anonymous"}),
        new PluginCommandGroup("mdt-planning-plan-restart", "MDT 规划计划重启", new String[] {"plan-restart"}, new String[0])
    };

    private File dataRoot;
    private HelpConfig config;
    private Map<String, String> nativeHelpNotes = new LinkedHashMap<String, String>();

    @Override
    public void init() {
        try {
            File modsRoot = new File(Vars.dataDirectory.absolutePath(), "mods");
            dataRoot = new File(new File(modsRoot, "config"), CONFIG_DIR_NAME);
            reloadFromDisk();
            Log.info("MDT Help分类策略 loaded. version=@ market=@", DISPLAY_VERSION, MARKET_COMPATIBILITY_VERSION);
            Log.info("配置目录: @", new File(dataRoot, CONFIG_FILE_NAME).getAbsolutePath());
        } catch (IOException exception) {
            throw new RuntimeException("MDT Help分类策略初始化失败。", exception);
        }
    }

    @Override
    public void registerServerCommands(final CommandHandler handler) {
        installHelpOverride(handler);

        handler.register("help-ui-reload", "重新加载 help 分类与备注配置。", args -> {
            try {
                reloadFromDisk();
                installHelpOverride(handler);
                Log.info("MDT Help分类策略 已重载。version=@ pageSize=@ strategy=@", DISPLAY_VERSION, config.pageSize, config.strategy);
            } catch (IOException exception) {
                Log.err("MDT Help分类策略 重载失败: @", exception.getMessage());
            }
        });

        handler.register("help-ui-preview", "[section|plugin|command|page] [page]", "在后台预览 help 页面内容。", args -> {
            printHelp(handler, args);
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        // Intentionally left blank. This plugin only replaces backend console help.
    }

    private void installHelpOverride(final CommandHandler handler) {
        handler.removeCommand("help");
        handler.register("help", "[section|plugin|command|page] [page]", "显示分类后的后台 help。", args -> {
            printHelp(handler, args);
        });
    }

    private void printHelp(CommandHandler handler, String[] args) {
        List<Command> commands = snapshot(handler);
        if (args.length == 0) {
            printOverview(commands, 1);
            return;
        }

        String first = args[0].trim();
        if (first.isEmpty()) {
            printOverview(commands, 1);
            return;
        }

        if (isInteger(first)) {
            printOverview(commands, parsePage(first, 1));
            return;
        }

        String normalized = first.toLowerCase(Locale.ROOT);
        if (matchesAny(normalized, "original", "native", "vanilla", "后台原版", "原版")) {
            printCategory("原版后台命令", filterBuiltin(commands), parseOptionalPage(args, 1));
            return;
        }
        if (matchesAny(normalized, "plugin", "plugins", "插件")) {
            printCategory("插件后台命令", filterPlugin(commands), parseOptionalPage(args, 1));
            return;
        }
        if (normalized.equals(config.allKeyword.toLowerCase(Locale.ROOT)) || matchesAny(normalized, "all", "全部")) {
            printCategory("全部后台命令", commands, parseOptionalPage(args, 1));
            return;
        }

        Command exact = findExactCommand(commands, first);
        if (exact != null) {
            printCommandDetails(exact);
            return;
        }

        PluginCommandGroup group = resolveGroup(first);
        if (group != null) {
            List<Command> grouped = filterGroup(commands, group);
            if (!grouped.isEmpty()) {
                printGroup(group, grouped, parseOptionalPage(args, 1));
                return;
            }
        }

        Log.info("未找到 help 目标: @", first);
        printUsage(commands);
    }

    private void printOverview(List<Command> commands, int page) {
        List<Command> builtin = filterBuiltin(commands);
        List<Command> plugin = filterPlugin(commands);
        Log.info("MDT Help分类策略 @ | 后台 help 已接管 | 市场兼容 @", DISPLAY_VERSION, MARKET_COMPATIBILITY_VERSION);
        Log.info("使用方式: help [页码] | help original [页码] | help plugin [页码] | help all [页码] | help <插件名> | help <命令名>");
        Log.info("分类策略=@ | 每页最大命令数=@", config.strategy, config.pageSize);
        printCategory("原版后台命令", builtin, page);
        printCategory("插件后台命令", plugin, page);
    }

    private void printCategory(String title, List<Command> commands, int page) {
        int safePage = sanitizePage(page);
        int totalPages = pageCount(commands.size());
        if (safePage > totalPages) {
            safePage = totalPages;
        }
        int start = (safePage - 1) * config.pageSize;
        int end = Math.min(start + config.pageSize, commands.size());

        Log.info("==== @ | 第 @/@ 页 | 共 @ 条 ====", title, safePage, totalPages, commands.size());
        if (commands.isEmpty()) {
            Log.info("  暂无命令。");
            return;
        }

        for (int i = start; i < end; i++) {
            Command command = commands.get(i);
            String note = builtinNote(command);
            if (note.isEmpty()) {
                Log.info("  @ @ - @", command.text, command.paramText, command.description);
            } else {
                Log.info("  @ @ - @ | 备注=@",
                    command.text,
                    command.paramText,
                    command.description,
                    note
                );
            }
        }
    }

    private void printGroup(PluginCommandGroup group, List<Command> commands, int page) {
        Log.info("==== 插件命令 | @ | @ ====", group.id, group.displayName);
        printCategory(group.displayName, commands, page);
    }

    private void printCommandDetails(Command command) {
        Log.info("==== 命令详情 ====");
        Log.info("命令=@", command.text);
        Log.info("参数=@", command.paramText == null || command.paramText.isEmpty() ? "(无)" : command.paramText);
        Log.info("说明=@", command.description == null || command.description.isEmpty() ? "(无描述)" : command.description);
        Log.info("分类=@", isBuiltin(command) ? "原版后台命令" : "插件后台命令");
        String note = builtinNote(command);
        if (!note.isEmpty()) {
            Log.info("中文备注=@", note);
        }
    }

    private void printUsage(List<Command> commands) {
        Log.info("可用帮助命令示例：");
        Log.info("  help");
        Log.info("  help 2");
        Log.info("  help original 1");
        Log.info("  help plugin 1");
        Log.info("  help @ 1", config.allKeyword);
        for (PluginCommandGroup group : PLUGIN_GROUPS) {
            List<Command> matched = filterGroup(commands, group);
            if (!matched.isEmpty()) {
                Log.info("  help @", group.id);
            }
        }
    }

    private List<Command> snapshot(CommandHandler handler) {
        Seq<Command> source = handler.getCommandList();
        List<Command> copy = new ArrayList<Command>(source.size);
        for (Command command : source) {
            copy.add(command);
        }
        return copy;
    }

    private List<Command> filterBuiltin(List<Command> commands) {
        List<Command> result = new ArrayList<Command>();
        for (Command command : commands) {
            if (isBuiltin(command)) {
                result.add(command);
            }
        }
        return result;
    }

    private List<Command> filterPlugin(List<Command> commands) {
        List<Command> result = new ArrayList<Command>();
        for (Command command : commands) {
            if (!isBuiltin(command)) {
                result.add(command);
            }
        }
        return result;
    }

    private List<Command> filterGroup(List<Command> commands, PluginCommandGroup group) {
        List<Command> result = new ArrayList<Command>();
        for (Command command : commands) {
            if (group.matches(command.text)) {
                result.add(command);
            }
        }
        return result;
    }

    private boolean isBuiltin(Command command) {
        return BUILT_IN_COMMANDS.contains(command.text);
    }

    private String builtinNote(Command command) {
        if (!config.enableNativeNotes) {
            return "";
        }
        String note = nativeHelpNotes.get(command.text);
        return note == null ? "" : note;
    }

    private Command findExactCommand(List<Command> commands, String name) {
        for (Command command : commands) {
            if (command.text.equalsIgnoreCase(name)) {
                return command;
            }
        }
        return null;
    }

    private PluginCommandGroup resolveGroup(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (PluginCommandGroup group : PLUGIN_GROUPS) {
            if (group.matchesIdentifier(normalized)) {
                return group;
            }
        }
        return null;
    }

    private boolean matchesAny(String value, String... options) {
        for (String option : options) {
            if (value.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    private int parseOptionalPage(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        return parsePage(args[index], 1);
    }

    private int parsePage(String value, int fallback) {
        if (!isInteger(value)) {
            return fallback;
        }
        return sanitizePage(Integer.parseInt(value));
    }

    private int sanitizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int pageCount(int size) {
        return Math.max(1, (size + config.pageSize - 1) / config.pageSize);
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void reloadFromDisk() throws IOException {
        ensureDataRoot();
        File configFile = new File(dataRoot, CONFIG_FILE_NAME);
        File noteFile = new File(dataRoot, NATIVE_HELP_FILE_NAME);
        copyDefaultIfMissing(CONFIG_FILE_NAME, configFile);
        copyDefaultIfMissing(NATIVE_HELP_FILE_NAME, noteFile);
        config = HelpConfig.load(configFile);
        nativeHelpNotes = loadNotes(noteFile);
    }

    private void ensureDataRoot() throws IOException {
        if (!dataRoot.exists() && !dataRoot.mkdirs()) {
            throw new IOException("无法创建帮助插件配置目录: " + dataRoot);
        }
    }

    private void copyDefaultIfMissing(String resourceName, File target) throws IOException {
        if (target.exists()) {
            return;
        }
        InputStream inputStream = HelpClassificationStrategyPlugin.class.getClassLoader().getResourceAsStream(resourceName);
        if (inputStream == null) {
            throw new IOException("缺少默认资源: " + resourceName);
        }
        Files.copy(inputStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        inputStream.close();
    }

    private Map<String, String> loadNotes(File file) throws IOException {
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        Map<String, String> notes = new LinkedHashMap<String, String>();
        for (String key : properties.stringPropertyNames()) {
            notes.put(key.trim(), properties.getProperty(key, "").trim());
        }
        return notes;
    }

    private static final class HelpConfig {
        final int pageSize;
        final String strategy;
        final String allKeyword;
        final boolean enableNativeNotes;

        private HelpConfig(int pageSize, String strategy, String allKeyword, boolean enableNativeNotes) {
            this.pageSize = Math.max(1, pageSize);
            this.strategy = strategy;
            this.allKeyword = allKeyword;
            this.enableNativeNotes = enableNativeNotes;
        }

        static HelpConfig load(File file) throws IOException {
            Properties properties = new Properties();
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                properties.load(reader);
            }

            int pageSize = parseInt(properties.getProperty("帮助.每页最大命令数"), 15);
            String strategy = read(properties, "帮助.分类策略", "default");
            String allKeyword = read(properties, "帮助.全部模式关键字", "all");
            boolean enableNativeNotes = parseBoolean(properties.getProperty("帮助.原版命令中文备注"), true);
            return new HelpConfig(pageSize, strategy, allKeyword, enableNativeNotes);
        }

        private static int parseInt(String value, int fallback) {
            try {
                return Integer.parseInt(value == null ? "" : value.trim());
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }

        private static boolean parseBoolean(String value, boolean fallback) {
            return value == null || value.trim().isEmpty() ? fallback : Boolean.parseBoolean(value.trim());
        }

        private static String read(Properties properties, String key, String fallback) {
            String value = properties.getProperty(key);
            return value == null || value.trim().isEmpty() ? fallback : value.trim();
        }
    }

    private static final class PluginCommandGroup {
        final String id;
        final String displayName;
        final String[] prefixes;
        final String[] explicitCommands;

        private PluginCommandGroup(String id, String displayName, String[] prefixes, String[] explicitCommands) {
            this.id = id;
            this.displayName = displayName;
            this.prefixes = prefixes;
            this.explicitCommands = explicitCommands;
        }

        boolean matches(String commandName) {
            for (String prefix : prefixes) {
                if (commandName.startsWith(prefix)) {
                    return true;
                }
            }
            for (String explicit : explicitCommands) {
                if (commandName.equalsIgnoreCase(explicit)) {
                    return true;
                }
            }
            return false;
        }

        boolean matchesIdentifier(String value) {
            return id.equalsIgnoreCase(value)
                || displayName.toLowerCase(Locale.ROOT).contains(value)
                || value.contains(id.toLowerCase(Locale.ROOT));
        }
    }
}
