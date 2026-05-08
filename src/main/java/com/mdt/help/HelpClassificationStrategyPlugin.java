package com.mdt.help;

import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public final class HelpClassificationStrategyPlugin extends Plugin {
    @Override
    public void init() {
        Log.info("MDT Help分类策略 loaded.");
        Log.info("配置目录建议: config/mods/config/mdt-help-classification-strategy");
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("help-ui-reload", "重新加载 help 分类与备注配置。", args -> {
            Log.info("MDT Help分类策略 命令占位已触发: help-ui-reload");
        });

        handler.register("help-ui-preview", "[pluginName|all]", "在后台预览 help 页面内容。", args -> {
            Log.info("MDT Help分类策略 命令占位已触发: help-ui-preview");
        });

    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("help", "[pluginName|all]", "查看分类后的 help 页面或指定插件命令。", (args, player) -> {
            player.sendMessage("[accent]MDT Help分类策略[] 命令占位已触发: help");
        });

    }
}
