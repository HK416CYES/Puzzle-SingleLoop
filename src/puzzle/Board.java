package puzzle;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示不可变的黑白棋盘。
 * 白格用 {@code true} 保存，黑格用 {@code false} 保存。
 */
public final class Board {
    /** 保存 private。 */
    private final int rows;
    /** 保存 private。 */
    private final int cols;
    /** 保存 private。 */
    private final boolean[] white;

    /** 创建 Board 实例。 */
    private Board(int rows, int cols, boolean[] white) {
        this.rows = rows;
        this.cols = cols;
        this.white = white;
    }

    /**
     * 创建指定尺寸的全黑空棋盘。
     *
     * @param rows 棋盘行数
     * @param cols 棋盘列数
     * @return 新的空棋盘
     */
    public static Board empty(int rows, int cols) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("棋盘尺寸必须为正数");
        }
        return new Board(rows, cols, new boolean[rows * cols]);
    }

    /**
     * 根据一维白格数组创建棋盘。
     *
     * @param rows 棋盘行数
     * @param cols 棋盘列数
     * @param white 白格标记数组
     * @return 新的棋盘实例
     */
    public static Board fromCells(int rows, int cols, boolean[] white) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("棋盘尺寸必须为正数");
        }
        if (white.length != rows * cols) {
            throw new IllegalArgumentException("棋盘数据长度和尺寸不匹配");
        }
        return new Board(rows, cols, white.clone());
    }

    /**
     * 根据文本行解析棋盘。
     *
     * @param rawLines 由 0 和 1 组成的棋盘文本行
     * @return 解析后的棋盘
     */
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

    /**
     * 将棋盘转换为 0/1 文本行。
     *
     * @return 棋盘文本行
     */
    public List<String> toLines() {
        List<String> lines = new ArrayList<>(rows);
        for (int r = 0; r < rows; r++) {
            StringBuilder line = new StringBuilder(cols);
            for (int c = 0; c < cols; c++) {
                line.append(isWhite(r, c) ? '1' : '0');
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** 执行 index 相关逻辑。 */
    private static int index(int cols, int row, int col) {
        return row * cols + col;
    }

    /**
     * 获取棋盘行数。
     *
     * @return 棋盘行数
     */
    public int rows() {
        return rows;
    }

    /**
     * 获取棋盘列数。
     *
     * @return 棋盘列数
     */
    public int cols() {
        return cols;
    }

    /**
     * 判断指定位置是否为白格。
     *
     * @param row 行号
     * @param col 列号
     * @return 如果是白格则返回 {@code true}
     */
    public boolean isWhite(int row, int col) {
        return white[index(cols, row, col)];
    }

    /**
     * 将行列坐标转换为一维格子编号。
     *
     * @param row 行号
     * @param col 列号
     * @return 一维格子编号
     */
    public int cellIndex(int row, int col) {
        return index(cols, row, col);
    }

    /**
     * 获取格子编号对应的行号。
     *
     * @param cell 一维格子编号
     * @return 行号
     */
    public int rowOf(int cell) {
        return cell / cols;
    }

    /**
     * 获取格子编号对应的列号。
     *
     * @param cell 一维格子编号
     * @return 列号
     */
    public int colOf(int cell) {
        return cell % cols;
    }

    /**
     * 统计棋盘白格数量。
     *
     * @return 白格数量
     */
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
