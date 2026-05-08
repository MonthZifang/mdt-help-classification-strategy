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
        installModOverride(handler);

        handler.register("help-ui-reload", "重新加载 help 分类与备注配置。", args -> {
            try {
                reloadFromDisk();
                installHelpOverride(handler);
                installModOverride(handler);
                Log.info("MDT Help分类策略 已重载。version=@ pageSize=@ strategy=@", DISPLAY_VERSION, config.pageSize, config.strategy);
            } catch (IOException exception) {
                Log.err("MDT Help分类策略 重载失败: @", exception.getMessage());
            }
        });

        handler.register("help-ui-preview", "[help|mod|plugin] [page]", "在后台预览 help 页面内容。", args -> {
            preview(handler, args);
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        // Intentionally left blank. This plugin only replaces backend console help.
    }

    private void installHelpOverride(final CommandHandler handler) {
        handler.removeCommand("help");
        handler.register("help", "[page]", "显示原版后台命令分页。", args -> {
            printBuiltinPage(handler, args);
        });
    }

    private void installModOverride(final CommandHandler handler) {
        handler.removeCommand("mod");
        handler.register("mod", "[plugin|page] [page]", "显示插件后台命令分页。", args -> {
            printPluginPage(handler, args);
        });
    }

    private void preview(CommandHandler handler, String[] args) {
        if (args.length == 0) {
            printBuiltinPage(handler, new String[0]);
            return;
        }

        String mode = args[0].trim().toLowerCase(Locale.ROOT);
        if (mode.equals("mod") || mode.equals("plugin") || mode.equals("plugins")) {
            if (args.length > 1) {
                printPluginPage(handler, new String[] {args[1]});
            } else {
                printPluginPage(handler, new String[0]);
            }
            return;
        }
        if (mode.equals("help") || mode.equals("original")) {
            if (args.length > 1) {
                printBuiltinPage(handler, new String[] {args[1]});
            } else {
                printBuiltinPage(handler, new String[0]);
            }
            return;
        }
        printBuiltinPage(handler, args);
    }

    private void printBuiltinPage(CommandHandler handler, String[] args) {
        List<Command> commands = filterBuiltin(snapshot(handler));
        int page = args.length == 0 ? 1 : parsePage(args[0], 1);
        Log.info("MDT Help分类策略 @ | 原版后台命令分页 | 每页 @ 条", DISPLAY_VERSION, config.pageSize);
        Log.info("使用方式: help [页码]");
        printCategory("原版后台命令", commands, page);
    }

    private void printPluginPage(CommandHandler handler, String[] args) {
        List<Command> commands = snapshot(handler);
        List<Command> pluginCommands = filterPlugin(commands);

        if (args.length == 0) {
            Log.info("MDT Help分类策略 @ | 插件后台命令分页 | 每页 @ 条", DISPLAY_VERSION, config.pageSize);
            Log.info("使用方式: mod [页码] | mod <插件名> [页码]");
            printCategory("插件后台命令", pluginCommands, 1);
            return;
        }

        String first = args[0].trim();
        if (first.isEmpty()) {
            Log.info("MDT Help分类策略 @ | 插件后台命令分页 | 每页 @ 条", DISPLAY_VERSION, config.pageSize);
            Log.info("使用方式: mod [页码] | mod <插件名> [页码]");
            printCategory("插件后台命令", pluginCommands, 1);
            return;
        }

        if (isInteger(first)) {
            Log.info("MDT Help分类策略 @ | 插件后台命令分页 | 每页 @ 条", DISPLAY_VERSION, config.pageSize);
            Log.info("使用方式: mod [页码] | mod <插件名> [页码]");
            printCategory("插件后台命令", pluginCommands, parsePage(first, 1));
            return;
        }

        PluginCommandGroup group = resolveGroup(first);
        if (group != null) {
            List<Command> grouped = filterGroup(pluginCommands, group);
            if (!grouped.isEmpty()) {
                Log.info("MDT Help分类策略 @ | 插件独立分页 | @ | 每页 @ 条", DISPLAY_VERSION, group.displayName, config.pageSize);
                Log.info("使用方式: mod @ [页码]", group.id);
                printGroup(group, grouped, parseOptionalPage(args, 1));
                return;
            }
        }

        Log.info("未找到插件分页目标: @", first);
        Log.info("可用方式: mod [页码] | mod <插件名> [页码]");
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

    private PluginCommandGroup resolveGroup(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (PluginCommandGroup group : PLUGIN_GROUPS) {
            if (group.matchesIdentifier(normalized)) {
                return group;
            }
        }
        return null;
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
