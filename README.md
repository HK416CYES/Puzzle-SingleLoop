# Puzzle UI

This project contains a Java/Swing viewer for the black-white cycle puzzle.

## Build

```bash
javac -d out $(find src/main/java -name '*.java')
```

## Run UI

```bash
java -cp out puzzle.PuzzleApp
```

The app starts without a built-in default puzzle. Select a difficulty from the dropdown, then use the `随机生成` button to create a new 10x10 puzzle with a unique solution. You can also use `加载棋盘` to open a board text file, and `保存棋盘` to save the current board as a `0`/`1` text file.

You can pass a board file on startup:

```bash
java -cp out puzzle.PuzzleApp board.txt
```

The board file should contain only `0` and `1`, one row per line. `1` is a white cell and `0` is a black cell.

## Generate From CLI

```bash
java -cp out puzzle.PuzzleApp --generate
java -cp out puzzle.PuzzleApp --generate normal
java -cp out puzzle.PuzzleApp --generate hard
java -cp out puzzle.PuzzleApp --generate hard 12345
```

The generated board is printed to stdout. The difficulty, seed, attempt count, elapsed milliseconds, and solver node count are printed to stderr.

## Check Uniqueness

```bash
java -cp out puzzle.PuzzleApp --check board.txt
```

The check command prints `1` when the board has exactly one legal cycle.
