# 黑白环形连线谜题

这是一个使用 Java/Swing 编写的黑白棋盘环形连线谜题程序，包含棋盘生成、唯一解验证、图形界面游玩、答案显示、棋盘保存与加载，以及 Windows 打包脚本。

## V0.2 版本说明

V0.2 的重点是让程序从“棋盘查看器”变成可以实际游玩的谜题应用：

- 难度分为 `简单`、`困难`、`地狱` 三档，地狱难度会经过更多候选筛选，生成结果通常更密集、更复杂。
- 程序启动时会自动生成一张简单难度棋盘，不再显示全黑空棋盘。
- 玩家可以按住鼠标在白格上滑动，用细绿线连接路径。
- 路径只能连接相邻白格，不能重复经过白格；最后可以回到起点形成闭环。
- `提交` 会检查玩家路径是否与标准答案为同一个环，允许起点不同或方向相反。
- `←` 可以撤销一步，`→` 可以恢复撤销的一步。
- `显示答案` 会显示标准答案路径，`加载棋盘` 和 `保存棋盘` 支持 0/1 文本棋盘文件。
- 命令行模式支持生成棋盘、固定 seed 复现、唯一性检查，并在 stderr 输出生成耗时、尝试次数和求解节点数。

## 环境要求

需要安装 JDK 17 或更新版本。

## 编译

```bash
javac --release 17 -d out $(find src/puzzle -name '*.java')
```

## 启动图形界面

```bash
java -cp out puzzle.PuzzleApp
```

不传入棋盘文件时，程序会自动生成一张简单难度棋盘。可以在下拉框中选择 `简单`、`困难` 或 `地狱`，然后点击 `随机生成` 创建新的 10x10 唯一解棋盘。

界面中还可以使用：

- `加载棋盘`：打开 0/1 文本棋盘文件。
- `保存棋盘`：将当前棋盘保存为文本文件。
- `显示答案`：显示标准答案环。
- `提交`：检查玩家绘制的绿色路径是否正确。
- `←` / `→`：撤销或重做玩家路径的一步。

启动时也可以直接传入棋盘文件：

```bash
java -cp out puzzle.PuzzleApp board.txt
```

棋盘文件只能包含 `0` 和 `1`，每行表示棋盘的一行。`1` 表示白格，`0` 表示黑格。

## Windows 打包

在 Windows 上安装 JDK 17 或更新版本后，运行：

```bat
scripts\package-windows.bat
```

脚本可以在资源管理器中双击运行。它会自动查找电脑中可用的 JDK 17+，选择版本最新且包含 `javac`、`jar`、`jpackage` 的 JDK，然后完成编译和打包。

打包结果位于：

```text
dist\windows\Puzzle\Puzzle.exe
```

`jpackage` 只能创建当前平台对应的可执行程序，因此 Windows 的 `.exe` 需要在 Windows 系统上打包。

## 命令行生成棋盘

```bash
java -cp out puzzle.PuzzleApp --generate
java -cp out puzzle.PuzzleApp --generate easy
java -cp out puzzle.PuzzleApp --generate hard
java -cp out puzzle.PuzzleApp --generate hell
java -cp out puzzle.PuzzleApp --generate hell 12345
```

生成出的棋盘会输出到 stdout。难度、seed、尝试次数、耗时和求解节点数会输出到 stderr。

旧版参数仍兼容：`normal` 会映射到 `easy`，旧中文 `高等` 会映射到 `hard`。

## 检查唯一解

```bash
java -cp out puzzle.PuzzleApp --check board.txt
```

如果棋盘恰好只有一个合法环，命令会输出 `1`。
