<p align="center">
	<img alt="Isekai logo" src="src/main/resources/assets/isekai/icon.png">
</p>
<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">Isekai</h1>
<h4 align="center">Isekai</h4>

## 简介
Make Isekai great again!

Isekai 是 Minecraft 1.20.1 的 Fabric 模组，加入了坐骑「陆行鸟」、刷怪蛋、孵化蛋和格拉姆硬币。

## 陆行鸟

- 在「异界生活幻想」创造模式标签页使用「陆行鸟刷怪蛋」，或用 `/summon isekai:land_bird` 召唤。
- 手持普通胡萝卜右键可驯服。驯服的成年陆行鸟可骑乘，装上马鞍后可以控制移动和跳跃。上鸟后按背包键可打开坐骑装备栏。
- 手持胡萝卜或金胡萝卜可以引诱附近清醒的陆行鸟。陆行鸟入夜会坐下休息，天亮后恢复原来的状态。成鸟装备马铠会显示对应材质，皮革马铠支持染色。
- 已驯服的幼鸟和成鸟都能由主人潜行右键切换游荡、坐下、跟随，动作栏会显示当前状态。野生幼鸟在游荡时会跟随附近的成鸟。
- 陆行鸟有六种外观，幼鸟约 20 分钟后会长成同色成鸟。可用 `/summon isekai:land_bird ~ ~ ~ {Plumage:6}` 指定颜色。
- 分别给两只成年陆行鸟喂金胡萝卜，它们会在原地掉落一颗随机颜色的蛋。

## 孵化蛋

- 「异界生活幻想」创造模式标签页有六种颜色的陆行鸟蛋，生存模式可通过繁殖获得。
- 放下蛋后，在三格距离内有点燃的营火、火、灵魂火、熔岩或岩浆块时会累计孵化时间。远离热源会暂停，累计达到设定时长后会孵出同色幼鸟，蛋上方需要留出空间。空手右键蛋可以查看进度。
- 受热时蛋会循环晃动，暂停孵化时会静止。孵化完成前会播放破壳动画。
- 第一位右键与孵化幼鸟互动的玩家会成为它的主人。

首次启动会生成 `config/isekai.properties`。默认 `eggIncubationSeconds=1200`，也就是 20 分钟。改成 `300` 就是 5 分钟。单人游戏改当前游戏实例的配置，联机改服务端的配置。重启后生效，已经放下的蛋会保留累计受热时间。

## 构建

使用 JDK 17 或 21。在 Windows 上运行 `./gradlew.bat build`，在 Linux 或 macOS 上运行 `./gradlew build`。产物位于 `build/libs/`，游戏运行时还需要 Fabric API 和 GeckoLib。
