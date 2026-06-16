package puzzle;

import java.util.ArrayList;
import java.util.List;

public final class Board {
    private static final String[] DEFAULT_ROWS = {
        "0111110110",
        "0111111111",
        "1110111111",
        "1010110110",
        "1110110110",
        "0110110000",
        "0000110110",
        "1111110110",
        "1111111111",
        "0110111111",
    };

    private final int rows;
    private final int cols;
    private final boolean[] white;

    private Board(int rows, int cols, boolean[] white) {
        this.rows = rows;
        this.cols = cols;
        this.white = white;
    }

    public static Board fromCells(int rows, int cols, boolean[] white) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("棋盘尺寸必须为正数");
        }
        if (white.length != rows * cols) {
            throw new IllegalArgumentException("棋盘数据长度和尺寸不匹配");
        }
        return new Board(rows, cols, white.clone());
    }

    public static Board defaultBoard() {
        return fromLines(List.of(DEFAULT_ROWS));
    }

    public static Board fromLines(List<String> rawLines) {
        List<String> lines = new ArrayList<>();
        for (String raw : rawLines) {
            String line = raw.strip();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("棋盘不能为空");
        }

        int rows = lines.size();
        int cols = lines.get(0).length();
        if (cols == 0) {
            throw new IllegalArgumentException("棋盘宽度不能为空");
        }

        boolean[] white = new boolean[rows * cols];
        for (int r = 0; r < rows; r++) {
            String line = lines.get(r);
            if (line.length() != cols) {
                throw new IllegalArgumentException("每一行的长度必须相同");
            }
            for (int c = 0; c < cols; c++) {
                char ch = line.charAt(c);
                if (ch == '1') {
                    white[index(cols, r, c)] = true;
                } else if (ch != '0') {
                    throw new IllegalArgumentException("棋盘只能包含 0 和 1");
                }
            }
        }

        return new Board(rows, cols, white);
    }

    private static int index(int cols, int row, int col) {
        return row * cols + col;
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    public boolean isWhite(int row, int col) {
        return white[index(cols, row, col)];
    }

    public int cellIndex(int row, int col) {
        return index(cols, row, col);
    }

    public int rowOf(int cell) {
        return cell / cols;
    }

    public int colOf(int cell) {
        return cell % cols;
    }

    public int whiteCount() {
        int count = 0;
        for (boolean cell : white) {
            if (cell) {
                count++;
            }
        }
        return count;
    }
}
