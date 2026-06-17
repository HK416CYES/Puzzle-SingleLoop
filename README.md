# Puzzle UI

This project contains a Java/Swing viewer and generator for the black-white cycle puzzle.

## V0.2 当前版本说明

V0.2 focuses on making the puzzle playable, not only viewable:

- 难度分为 `简单`、`困难`、`地狱` 三档，地狱难度会经过更多候选筛选，生成结果通常更密集、更复杂。
- 程序启动时会自动生成一张简单难度棋盘，不再显示全黑空棋盘。
- 玩家可以按住鼠标在白格上滑动，用细绿线连接路径。
- 路径只能连接相邻白格，不能重复经过白格；最后可以回到起点形成闭环。
- `提交` 会检查玩家路径是否与标准答案为同一个环，允许起点不同或方向相反。
- `←` 可以撤销一步，`→` 可以恢复撤销的一步。
- `显示答案` 会显示标准答案路径，`加载棋盘` 和 `保存棋盘` 支持 0/1 文本棋盘文件。
- CLI 支持生成、固定 seed 复现、唯一性检查，并在 stderr 输出生成耗时、尝试次数和求解节点数。

## Build

Requires JDK 17 or newer.

```bash
javac --release 17 -d out $(find src/puzzle -name '*.java')
```

## Run UI

```bash
java -cp out puzzle.PuzzleApp
```

When opened without a board file, the app automatically generates one easy puzzle. Select `简单`, `困难`, or `地狱` from the dropdown, then use the `随机生成` button to create another 10x10 puzzle with a unique solution.

You can also use `加载棋盘` to open a board text file, `保存棋盘` to save the current board, `显示答案` to reveal the standard cycle, and `提交` to check the player's green path.

You can pass a board file on startup:

```bash
java -cp out puzzle.PuzzleApp board.txt
```

The board file should contain only `0` and `1`, one row per line. `1` is a white cell and `0` is a black cell.

## Package For Windows

Run this on Windows with JDK 17 or newer installed. The script first checks `PATH`, then falls back to installed JDK directories under `C:\Program Files\Java`:

```bat
scripts\package-windows.bat
```

The script can be double-clicked from Windows Explorer. It prints each build step, pauses before closing, and creates a Windows app image at:

```text
dist\windows\Puzzle\Puzzle.exe
```

`jpackage` creates platform-specific executables, so the Windows `.exe` must be built on Windows.

## Generate From CLI

```bash
java -cp out puzzle.PuzzleApp --generate
java -cp out puzzle.PuzzleApp --generate easy
java -cp out puzzle.PuzzleApp --generate hard
java -cp out puzzle.PuzzleApp --generate hell
java -cp out puzzle.PuzzleApp --generate hell 12345
```

The generated board is printed to stdout. The difficulty, seed, attempt count, elapsed milliseconds, and solver node count are printed to stderr.

旧版参数仍兼容：`normal` 会映射到 `easy`，旧中文 `高等` 会映射到 `hard`。

## Check Uniqueness

```bash
java -cp out puzzle.PuzzleApp --check board.txt
```

The check command prints `1` when the board has exactly one legal cycle.
