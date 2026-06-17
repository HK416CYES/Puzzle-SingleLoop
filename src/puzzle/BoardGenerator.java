package puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class BoardGenerator {
    public record Generated(Board board, CycleSolver.Result result, long seed, int attempts,
                            long elapsedMillis, long solveNodes) {
    }

    private record DifficultyProfile(int minBlacks, int maxBlacks, int minRowColumnRuns, int maxRepeatedRows,
                                     int minJunctions, int minFullBlocks, int lowRiskChecks) {
    }

    private record Ear(int edgeIndex, int a, int b) {
    }

    public enum Difficulty {
        EASY("简单"),
        HARD("困难"),
        HELL("地狱");

        private final String label;

        Difficulty(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }

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

    private static final int ROWS = 10;
    private static final int COLS = 10;
    private static final long FINAL_SOLVER_LIMIT = 40_000_000L;
    private static final long STEP_SOLVER_LIMIT = 1_000_000L;

    private BoardGenerator() {
    }

    public static Generated generate() {
        return generate(Difficulty.EASY);
    }

    public static Generated generate(Difficulty difficulty) {
        return generate(difficulty, System.nanoTime(), defaultMaxAttempts(difficulty));
    }

    public static Generated generate(Difficulty difficulty, long seed) {
        return generate(difficulty, seed, defaultMaxAttempts(difficulty));
    }

    public static Generated generate(long seed, int maxAttempts) {
        return generate(Difficulty.EASY, seed, maxAttempts);
    }

    public static int defaultMaxAttempts(Difficulty difficulty) {
        return difficulty == Difficulty.HELL ? 9000 : 5000;
    }

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
            if (result.hasUniqueSolution()) {
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                return new Generated(board, result, seed, attempt, elapsedMillis, solveNodes);
            }
        }

        throw new IllegalStateException("没有生成唯一解棋盘，请重试");
    }

    private static void growForced(RingBoard ring, int targetWhites, Random random) {
        while (ring.whiteCount() < targetWhites) {
            List<Ear> ears = ring.collectEars(true);
            if (ears.isEmpty()) {
                return;
            }
            ring.apply(ears.get(random.nextInt(ears.size())));
        }
    }

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

    private static DifficultyProfile profileFor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> new DifficultyProfile(26, 38, 32, 3, 28, 6, 0);
            case HARD -> new DifficultyProfile(16, 24, 28, 4, 48, 18, 60);
            case HELL -> new DifficultyProfile(10, 16, 26, 4, 60, 30, 80);
        };
    }

    private static int evenBetween(Random random, int minInclusive, int maxInclusive) {
        int min = minInclusive + (minInclusive & 1);
        int max = maxInclusive - (maxInclusive & 1);
        return min + random.nextInt((max - min) / 2 + 1) * 2;
    }

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
            && forced * 100 <= whites * 78
            && full2x2 >= profile.minFullBlocks()
            && rowRuns + colRuns >= profile.minRowColumnRuns()
            && repeatedAdjacentRows <= profile.maxRepeatedRows()
            && blackRows >= 4
            && blackCols >= 4
            && isWhiteConnected(cells);

        return switch (difficulty) {
            case EASY -> baseQuality && whites >= 62 && whites <= 74;
            case HARD -> baseQuality && whites >= 76 && whites <= 84;
            case HELL -> baseQuality && whites >= 84 && whites <= 90;
        };
    }

    private static boolean locallyValid(boolean[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] && degree(cells, i) < 2) {
                return false;
            }
        }
        return isWhiteConnected(cells);
    }

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

    private static int index(int row, int col) {
        return row * COLS + col;
    }

    private static final class RingBoard {
        private boolean[] white;
        private final ArrayList<Integer> path = new ArrayList<>();
        private final int[] pathIndex = new int[ROWS * COLS];

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

        private static RingBoard randomSeed(Random random) {
            return new RingBoard(random.nextInt(ROWS - 1), random.nextInt(COLS - 1));
        }

        private int whiteCount() {
            return path.size();
        }

        private List<Ear> collectEars(boolean forcedOnly) {
            ArrayList<Ear> ears = new ArrayList<>();
            for (int i = 0; i < path.size(); i++) {
                int a = path.get(i);
                int b = path.get((i + 1) % path.size());
                addEarsForEdge(ears, i, a, b, forcedOnly);
            }
            return ears;
        }

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

        private boolean[] copyWith(Ear ear) {
            boolean[] next = white.clone();
            next[ear.a()] = true;
            next[ear.b()] = true;
            return next;
        }

        private void apply(Ear ear) {
            white[ear.a()] = true;
            white[ear.b()] = true;
            insertPathCells(ear);
        }

        private void setPath(List<Integer> cells) {
            path.clear();
            path.addAll(cells);
            rebuildPathIndex();
        }

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
