package puzzle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class CycleSolver {
    public record Result(int solutionCount, boolean aborted, List<Integer> path) {
        public boolean hasUniqueSolution() {
            return !aborted && solutionCount == 1 && !path.isEmpty();
        }
    }

    private final Board board;
    private final int whiteCount;
    private final int[] cellToId;
    private final int[] idToCell;
    private final List<Integer>[] adj;
    private final boolean[] used;
    private final ArrayList<Integer> path = new ArrayList<>();
    private final long nodeLimit;
    private int start;
    private int solutionCount;
    private long nodes;
    private boolean aborted;
    private List<Integer> firstSolution = List.of();

    @SuppressWarnings("unchecked")
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

        this.adj = new List[whiteCount];
        for (int i = 0; i < whiteCount; i++) {
            adj[i] = new ArrayList<>();
        }

        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};
        for (int i = 0; i < whiteCount; i++) {
            int cell = idToCell[i];
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
                    adj[i].add(nid);
                }
            }
        }

        this.used = new boolean[whiteCount];
    }

    public static Result solve(Board board, long nodeLimit) {
        return new CycleSolver(board, nodeLimit).solve();
    }

    private Result solve() {
        if (whiteCount < 4 || (whiteCount & 1) == 1) {
            return new Result(0, false, List.of());
        }
        for (List<Integer> edges : adj) {
            if (edges.size() < 2) {
                return new Result(0, false, List.of());
            }
        }

        start = 0;
        for (int i = 1; i < whiteCount; i++) {
            if (adj[i].size() < adj[start].size()) {
                start = i;
            }
        }

        used[start] = true;
        path.add(start);
        dfs(start, 1);

        List<Integer> cells = new ArrayList<>();
        for (int id : firstSolution) {
            cells.add(idToCell[id]);
        }
        return new Result(solutionCount, aborted, cells);
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
            if (isAdjacent(cur, start) && path.size() > 1 && path.get(1) < cur) {
                solutionCount++;
                if (firstSolution.isEmpty()) {
                    firstSolution = List.copyOf(path);
                }
            }
            return;
        }

        if (!stillPossible(cur, usedCount)) {
            return;
        }

        List<Integer> nexts = new ArrayList<>();
        for (int to : adj[cur]) {
            if (!used[to]) {
                nexts.add(to);
            }
        }
        nexts.sort(Comparator.comparingInt(to -> onwardOptions(to, cur)));

        for (int to : nexts) {
            used[to] = true;
            path.add(to);
            dfs(to, usedCount + 1);
            path.remove(path.size() - 1);
            used[to] = false;
            if (aborted || solutionCount >= 2) {
                return;
            }
        }
    }

    private boolean stillPossible(int cur, int usedCount) {
        int remaining = whiteCount - usedCount;
        if (remaining == 0) {
            return isAdjacent(cur, start);
        }

        boolean hasNext = false;
        for (int to : adj[cur]) {
            if (!used[to]) {
                hasNext = true;
                break;
            }
        }
        if (!hasNext) {
            return false;
        }

        boolean[] seen = new boolean[whiteCount];
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        seen[cur] = true;
        stack.push(cur);
        int reachableUnused = 0;
        while (!stack.isEmpty()) {
            int v = stack.pop();
            for (int to : adj[v]) {
                if (seen[to] || used[to]) {
                    continue;
                }
                seen[to] = true;
                reachableUnused++;
                stack.push(to);
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
            for (int to : adj[v]) {
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
        for (int to : adj[vertex]) {
            if (!used[to] || to == start || to == cur) {
                options++;
            }
        }
        return options;
    }

    private boolean isAdjacent(int a, int b) {
        return adj[a].contains(b);
    }
}
