package puzzle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理玩家当前绘制的路径以及撤销、重做状态。
 */
final class PlayerPath {
    /** 保存 private。 */
    private final Board board;
    /** 保存 private。 */
    private final ArrayList<Integer> cells = new ArrayList<>();
    /** 保存 private。 */
    private final ArrayDeque<RedoStep> redoSteps = new ArrayDeque<>();
    /** 保存 private。 */
    private boolean closed;

    /**
     * 创建绑定到指定棋盘的玩家路径状态。
     *
     * @param board 当前棋盘
     */
    PlayerPath(Board board) {
        this.board = board;
    }

    /**
     * 尝试将格子追加到玩家路径。
     *
     * @param cell 待追加的格子编号
     * @return 如果成功追加或成功闭环则返回 {@code true}
     */
    boolean addCell(int cell) {
        if (!isWhiteCell(cell) || closed) {
            return false;
        }
        if (cells.isEmpty()) {
            cells.add(cell);
            clearRedo();
            return true;
        }

        int last = cells.get(cells.size() - 1);
        if (last == cell || !isAdjacent(last, cell)) {
            return false;
        }
        if (cell == cells.get(0)) {
            if (cells.size() < 4) {
                return false;
            }
            closed = true;
            clearRedo();
            return true;
        }
        if (cells.contains(cell)) {
            return false;
        }

        cells.add(cell);
        clearRedo();
        return true;
    }

    /**
     * 清空当前路径和重做历史。
     */
    void reset() {
        cells.clear();
        closed = false;
        clearRedo();
    }

    /**
     * 撤销最近一步路径操作。
     *
     * @return 如果成功撤销则返回 {@code true}
     */
    boolean undo() {
        if (closed) {
            closed = false;
            redoSteps.push(RedoStep.closingStep());
            return true;
        }
        if (cells.isEmpty()) {
            return false;
        }
        redoSteps.push(RedoStep.cell(cells.remove(cells.size() - 1)));
        return true;
    }

    /**
     * 恢复最近一次撤销的路径操作。
     *
     * @return 如果成功重做则返回 {@code true}
     */
    boolean redo() {
        if (redoSteps.isEmpty()) {
            return false;
        }
        RedoStep step = redoSteps.pop();
        if (step.closure()) {
            if (cells.size() < 4 || !isAdjacent(cells.get(cells.size() - 1), cells.get(0))) {
                clearRedo();
                return false;
            }
            closed = true;
            return true;
        }

        int cell = step.cell();
        if (!isWhiteCell(cell) || (!cells.isEmpty() && !isAdjacent(cells.get(cells.size() - 1), cell))) {
            clearRedo();
            return false;
        }
        cells.add(cell);
        return true;
    }

    /**
     * 判断当前路径是否存在可撤销步骤。
     *
     * @return 如果可以撤销则返回 {@code true}
     */
    boolean canUndo() {
        return closed || !cells.isEmpty();
    }

    /**
     * 判断当前路径是否存在可重做步骤。
     *
     * @return 如果可以重做则返回 {@code true}
     */
    boolean canRedo() {
        return !redoSteps.isEmpty();
    }

    /**
     * 判断路径是否已经回到起点形成闭环。
     *
     * @return 如果已经闭环则返回 {@code true}
     */
    boolean isClosed() {
        return closed;
    }

    /**
     * 获取玩家路径中的格子序列。
     *
     * @return 玩家路径格子序列
     */
    List<Integer> cells() {
        return List.copyOf(cells);
    }

    /**
     * 判断格子是否已经在玩家路径中。
     *
     * @param cell 待检查的格子编号
     * @return 如果格子已经位于路径中则返回 {@code true}
     */
    boolean containsCell(int cell) {
        return cells.contains(cell);
    }

    /** 执行 clearRedo 相关逻辑。 */
    private void clearRedo() {
        redoSteps.clear();
    }

    /** 执行 isWhiteCell 相关逻辑。 */
    private boolean isWhiteCell(int cell) {
        if (cell < 0 || cell >= board.rows() * board.cols()) {
            return false;
        }
        return board.isWhite(board.rowOf(cell), board.colOf(cell));
    }

    /** 执行 isAdjacent 相关逻辑。 */
    private boolean isAdjacent(int a, int b) {
        int dr = Math.abs(board.rowOf(a) - board.rowOf(b));
        int dc = Math.abs(board.colOf(a) - board.colOf(b));
        return dr + dc == 1;
    }

    /** 表示 RedoStep 记录。 */
    private record RedoStep(int cell, boolean closure) {
        /** 执行 cell 相关逻辑。 */
        private static RedoStep cell(int cell) {
            return new RedoStep(cell, false);
        }

        /** 执行 closingStep 相关逻辑。 */
        private static RedoStep closingStep() {
            return new RedoStep(-1, true);
        }
    }
}
