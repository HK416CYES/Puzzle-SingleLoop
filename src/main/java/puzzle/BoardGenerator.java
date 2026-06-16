package puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class BoardGenerator {
    public record Generated(Board board, CycleSolver.Result result, long seed, int attempts) {
    }

    private record DifficultyProfile(int minBlacks, int maxBlacks, int minRowColumnRuns, int maxRepeatedRows,
                                     int minJunctions, int minFullBlocks) {
    }

    private record Ear(int a, int b) {
    }

    public enum Difficulty {
        NORMAL("普通"),
        HARD("高等");

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
            for (Difficulty difficulty : values()) {
                if (difficulty.name().equalsIgnoreCase(text) || difficulty.label.equals(text)) {
                    return difficulty;
                }
            }
            throw new IllegalArgumentException("未知难度：" + text);
        }
    }

    private static final int ROWS = 10;
    private static final int COLS = 10;
    private static final long GENERATOR_SOLVER_LIMIT = 5_000_000L;

    private BoardGenerator() {
    }

    public static Generated generate() {
        return generate(Difficulty.NORMAL);
    }

    public static Generated generate(Difficulty difficulty) {
        long seed = System.nanoTime();
        return generate(difficulty, seed, 5000);
    }

    public static Generated generate(long seed, int maxAttempts) {
        return generate(Difficulty.NORMAL, seed, maxAttempts);
    }

    public static Generated generate(Difficulty difficulty, long seed, int maxAttempts) {
        if (difficulty == Difficulty.HARD) {
            return generateHard(seed, maxAttempts);
        }

        Random random = new Random(seed);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean[] cells = buildCandidate(difficulty, random);
            if (!qualityOk(cells, difficulty)) {
                continue;
            }

            Board board = Board.fromCells(ROWS, COLS, cells);
            CycleSolver.Result result = CycleSolver.solve(board, GENERATOR_SOLVER_LIMIT);
            if (result.hasUniqueSolution()) {
                return new Generated(board, result, seed, attempt);
            }
        }
        throw new IllegalStateException("没有生成唯一解棋盘，请重试");
    }

    private static Generated generateHard(long seed, int maxAttempts) {
        Random random = new Random(seed);
        DifficultyProfile hardProfile = profileFor(Difficulty.HARD);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean[] cells = buildCandidate(Difficulty.NORMAL, random);
            if (!qualityOk(cells, Difficulty.NORMAL)) {
                continue;
            }

            Board board = Board.fromCells(ROWS, COLS, cells);
            CycleSolver.Result result = CycleSolver.solve(board, GENERATOR_SOLVER_LIMIT);
            if (!result.hasUniqueSolution()) {
                continue;
            }

            int targetWhites = ROWS * COLS - evenBetween(random, hardProfile.minBlacks(), hardProfile.maxBlacks());
            while (whiteCount(cells) < targetWhites) {
                List<Ear> ears = collectExpansionEars(cells, false);
                Collections.shuffle(ears, random);
                boolean accepted = false;
                int checked = 0;
                for (Ear ear : ears) {
                    if (++checked > 80) {
                        break;
                    }
                    boolean[] next = cells.clone();
                    next[ear.a()] = true;
                    next[ear.b()] = true;
                    if (!locallyValid(next)) {
                        continue;
                    }

                    Board nextBoard = Board.fromCells(ROWS, COLS, next);
                    CycleSolver.Result nextResult = CycleSolver.solve(nextBoard, GENERATOR_SOLVER_LIMIT);
                    if (nextResult.hasUniqueSolution()) {
                        cells = next;
                        result = nextResult;
                        accepted = true;
                        break;
                    }
                }

                if (!accepted) {
                    break;
                }
            }

            if (qualityOk(cells, Difficulty.HARD)) {
                return new Generated(Board.fromCells(ROWS, COLS, cells), result, seed, attempt);
            }
        }

        throw new IllegalStateException("没有生成唯一解棋盘，请重试");
    }

    private static boolean[] buildCandidate(Difficulty difficulty, Random random) {
        boolean[] cells = new boolean[ROWS * COLS];
        DifficultyProfile profile = profileFor(difficulty);
        int targetWhites = ROWS * COLS - evenBetween(random, profile.minBlacks(), profile.maxBlacks());
        seedRandomBlock(cells, random);

        while (whiteCount(cells) < targetWhites) {
            List<Ear> ears = collectExpansionEars(cells, true);
            if (ears.isEmpty() && difficulty == Difficulty.HARD) {
                ears = collectExpansionEars(cells, false);
            }
            if (ears.isEmpty()) {
                break;
            }
            Ear ear = ears.get(random.nextInt(ears.size()));
            cells[ear.a()] = true;
            cells[ear.b()] = true;
        }

        return cells;
    }

    private static DifficultyProfile profileFor(Difficulty difficulty) {
        return switch (difficulty) {
            case NORMAL -> new DifficultyProfile(24, 34, 34, 2, 34, 8);
            case HARD -> new DifficultyProfile(10, 16, 26, 4, 60, 30);
        };
    }

    private static int evenBetween(Random random, int minInclusive, int maxInclusive) {
        int min = minInclusive + (minInclusive & 1);
        int max = maxInclusive - (maxInclusive & 1);
        return min + random.nextInt((max - min) / 2 + 1) * 2;
    }

    private static void seedRandomBlock(boolean[] cells, Random random) {
        int r = random.nextInt(ROWS - 1);
        int c = random.nextInt(COLS - 1);
        cells[index(r, c)] = true;
        cells[index(r + 1, c)] = true;
        cells[index(r, c + 1)] = true;
        cells[index(r + 1, c + 1)] = true;
    }

    private static List<Ear> collectExpansionEars(boolean[] cells, boolean forcedOnly) {
        ArrayList<Ear> ears = new ArrayList<>();

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c + 1 < COLS; c++) {
                int left = index(r, c);
                int right = index(r, c + 1);
                if (!cells[left] || !cells[right]) {
                    continue;
                }
                addHorizontalEar(cells, ears, r - 1, c, forcedOnly);
                addHorizontalEar(cells, ears, r + 1, c, forcedOnly);
            }
        }

        for (int r = 0; r + 1 < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int top = index(r, c);
                int bottom = index(r + 1, c);
                if (!cells[top] || !cells[bottom]) {
                    continue;
                }
                addVerticalEar(cells, ears, r, c - 1, forcedOnly);
                addVerticalEar(cells, ears, r, c + 1, forcedOnly);
            }
        }

        return ears;
    }

    private static void addHorizontalEar(boolean[] cells, List<Ear> ears, int row, int col, boolean forcedOnly) {
        if (row < 0 || row >= ROWS || col < 0 || col + 1 >= COLS) {
            return;
        }
        int a = index(row, col);
        int b = index(row, col + 1);
        if (!cells[a] && !cells[b] && acceptableAfterAdd(cells, a, b, forcedOnly)) {
            ears.add(new Ear(a, b));
        }
    }

    private static void addVerticalEar(boolean[] cells, List<Ear> ears, int row, int col, boolean forcedOnly) {
        if (row < 0 || row + 1 >= ROWS || col < 0 || col >= COLS) {
            return;
        }
        int a = index(row, col);
        int b = index(row + 1, col);
        if (!cells[a] && !cells[b] && acceptableAfterAdd(cells, a, b, forcedOnly)) {
            ears.add(new Ear(a, b));
        }
    }

    private static boolean acceptableAfterAdd(boolean[] cells, int a, int b, boolean forcedOnly) {
        boolean[] next = cells.clone();
        next[a] = true;
        next[b] = true;
        int da = degree(next, a);
        int db = degree(next, b);
        if (forcedOnly) {
            return da == 2 && db == 2;
        }
        return da <= 3 && db <= 3;
    }

    private static int whiteCount(boolean[] cells) {
        int count = 0;
        for (boolean cell : cells) {
            if (cell) {
                count++;
            }
        }
        return count;
    }

    private static List<Integer> whiteNeighbors(boolean[] cells, int cell) {
        ArrayList<Integer> result = new ArrayList<>(4);
        int row = cell / COLS;
        int col = cell % COLS;
        addWhiteNeighbor(cells, result, row - 1, col);
        addWhiteNeighbor(cells, result, row + 1, col);
        addWhiteNeighbor(cells, result, row, col - 1);
        addWhiteNeighbor(cells, result, row, col + 1);
        return result;
    }

    private static void addWhiteNeighbor(boolean[] cells, List<Integer> result, int row, int col) {
        if (row >= 0 && row < ROWS && col >= 0 && col < COLS && cells[index(row, col)]) {
            result.add(index(row, col));
        }
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
            for (int c = 0; c < COLS; c++) {
                if (cells[index(r, c)]) {
                    if (!inRun) {
                        rowRuns++;
                    }
                    inRun = true;
                } else {
                    inRun = false;
                }
            }
        }

        for (int c = 0; c < COLS; c++) {
            boolean inRun = false;
            for (int r = 0; r < ROWS; r++) {
                if (cells[index(r, c)]) {
                    if (!inRun) {
                        colRuns++;
                    }
                    inRun = true;
                } else {
                    inRun = false;
                }
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

        for (int r = 0; r < ROWS; r++) {
            boolean hasBlack = false;
            for (int c = 0; c < COLS; c++) {
                if (!cells[index(r, c)]) {
                    hasBlack = true;
                    break;
                }
            }
            if (hasBlack) {
                blackRows++;
            }
        }

        for (int c = 0; c < COLS; c++) {
            boolean hasBlack = false;
            for (int r = 0; r < ROWS; r++) {
                if (!cells[index(r, c)]) {
                    hasBlack = true;
                    break;
                }
            }
            if (hasBlack) {
                blackCols++;
            }
        }

        if (!isWhiteConnected(cells)) {
            return false;
        }

        boolean baseQuality = whites >= 12
            && (whites & 1) == 0
            && junctions >= profile.minJunctions()
            && forced * 100 <= whites * 78
            && full2x2 >= profile.minFullBlocks()
            && rowRuns + colRuns >= profile.minRowColumnRuns()
            && repeatedAdjacentRows <= profile.maxRepeatedRows()
            && blackRows >= 4
            && blackCols >= 4;

        if (difficulty == Difficulty.HARD) {
            return baseQuality && whites >= 84 && whites <= 90;
        }
        return baseQuality && whites >= 66 && whites <= 76;
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
        ArrayList<Integer> stack = new ArrayList<>();
        stack.add(start);
        seen[start] = true;
        int reached = 0;
        while (!stack.isEmpty()) {
            int cell = stack.remove(stack.size() - 1);
            reached++;
            for (int next : whiteNeighbors(cells, cell)) {
                if (!seen[next]) {
                    seen[next] = true;
                    stack.add(next);
                }
            }
        }
        return reached == whites;
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
}
