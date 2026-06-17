package puzzle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AnswerChecker {
    private AnswerChecker() {
    }

    static boolean matches(Board board, List<Integer> playerPath, boolean playerClosed, List<Integer> answerPath) {
        if (!playerClosed || answerPath == null || answerPath.isEmpty() || playerPath.size() != answerPath.size()) {
            return false;
        }
        if (playerPath.size() != board.whiteCount()) {
            return false;
        }
        if (!containsEachWhiteOnce(board, playerPath)) {
            return false;
        }
        return sameCycle(playerPath, answerPath);
    }

    private static boolean containsEachWhiteOnce(Board board, List<Integer> path) {
        Set<Integer> seen = new HashSet<>();
        for (int cell : path) {
            if (cell < 0 || cell >= board.rows() * board.cols()) {
                return false;
            }
            if (!board.isWhite(board.rowOf(cell), board.colOf(cell)) || !seen.add(cell)) {
                return false;
            }
        }
        return seen.size() == board.whiteCount();
    }

    private static boolean sameCycle(List<Integer> playerPath, List<Integer> answerPath) {
        int size = answerPath.size();
        int first = playerPath.get(0);
        for (int offset = 0; offset < size; offset++) {
            if (answerPath.get(offset) != first) {
                continue;
            }
            if (matchesDirection(playerPath, answerPath, offset, 1)
                || matchesDirection(playerPath, answerPath, offset, -1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesDirection(List<Integer> playerPath, List<Integer> answerPath, int offset, int direction) {
        int size = answerPath.size();
        for (int i = 0; i < size; i++) {
            int answerIndex = Math.floorMod(offset + i * direction, size);
            if (!playerPath.get(i).equals(answerPath.get(answerIndex))) {
                return false;
            }
        }
        return true;
    }
}
