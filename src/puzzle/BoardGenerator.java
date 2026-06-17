package puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** { 对应的内部状态。 */
public final class BoardGenerator {
    /**
     * 表示一次生成结果及其诊断信息。
     *
     * @param board 生成出的棋盘
     * @param result 棋盘对应的求解结果
     * @param seed 本次生成使用的随机种子
     * @param attempts 生成尝试次数
     * @param elapsedMillis 生成耗时，单位毫秒
     * @param solveNodes 求解器访问节点数
     */
    public record Generated(Board board, CycleSolver.Result result, long seed, int attempts,
                            long elapsedMillis, long solveNodes) {
    }

    /**
     * 保存单个难度的生成阈值。
     *
     * @param minBlacks 目标黑格数量下限
     * @param maxBlacks 目标黑格数量上限
     * @param minWhites 可接受白格数量下限
     * @param maxWhites 可接受白格数量上限
     * @param minRowColumnRuns 行列白格片段数量下限
     * @param maxRepeatedRows 相邻重复行数量上限
     * @param minJunctions 分支白格数量下限
     * @param minFullBlocks 全白二乘二块数量下限
     * @param maxForcedPercent 二度白格比例上限
     * @param lowRiskChecks 每步低风险扩张候选检查上限
     * @param minFinalSolveNodes 最终求解节点数下限
     * @param minAcceptanceAttempt 允许接受答案的最小尝试次数
     */
    private record DifficultyProfile(int minBlacks, int maxBlacks, int minWhites, int maxWhites,
                                     int minRowColumnRuns, int maxRepeatedRows, int minJunctions,
                                     int minFullBlocks, int maxForcedPercent, int lowRiskChecks,
                                     long minFinalSolveNodes, int minAcceptanceAttempt) {
    }

    /**
     * 表示把答案环上一条边替换成两个新格子的耳扩张。
     *
     * @param edgeIndex 答案环边的位置。
     * @param a 第一个格子或顶点。
     * @param b 第二个格子或顶点。
     */
    private record Ear(int edgeIndex, int a, int b) {
    }

    /** { 对应的内部状态。 */
    public enum Difficulty {
        /** 简单难度，白格较少且生成更快。 */
        EASY("简单"),
        /** 困难难度，白格密度中高并引入低风险扩张。 */
        HARD("困难"),
        /** 地狱难度，白格密度最高且分支更多。 */
        HELL("地狱");

        /** 难度中文显示名称。 */
        private final String label;

        /**
         * 创建生成难度。
         *
         * @param label 中文显示名称
         */
        Difficulty(String label) {
            this.label = label;
        }

        /**
         * 获取难度中文标签。
         *
         * @return 中文标签
         */
        public String label() {
            return label;
        }

        /**
         * 返回适合界面显示的中文名称。
         *
         * @return 中文显示名称。
         */
        @Override
        public String toString() {
            return label;
        }

        /**
         * 根据命令行或界面文本解析难度。
         *
         * @param text 难度名称、中文标签或旧版别名
         * @return 解析出的难度
         */
        public static Difficulty fromText(String text) {
            String normalized = text == null ? "" : text.trim();
            if (normalized.equalsIgnoreCase("normal") || normalized.equals("普通")) {
                return EASY;
            }
            if (normalized.equals("高等")) {
                return HARD;
            }
            for (Difficulty difficulty : values()) {
                if (difficulty.name().equalsIgnoreCase(normalized) || difficulty.label.equals(normalized)) {
                    return difficulty;
                }
            }
            throw new IllegalArgumentException("未知难度：" + text);
        }
    }

    /** 生成器固定棋盘行数。 */
    private static final int ROWS = 10;
    /** 生成器固定棋盘列数。 */
    private static final int COLS = 10;
    /** 最终唯一性验证的搜索节点上限。 */
    private static final long FINAL_SOLVER_LIMIT = 40_000_000L;
    /** 低风险扩张试探的搜索节点上限。 */
    private static final long STEP_SOLVER_LIMIT = 1_000_000L;

    /** 创建 BoardGenerator 实例。 */
    private BoardGenerator() {
    }

    /**
     * 使用默认简单难度生成棋盘。
     *
     * @return 生成结果
     */
    public static Generated generate() {
        return generate(Difficulty.EASY);
    }

    /**
     * 使用指定难度和随机种子生成棋盘。
     *
     * @param difficulty 生成难度
     * @return 生成结果
     */
    public static Generated generate(Difficulty difficulty) {
        return generate(difficulty, System.nanoTime(), defaultMaxAttempts(difficulty));
    }

    /**
     * 使用指定难度和固定种子生成棋盘。
     *
     * @param difficulty 生成难度
     * @param seed 随机种子
     * @return 生成结果
     */
    public static Generated generate(Difficulty difficulty, long seed) {
        return generate(difficulty, seed, defaultMaxAttempts(difficulty));
    }

    /**
     * 使用默认简单难度、固定种子和尝试上限生成棋盘。
     *
     * @param seed 随机种子
     * @param maxAttempts 最大尝试次数
     * @return 生成结果
     */
    public static Generated generate(long seed, int maxAttempts) {
        return generate(Difficulty.EASY, seed, maxAttempts);
    }

    /**
     * 获取指定难度的默认最大尝试次数。
     *
     * @param difficulty 生成难度
     * @return 默认最大尝试次数
     */
    public static int defaultMaxAttempts(Difficulty difficulty) {
        return difficulty == Difficulty.HELL ? 9000 : 5000;
    }

    /**
     * 使用完整参数生成棋盘。
     *
     * @param difficulty 生成难度
     * @param seed 随机种子
     * @param maxAttempts 最大尝试次数
     * @return 生成结果
     */
    public static Generated generate(Difficulty difficulty, long seed, int maxAttempts) {
        long started = System.nanoTime();
        Random random = new Random(seed);
        DifficultyProfile profile = profileFor(difficulty);
        long solveNodes = 0;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            RingBoard ring = RingBoard.randomSeed(random);
            int targetWhites = ROWS * COLS - evenBetween(random, profile.minBlacks(), profile.maxBlacks());

            growForced(ring, targetWhites, random);
            if (difficulty != Difficulty.EASY && ring.whiteCount() < targetWhites) {
                solveNodes += growWithLowRiskSteps(ring, targetWhites, random, profile.lowRiskChecks());
            }

            if (!qualityOk(ring.white, difficulty)) {
                continue;
            }

            Board board = Board.fromCells(ROWS, COLS, ring.white);
            CycleSolver.Result result = CycleSolver.solve(board, FINAL_SOLVER_LIMIT);
            solveNodes += result.nodes();
            if (result.hasUniqueSolution()
                && result.nodes() >= profile.minFinalSolveNodes()
                && attempt >= profile.minAcceptanceAttempt()) {
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                return new Generated(board, result, seed, attempt, elapsedMillis, solveNodes);
            }
        }

        throw new IllegalStateException("没有生成唯一解棋盘，请重试");
    }

    /**
     * 用强制扩张尽量扩展答案环。
     *
     * @param ring 正在生长的答案环状态。
     * @param targetWhites 目标白格数量。
     * @param random 随机数生成器。
     */
    private static void growForced(RingBoard ring, int targetWhites, Random random) {
        while (ring.whiteCount() < targetWhites) {
            List<Ear> ears = ring.collectEars(true);
            if (ears.isEmpty()) {
                return;
            }
            ring.apply(ears.get(random.nextInt(ears.size())));
        }
    }

    /**
     * 使用带局部验证的低风险扩张继续扩展答案环。
     *
     * @param ring 正在生长的答案环状态。
     * @param targetWhites 目标白格数量。
     * @param random 随机数生成器。
     * @param maxChecksPerStep 每一步低风险扩张最多检查的候选数量。
     * @return 低风险扩张消耗的求解节点数。
     */
    private static long growWithLowRiskSteps(RingBoard ring, int targetWhites, Random random, int maxChecksPerStep) {
        long solveNodes = 0;
        while (ring.whiteCount() < targetWhites) {
            List<Ear> ears = ring.collectEars(false);
            Collections.shuffle(ears, random);
            boolean accepted = false;
            int checked = 0;

            for (Ear ear : ears) {
                if (++checked > maxChecksPerStep) {
                    break;
                }

                boolean[] next = ring.copyWith(ear);
                if (!locallyValid(next)) {
                    continue;
                }

                Board nextBoard = Board.fromCells(ROWS, COLS, next);
                CycleSolver.Result result = CycleSolver.solve(nextBoard, STEP_SOLVER_LIMIT);
                solveNodes += result.nodes();
                if (result.hasUniqueSolution()) {
                    ring.white = next;
                    ring.setPath(result.path());
                    accepted = true;
                    break;
                }
            }

            if (!accepted) {
                break;
            }
        }
        return solveNodes;
    }

    /**
     * 返回指定难度的生成参数。
     *
     * @param difficulty 生成难度。
     * @return 难度生成参数。
     */
    private static DifficultyProfile profileFor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> new DifficultyProfile(28, 42, 58, 72, 30, 4, 18, 2, 90, 0, 0L, 1);
            case HARD -> new DifficultyProfile(18, 26, 74, 82, 28, 4, 48, 18, 80, 70, 0L, 1);
            case HELL -> new DifficultyProfile(10, 16, 84, 90, 26, 4, 60, 30, 78, 80, 0L, 200);
        };
    }

    /**
     * 在闭区间中生成随机偶数。
     *
     * @param random 随机数生成器。
     * @param minInclusive 闭区间最小值。
     * @param maxInclusive 闭区间最大值。
     * @return 随机偶数。
     */
    private static int evenBetween(Random random, int minInclusive, int maxInclusive) {
        int min = minInclusive + (minInclusive & 1);
        int max = maxInclusive - (maxInclusive & 1);
        return min + random.nextInt((max - min) / 2 + 1) * 2;
    }

    /**
     * 检查候选棋盘是否满足难度质量要求。
     *
     * @param cells 格子状态或路径列表。
     * @param difficulty 生成难度。
     * @return 如果候选棋盘满足质量要求则返回 true。
     */
    private static boolean qualityOk(boolean[] cells, Difficulty difficulty) {
        DifficultyProfile profile = profileFor(difficulty);
        int whites = 0;
        int junctions = 0;
        int forced = 0;
        int full2x2 = 0;
        int rowRuns = 0;
        int colRuns = 0;
        int repeatedAdjacentRows = 0;
        int blackRows = 0;
        int blackCols = 0;

        for (int i = 0; i < cells.length; i++) {
            if (!cells[i]) {
                continue;
            }
            whites++;
            int deg = degree(cells, i);
            if (deg < 2) {
                return false;
            }
            if (deg == 2) {
                forced++;
            }
            if (deg >= 3) {
                junctions++;
            }
        }

        for (int r = 0; r + 1 < ROWS; r++) {
            for (int c = 0; c + 1 < COLS; c++) {
                if (cells[index(r, c)] && cells[index(r + 1, c)]
                    && cells[index(r, c + 1)] && cells[index(r + 1, c + 1)]) {
                    full2x2++;
                }
            }
        }

        for (int r = 0; r < ROWS; r++) {
            boolean inRun = false;
            boolean hasBlack = false;
            for (int c = 0; c < COLS; c++) {
                if (cells[index(r, c)]) {
                    if (!inRun) {
                        rowRuns++;
                    }
                    inRun = true;
                } else {
                    inRun = false;
                    hasBlack = true;
                }
            }
            if (hasBlack) {
                blackRows++;
            }
        }

        for (int c = 0; c < COLS; c++) {
            boolean inRun = false;
            boolean hasBlack = false;
            for (int r = 0; r < ROWS; r++) {
                if (cells[index(r, c)]) {
                    if (!inRun) {
                        colRuns++;
                    }
                    inRun = true;
                } else {
                    inRun = false;
                    hasBlack = true;
                }
            }
            if (hasBlack) {
                blackCols++;
            }
        }

        for (int r = 0; r + 1 < ROWS; r++) {
            boolean same = true;
            for (int c = 0; c < COLS; c++) {
                if (cells[index(r, c)] != cells[index(r + 1, c)]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                repeatedAdjacentRows++;
            }
        }

        boolean baseQuality = whites >= 12
            && (whites & 1) == 0
            && junctions >= profile.minJunctions()
            && forced * 100 <= whites * profile.maxForcedPercent()
            && full2x2 >= profile.minFullBlocks()
            && rowRuns + colRuns >= profile.minRowColumnRuns()
            && repeatedAdjacentRows <= profile.maxRepeatedRows()
            && blackRows >= 4
            && blackCols >= 4
            && isWhiteConnected(cells);

        return baseQuality && whites >= profile.minWhites() && whites <= profile.maxWhites();
    }

    /**
     * 检查低风险扩张后的局部合法性。
     *
     * @param cells 格子状态或路径列表。
     * @return 如果局部状态合法则返回 true。
     */
    private static boolean locallyValid(boolean[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] && degree(cells, i) < 2) {
                return false;
            }
        }
        return isWhiteConnected(cells);
    }

    /**
     * 检查所有白格是否连通。
     *
     * @param cells 格子状态或路径列表。
     * @return 如果所有白格连通则返回 true。
     */
    private static boolean isWhiteConnected(boolean[] cells) {
        int start = -1;
        int whites = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i]) {
                whites++;
                if (start == -1) {
                    start = i;
                }
            }
        }
        if (start == -1) {
            return false;
        }

        boolean[] seen = new boolean[cells.length];
        int[] stack = new int[cells.length];
        int stackSize = 0;
        int reached = 0;
        seen[start] = true;
        stack[stackSize++] = start;
        while (stackSize > 0) {
            int cell = stack[--stackSize];
            reached++;
            int row = cell / COLS;
            int col = cell % COLS;
            stackSize = pushIfWhite(cells, seen, stack, stackSize, row - 1, col);
            stackSize = pushIfWhite(cells, seen, stack, stackSize, row + 1, col);
            stackSize = pushIfWhite(cells, seen, stack, stackSize, row, col - 1);
            stackSize = pushIfWhite(cells, seen, stack, stackSize, row, col + 1);
        }
        return reached == whites;
    }

    /**
     * 在连通性搜索中压入未访问白格。
     *
     * @param cells 格子状态或路径列表。
     * @param seen 访问标记数组。
     * @param stack 搜索栈。
     * @param stackSize 当前栈大小。
     * @param row 行号。
     * @param col 列号。
     * @return 更新后的栈大小。
     */
    private static int pushIfWhite(boolean[] cells, boolean[] seen, int[] stack, int stackSize, int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return stackSize;
        }
        int cell = index(row, col);
        if (!cells[cell] || seen[cell]) {
            return stackSize;
        }
        seen[cell] = true;
        stack[stackSize++] = cell;
        return stackSize;
    }

    /**
     * 计算格子的白格邻居数量。
     *
     * @param cells 格子状态或路径列表。
     * @param cell 格子编号。
     * @return 白格邻居数量。
     */
    private static int degree(boolean[] cells, int cell) {
        int deg = 0;
        int row = cell / COLS;
        int col = cell % COLS;
        if (row > 0 && cells[index(row - 1, col)]) {
            deg++;
        }
        if (row + 1 < ROWS && cells[index(row + 1, col)]) {
            deg++;
        }
        if (col > 0 && cells[index(row, col - 1)]) {
            deg++;
        }
        if (col + 1 < COLS && cells[index(row, col + 1)]) {
            deg++;
        }
        return deg;
    }

    /**
     * 将行列坐标转换为一维数组下标。
     *
     * @param row 行号。
     * @param col 列号。
     * @return 一维数组下标。
     */
    private static int index(int row, int col) {
        return row * COLS + col;
    }

    /** { 对应的内部状态。 */
    private static final class RingBoard {
        /** 按行优先保存白格状态。 */
        private boolean[] white;
        /** 当前已知答案环路径。 */
        private final ArrayList<Integer> path = new ArrayList<>();
        /** 格子到答案环位置的映射。 */
        private final int[] pathIndex = new int[ROWS * COLS];

        /**
         * 创建 RingBoard 实例。
         *
         * @param startRow 初始二乘二白块左上角行号。
         * @param startCol 初始二乘二白块左上角列号。
         */
        private RingBoard(int startRow, int startCol) {
            white = new boolean[ROWS * COLS];
            int a = index(startRow, startCol);
            int b = index(startRow, startCol + 1);
            int c = index(startRow + 1, startCol + 1);
            int d = index(startRow + 1, startCol);
            white[a] = white[b] = white[c] = white[d] = true;
            path.add(a);
            path.add(b);
            path.add(c);
            path.add(d);
            rebuildPathIndex();
        }

        /**
         * 随机创建初始二乘二白格环。
         *
         * @param random 随机数生成器。
         * @return 初始答案环状态。
         */
        private static RingBoard randomSeed(Random random) {
            return new RingBoard(random.nextInt(ROWS - 1), random.nextInt(COLS - 1));
        }

        /**
         * 返回当前环上的白格数量。
         *
         * @return 白格数量。
         */
        private int whiteCount() {
            return path.size();
        }

        /**
         * 收集当前答案环可用的耳扩张候选。
         *
         * @param forcedOnly forcedOnly 参数。
         * @return 耳扩张候选列表。
         */
        private List<Ear> collectEars(boolean forcedOnly) {
            ArrayList<Ear> ears = new ArrayList<>();
            for (int i = 0; i < path.size(); i++) {
                int a = path.get(i);
                int b = path.get((i + 1) % path.size());
                addEarsForEdge(ears, i, a, b, forcedOnly);
            }
            return ears;
        }

        /**
         * 为答案环上的一条边收集耳扩张。
         *
         * @param ears 耳扩张候选列表。
         * @param edgeIndex 答案环边的位置。
         * @param a 第一个格子或顶点。
         * @param b 第二个格子或顶点。
         * @param forcedOnly forcedOnly 参数。
         */
        private void addEarsForEdge(List<Ear> ears, int edgeIndex, int a, int b, boolean forcedOnly) {
            int ar = a / COLS;
            int ac = a % COLS;
            int br = b / COLS;
            int bc = b % COLS;

            if (ar == br) {
                addEar(ears, edgeIndex, ar - 1, ac, ar - 1, bc, forcedOnly);
                addEar(ears, edgeIndex, ar + 1, ac, ar + 1, bc, forcedOnly);
            } else if (ac == bc) {
                addEar(ears, edgeIndex, ar, ac - 1, br, ac - 1, forcedOnly);
                addEar(ears, edgeIndex, ar, ac + 1, br, ac + 1, forcedOnly);
            }
        }

        /**
         * 尝试加入一个耳扩张候选。
         *
         * @param ears 耳扩张候选列表。
         * @param edgeIndex 答案环边的位置。
         * @param ar 第一个新格子的行号。
         * @param ac 第一个新格子的列号。
         * @param br 第二个新格子的行号。
         * @param bc 第二个新格子的列号。
         * @param forcedOnly forcedOnly 参数。
         */
        private void addEar(List<Ear> ears, int edgeIndex, int ar, int ac, int br, int bc, boolean forcedOnly) {
            if (ar < 0 || ar >= ROWS || ac < 0 || ac >= COLS
                || br < 0 || br >= ROWS || bc < 0 || bc >= COLS) {
                return;
            }

            int a = index(ar, ac);
            int b = index(br, bc);
            if (a == b || white[a] || white[b]) {
                return;
            }

            boolean[] next = white.clone();
            next[a] = true;
            next[b] = true;
            int da = degree(next, a);
            int db = degree(next, b);
            if ((forcedOnly && da == 2 && db == 2) || (!forcedOnly && da <= 3 && db <= 3)) {
                ears.add(new Ear(edgeIndex, a, b));
            }
        }

        /**
         * 复制白格状态并加入指定耳扩张。
         *
         * @param ear 耳扩张候选。
         * @return 加入候选后的白格状态副本。
         */
        private boolean[] copyWith(Ear ear) {
            boolean[] next = white.clone();
            next[ear.a()] = true;
            next[ear.b()] = true;
            return next;
        }

        /**
         * 把耳扩张应用到当前环。
         *
         * @param ear 耳扩张候选。
         */
        private void apply(Ear ear) {
            white[ear.a()] = true;
            white[ear.b()] = true;
            insertPathCells(ear);
        }

        /**
         * 用求解器返回的路径替换当前答案环。
         *
         * @param cells 格子状态或路径列表。
         */
        private void setPath(List<Integer> cells) {
            path.clear();
            path.addAll(cells);
            rebuildPathIndex();
        }

        /**
         * 把耳扩张的新格子插入答案环路径。
         *
         * @param ear 耳扩张候选。
         */
        private void insertPathCells(Ear ear) {
            int insertAt = ear.edgeIndex() + 1;
            if (insertAt >= path.size()) {
                path.add(ear.a());
                path.add(ear.b());
            } else {
                path.add(insertAt, ear.a());
                path.add(insertAt + 1, ear.b());
            }
            rebuildPathIndex();
        }

        /** 重建格子到答案环位置的映射。 */
        private void rebuildPathIndex() {
            for (int i = 0; i < pathIndex.length; i++) {
                pathIndex[i] = -1;
            }
            for (int i = 0; i < path.size(); i++) {
                pathIndex[path.get(i)] = i;
            }
        }
    }
}
