package puzzle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

final class PlayerPath {
    private final Board board;
    private final ArrayList<Integer> cells = new ArrayList<>();
    private final ArrayDeque<RedoStep> redoSteps = new ArrayDeque<>();
    private boolean closed;

    PlayerPath(Board board) {
        this.board = board;
    }

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

    void reset() {
        cells.clear();
        closed = false;
        clearRedo();
    }

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

    boolean canUndo() {
        return closed || !cells.isEmpty();
    }

    boolean canRedo() {
        return !redoSteps.isEmpty();
    }

    boolean isClosed() {
        return closed;
    }

    List<Integer> cells() {
        return List.copyOf(cells);
    }

    private void clearRedo() {
        redoSteps.clear();
    }

    private boolean isWhiteCell(int cell) {
        if (cell < 0 || cell >= board.rows() * board.cols()) {
            return false;
        }
        return board.isWhite(board.rowOf(cell), board.colOf(cell));
    }

    private boolean isAdjacent(int a, int b) {
        int dr = Math.abs(board.rowOf(a) - board.rowOf(b));
        int dc = Math.abs(board.colOf(a) - board.colOf(b));
        return dr + dc == 1;
    }

    private record RedoStep(int cell, boolean closure) {
        private static RedoStep cell(int cell) {
            return new RedoStep(cell, false);
        }

        private static RedoStep closingStep() {
            return new RedoStep(-1, true);
        }
    }
}
