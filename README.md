<div align="center">
  <a href="https://github.com/MonthZifang/YUEYUEDAO-TECH">
    <img src="./md/logo.png" alt="YUEYUEDAO TECH Logo" width="720" />
  </a>

  <p><strong>YUEYUEDAO TECH 维护 MDT Help分类策略</strong></p>

  <p>
    <a href="https://github.com/MonthZifang/YUEYUEDAO-TECH"><strong>查看月月岛科技详情</strong></a>
  </p>
</div>

# MDT Help分类策略

将注册到 help 的命令与原版命令区分开来，提供可交互分页页面，并支持原版命令汉化备注、插件分类与 `help 插件名` 快速查看。

## 市场固定识别文件

仓库根目录固定提供以下文件，供插件市场识别：

```text
market.plugin.json
plugin.json
```

## 依赖

- 无强依赖。

## 配置文件

首次启动后建议维护以下配置文件：

```text
config/mods/config/mdt-help-classification-strategy/help-classification-strategy.properties
```

- 每页最多命令数默认 15，可按配置调整。
- 支持全部不分类模式与默认分类模式切换。
- 支持对原版命令增加中文备注。
- 支持 `a`、`s`、`回车`、`空格`、`esc`、`q` 等交互键。

## 功能说明

- 原版命令与插件命令分开展示，避免混排。
- 支持分页、上一页、下一页与确认交互。
- 支持 `help 插件名` 快速查看某个插件的可用命令。
- 支持界面字符串颜色、分类规则和备注语言调整。

## 数据与写入说明

- 该插件主要负责命令展示与分类，不写入复杂业务数据。

## 命令

- `help-ui-reload`：重新加载 help 分类与备注配置。
- `help-ui-preview [pluginName|all]`：在后台预览 help 页面内容。
- `/help [pluginName|all]`：查看分类后的 help 页面或指定插件命令。

## Help 注册备注

- `help mdt-help-classification-strategy`：查看 MDT Help分类策略 的独立命令说明。
- 中文备注建议写为“帮助页重载、帮助页预览、分类帮助查看”。

## 附带资源

- 附带 `src/main/resources/native-help-zh.properties` 作为原版命令汉化备注示例。

## 插件入口

```text
com.mdt.help.HelpClassificationStrategyPlugin
```

## 版本规则

- 当前插件版本：`v1`
- 当前需求市场版本：`v1`
