package puzzle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** { 对应的内部状态。 */
public final class CycleSolver {
    /**
     * 表示求解器输出。
     *
     * @param solutionCount 已找到的答案数量，最多统计到两个
     * @param aborted 是否因节点上限中止
     * @param path 第一条答案路径
     * @param nodes 搜索访问节点数
     */
    public record Result(int solutionCount, boolean aborted, List<Integer> path, long nodes) {
        /**
         * 判断求解结果是否为唯一解。
         *
         * @return 如果存在且仅存在一个完整答案则返回 {@code true}
         */
        public boolean hasUniqueSolution() {
            return !aborted && solutionCount == 1 && !path.isEmpty();
        }
    }

    /** 当前棋盘。 */
    private final Board board;
    /** 白格总数。 */
    private final int whiteCount;
    /** 棋盘格子编号到白格顶点编号的映射。 */
    private final int[] cellToId;
    /** 白格顶点编号到棋盘格子编号的映射。 */
    private final int[] idToCell;
    /** 白格邻接表。 */
    private final int[][] adj;
    /** 每个白格顶点的邻接数量。 */
    private final int[] degree;
    /** 白格顶点是否相邻的快速查询表。 */
    private final boolean[][] adjacent;
    /** 搜索中已经使用的顶点标记。 */
    private final boolean[] used;
    /** 连通性剪枝复用的访问标记。 */
    private final boolean[] seen;
    /** 连通性剪枝复用的栈。 */
    private final int[] stack;
    /** 当前已知答案环路径。 */
    private final int[] path;
    /** 第一条完整答案路径。 */
    private final int[] firstSolution;
    /** 每一层 DFS 复用的候选邻居缓冲区。 */
    private final int[][] nextsByDepth;
    /** 搜索节点上限。 */
    private final long nodeLimit;
    /** DFS 固定起点。 */
    private int start;
    /** 当前 DFS 路径长度。 */
    private int pathLen;
    /** 已经找到的答案数量。 */
    private int solutionCount;
    /** 第一条答案路径长度。 */
    private int firstSolutionLen;
    /** 已经访问的搜索节点数。 */
    private long nodes;
    /** 是否因为超过节点上限而中止。 */
    private boolean aborted;

    /**
     * 创建 CycleSolver 实例。
     *
     * @param board 棋盘对象。
     * @param nodeLimit 搜索节点上限。
     */
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

    /**
     * 求解指定棋盘。
     *
     * @param board 待求解棋盘
     * @param nodeLimit 搜索节点上限
     * @return 求解结果
     */
    public static Result solve(Board board, long nodeLimit) {
        return new CycleSolver(board, nodeLimit).solve();
    }

    /** 为所有白格顶点建立四方向邻接关系。 */
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

    /**
     * 执行当前求解器实例的搜索流程。
     *
     * @return 求解结果
     */
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

    /**
     * 检查白格在棋盘二分染色中的数量是否平衡。
     *
     * @return 如果两种棋盘染色上的白格数量相等则返回 true。
     */
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

    /**
     * 构造求解结果对象。
     *
     * @param count 答案数量。
     * @param isAborted 是否中止。
     * @return 求解结果。
     */
    private Result result(int count, boolean isAborted) {
        ArrayList<Integer> cells = new ArrayList<>(firstSolutionLen);
        for (int i = 0; i < firstSolutionLen; i++) {
            cells.add(idToCell[firstSolution[i]]);
        }
        return new Result(count, isAborted, cells, nodes);
    }

    /**
     * 从当前顶点继续深度优先搜索哈密顿环。
     *
     * @param cur 当前顶点。
     * @param usedCount 已经使用的顶点数量。
     */
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

    /**
     * 按后续可选数量对候选邻居排序。
     *
     * @param values 待排序的候选顶点数组。
     * @param count 答案数量。
     * @param cur 当前顶点。
     */
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

    /**
     * 检查当前 DFS 状态是否仍可能完成环。
     *
     * @param cur 当前顶点。
     * @param usedCount 已经使用的顶点数量。
     * @return 如果当前搜索状态仍可能完成答案环则返回 true。
     */
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

    /**
     * 计算顶点在当前搜索状态下的后续选择数量。
     *
     * @param vertex 顶点编号。
     * @param cur 当前顶点。
     * @return 后续可选顶点数量。
     */
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
