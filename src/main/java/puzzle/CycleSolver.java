package puzzle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CycleSolver {
    public record Result(int solutionCount, boolean aborted, List<Integer> path, long nodes) {
        public boolean hasUniqueSolution() {
            return !aborted && solutionCount == 1 && !path.isEmpty();
        }
    }

    private final Board board;
    private final int whiteCount;
    private final int[] cellToId;
    private final int[] idToCell;
    private final int[][] adj;
    private final int[] degree;
    private final boolean[][] adjacent;
    private final boolean[] used;
    private final boolean[] seen;
    private final int[] stack;
    private final int[] path;
    private final int[] firstSolution;
    private final int[][] nextsByDepth;
    private final long nodeLimit;
    private int start;
    private int pathLen;
    private int solutionCount;
    private int firstSolutionLen;
    private long nodes;
    private boolean aborted;

    private CycleSolver(Board board, long nodeLimit) {
        this.board = board;
        this.nodeLimit = nodeLimit;
        this.whiteCount = board.whiteCount();
        this.cellToId = new int[board.rows() * board.cols()];
        Arrays.fill(cellToId, -1);
        this.idToCell = new int[whiteCount];

        int id = 0;
        for (int r = 0; r < board.rows(); r++) {
            for (int c = 0; c < board.cols(); c++) {
                if (board.isWhite(r, c)) {
                    int cell = board.cellIndex(r, c);
                    cellToId[cell] = id;
                    idToCell[id] = cell;
                    id++;
                }
            }
        }

        this.adj = new int[whiteCount][4];
        this.degree = new int[whiteCount];
        this.adjacent = new boolean[whiteCount][whiteCount];
        buildAdjacency();

        this.used = new boolean[whiteCount];
        this.seen = new boolean[whiteCount];
        this.stack = new int[whiteCount];
        this.path = new int[whiteCount];
        this.firstSolution = new int[whiteCount];
        this.nextsByDepth = new int[Math.max(1, whiteCount)][4];
    }

    public static Result solve(Board board, long nodeLimit) {
        return new CycleSolver(board, nodeLimit).solve();
    }

    private void buildAdjacency() {
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};
        for (int id = 0; id < whiteCount; id++) {
            int cell = idToCell[id];
            int row = board.rowOf(cell);
            int col = board.colOf(cell);
            for (int k = 0; k < 4; k++) {
                int nr = row + dr[k];
                int nc = col + dc[k];
                if (nr < 0 || nr >= board.rows() || nc < 0 || nc >= board.cols()) {
                    continue;
                }
                int nid = cellToId[board.cellIndex(nr, nc)];
                if (nid != -1) {
                    adj[id][degree[id]++] = nid;
                    adjacent[id][nid] = true;
                }
            }
        }
    }

    private Result solve() {
        if (whiteCount < 4 || (whiteCount & 1) == 1 || !balancedBipartition()) {
            return result(0, false);
        }
        for (int i = 0; i < whiteCount; i++) {
            if (degree[i] < 2) {
                return result(0, false);
            }
        }

        start = 0;
        for (int i = 1; i < whiteCount; i++) {
            if (degree[i] < degree[start]) {
                start = i;
            }
        }

        used[start] = true;
        path[0] = start;
        pathLen = 1;
        dfs(start, 1);
        return result(solutionCount, aborted);
    }

    private boolean balancedBipartition() {
        int even = 0;
        int odd = 0;
        for (int id = 0; id < whiteCount; id++) {
            int cell = idToCell[id];
            if (((board.rowOf(cell) + board.colOf(cell)) & 1) == 0) {
                even++;
            } else {
                odd++;
            }
        }
        return even == odd;
    }

    private Result result(int count, boolean isAborted) {
        ArrayList<Integer> cells = new ArrayList<>(firstSolutionLen);
        for (int i = 0; i < firstSolutionLen; i++) {
            cells.add(idToCell[firstSolution[i]]);
        }
        return new Result(count, isAborted, cells, nodes);
    }

    private void dfs(int cur, int usedCount) {
        if (++nodes > nodeLimit) {
            aborted = true;
            return;
        }
        if (aborted || solutionCount >= 2) {
            return;
        }

        if (usedCount == whiteCount) {
            if (adjacent[cur][start] && pathLen > 1 && path[1] < cur) {
                solutionCount++;
                if (firstSolutionLen == 0) {
                    firstSolutionLen = pathLen;
                    System.arraycopy(path, 0, firstSolution, 0, pathLen);
                }
            }
            return;
        }

        if (!stillPossible(cur, usedCount)) {
            return;
        }

        int[] nexts = nextsByDepth[usedCount];
        int nextCount = 0;
        for (int i = 0; i < degree[cur]; i++) {
            int to = adj[cur][i];
            if (!used[to]) {
                nexts[nextCount++] = to;
            }
        }
        sortByOnwardOptions(nexts, nextCount, cur);

        for (int i = 0; i < nextCount; i++) {
            int to = nexts[i];
            used[to] = true;
            path[pathLen++] = to;
            dfs(to, usedCount + 1);
            pathLen--;
            used[to] = false;
            if (aborted || solutionCount >= 2) {
                return;
            }
        }
    }

    private void sortByOnwardOptions(int[] values, int count, int cur) {
        for (int i = 1; i < count; i++) {
            int value = values[i];
            int score = onwardOptions(value, cur);
            int j = i - 1;
            while (j >= 0 && onwardOptions(values[j], cur) > score) {
                values[j + 1] = values[j];
                j--;
            }
            values[j + 1] = value;
        }
    }

    private boolean stillPossible(int cur, int usedCount) {
        int remaining = whiteCount - usedCount;
        if (remaining == 0) {
            return adjacent[cur][start];
        }

        boolean hasNext = false;
        for (int i = 0; i < degree[cur]; i++) {
            if (!used[adj[cur][i]]) {
                hasNext = true;
                break;
            }
        }
        if (!hasNext) {
            return false;
        }

        Arrays.fill(seen, false);
        int stackSize = 0;
        seen[cur] = true;
        stack[stackSize++] = cur;
        int reachableUnused = 0;
        while (stackSize > 0) {
            int v = stack[--stackSize];
            for (int i = 0; i < degree[v]; i++) {
                int to = adj[v][i];
                if (seen[to] || used[to]) {
                    continue;
                }
                seen[to] = true;
                reachableUnused++;
                stack[stackSize++] = to;
            }
        }
        if (reachableUnused != remaining) {
            return false;
        }

        for (int v = 0; v < whiteCount; v++) {
            if (used[v]) {
                continue;
            }
            int available = 0;
            for (int i = 0; i < degree[v]; i++) {
                int to = adj[v][i];
                if (!used[to] || to == cur || to == start) {
                    available++;
                }
            }
            if (available < 2) {
                return false;
            }
        }

        return true;
    }

    private int onwardOptions(int vertex, int cur) {
        int options = 0;
        for (int i = 0; i < degree[vertex]; i++) {
            int to = adj[vertex][i];
            if (!used[to] || to == start || to == cur) {
                options++;
            }
        }
        return options;
    }
}
