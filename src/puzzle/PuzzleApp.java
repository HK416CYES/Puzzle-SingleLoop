package puzzle;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PuzzleApp {
    private static final long SOLVER_NODE_LIMIT = 40_000_000L;

    private final JFrame frame = new JFrame("Black White Cycle Puzzle");
    private final BoardPanel boardPanel = new BoardPanel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JToggleButton answerButton = new JToggleButton("显示答案");
    private final JButton generateButton = new JButton("随机生成");
    private final JButton saveButton = new JButton("保存棋盘");
    private final JComboBox<BoardGenerator.Difficulty> difficultyBox =
        new JComboBox<>(BoardGenerator.Difficulty.values());
    private Board board;
    private boolean autoGenerateOnShow;
    private CycleSolver.Result currentResult = new CycleSolver.Result(0, false, List.of(), 0);

    public static void main(String[] args) {
        if (args.length > 0 && "--check".equals(args[0])) {
            runCliCheck(args);
            return;
        }
        if (args.length > 0 && "--generate".equals(args[0])) {
            runCliGenerate(args);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            Board initialBoard = loadInitialBoard(args);
            new PuzzleApp(initialBoard).show();
        });
    }

    private static void runCliCheck(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp out puzzle.PuzzleApp --check board.txt");
            System.exit(1);
        }
        try {
            Board board = Board.fromLines(Files.readAllLines(Path.of(args[1])));
            CycleSolver.Result result = CycleSolver.solve(board, SOLVER_NODE_LIMIT);
            if (result.aborted()) {
                System.out.println("unknown");
            } else {
                System.out.println(result.solutionCount());
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
    }

    private static void runCliGenerate(String[] args) {
        BoardGenerator.Difficulty difficulty = args.length >= 2
            ? BoardGenerator.Difficulty.fromText(args[1])
            : BoardGenerator.Difficulty.EASY;
        BoardGenerator.Generated generated = args.length >= 3
            ? BoardGenerator.generate(difficulty, Long.parseLong(args[2]))
            : BoardGenerator.generate(difficulty);
        Board board = generated.board();
        System.err.println("difficulty=" + difficulty.name().toLowerCase()
            + " seed=" + generated.seed()
            + " attempts=" + generated.attempts()
            + " elapsedMs=" + generated.elapsedMillis()
            + " solveNodes=" + generated.solveNodes());
        for (String line : board.toLines()) {
            System.out.println(line);
        }
    }

    private static Board loadInitialBoard(String[] args) {
        if (args.length == 0) {
            return null;
        }
        try {
            return Board.fromLines(Files.readAllLines(Path.of(args[0])));
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                null,
                "无法加载棋盘文件：\n" + ex.getMessage(),
                "加载失败",
                JOptionPane.ERROR_MESSAGE
            );
            return null;
        }
    }

    private PuzzleApp(Board initialBoard) {
        configureFrame();
        if (initialBoard == null) {
            showEmptyBoard();
            autoGenerateOnShow = true;
        } else {
            loadBoard(initialBoard);
        }
    }

    private void configureFrame() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(createToolbar(), BorderLayout.NORTH);
        frame.add(boardPanel, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    private JPanel createToolbar() {
        JButton openButton = new JButton("加载棋盘");

        answerButton.setEnabled(false);
        saveButton.setEnabled(false);
        answerButton.addActionListener(event -> {
            boardPanel.setShowAnswer(answerButton.isSelected());
            answerButton.setText(answerButton.isSelected() ? "隐藏答案" : "显示答案");
        });
        openButton.addActionListener(event -> openBoardFile());
        saveButton.addActionListener(event -> saveBoardFile());
        generateButton.addActionListener(event -> generateRandomBoard());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        toolbar.add(difficultyBox);
        toolbar.add(generateButton);
        toolbar.add(openButton);
        toolbar.add(saveButton);
        toolbar.add(answerButton);
        return toolbar;
    }

    private void show() {
        frame.setVisible(true);
        if (autoGenerateOnShow) {
            autoGenerateOnShow = false;
            generateRandomBoard();
        }
    }

    private void showEmptyBoard() {
        board = null;
        currentResult = new CycleSolver.Result(0, false, List.of(), 0);
        boardPanel.setBoard(Board.empty(10, 10));
        boardPanel.setSolutionPath(List.of());
        boardPanel.setShowAnswer(false);
        boardPanel.setOverlayText("随机生成或加载棋盘");
        answerButton.setSelected(false);
        answerButton.setText("显示答案");
        answerButton.setEnabled(false);
        saveButton.setEnabled(false);
        statusLabel.setText("正在准备自动生成棋盘...");
    }

    private void openBoardFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text board files", "txt", "board"));
        int result = chooser.showOpenDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            Board loaded = Board.fromLines(Files.readAllLines(chooser.getSelectedFile().toPath()));
            loadBoard(loaded);
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "加载失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveBoardFile() {
        if (board == null) {
            JOptionPane.showMessageDialog(frame, "当前没有可保存的棋盘", "保存失败", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text board files", "txt", "board"));
        int result = chooser.showSaveDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path path = withDefaultTxtExtension(chooser.getSelectedFile().toPath());
        if (Files.exists(path)) {
            int overwrite = JOptionPane.showConfirmDialog(
                frame,
                "文件已存在，是否覆盖？",
                "确认保存",
                JOptionPane.YES_NO_OPTION
            );
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            Files.write(path, board.toLines());
            statusLabel.setText("棋盘已保存到 " + path.getFileName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "保存失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path withDefaultTxtExtension(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.contains(".")) {
            return path;
        }
        return path.resolveSibling(fileName + ".txt");
    }

    private void generateRandomBoard() {
        BoardGenerator.Difficulty difficulty = selectedDifficulty();
        answerButton.setSelected(false);
        answerButton.setText("显示答案");
        answerButton.setEnabled(false);
        saveButton.setEnabled(false);
        generateButton.setEnabled(false);
        difficultyBox.setEnabled(false);
        boardPanel.setShowAnswer(false);
        boardPanel.setOverlayText("正在生成");
        statusLabel.setText("正在随机生成" + difficulty.label() + "难度唯一解棋盘...");

        SwingWorker<BoardGenerator.Generated, Void> worker = new SwingWorker<>() {
            @Override
            protected BoardGenerator.Generated doInBackground() {
                return BoardGenerator.generate(difficulty);
            }

            @Override
            protected void done() {
                generateButton.setEnabled(true);
                difficultyBox.setEnabled(true);
                try {
                    BoardGenerator.Generated generated = get();
                    board = generated.board();
                    currentResult = generated.result();
                    boardPanel.setBoard(board);
                    boardPanel.setSolutionPath(currentResult.path());
                    boardPanel.setOverlayText("");
                    statusLabel.setText(
                        difficulty.label() + "难度棋盘已生成，seed=" + generated.seed()
                            + "，尝试 " + generated.attempts()
                            + " 次，用时 " + generated.elapsedMillis()
                            + "ms，白格 " + board.whiteCount() + " 个"
                    );
                    answerButton.setEnabled(true);
                    saveButton.setEnabled(true);
                } catch (Exception ex) {
                    boardPanel.setOverlayText("");
                    statusLabel.setText("随机生成失败：" + ex.getMessage());
                    answerButton.setEnabled(false);
                    saveButton.setEnabled(board != null);
                }
            }
        };
        worker.execute();
    }

    private BoardGenerator.Difficulty selectedDifficulty() {
        Object selected = difficultyBox.getSelectedItem();
        if (selected instanceof BoardGenerator.Difficulty difficulty) {
            return difficulty;
        }
        return BoardGenerator.Difficulty.EASY;
    }

    private void loadBoard(Board newBoard) {
        generateButton.setEnabled(true);
        difficultyBox.setEnabled(true);
        this.board = newBoard;
        this.currentResult = new CycleSolver.Result(0, false, List.of(), 0);
        boardPanel.setBoard(newBoard);
        answerButton.setSelected(false);
        answerButton.setText("显示答案");
        answerButton.setEnabled(false);
        saveButton.setEnabled(true);
        statusLabel.setText("正在求解 " + board.rows() + "x" + board.cols() + "，白格 " + board.whiteCount() + " 个...");
        boardPanel.setOverlayText("正在求解");

        SwingWorker<CycleSolver.Result, Void> worker = new SwingWorker<>() {
            @Override
            protected CycleSolver.Result doInBackground() {
                return CycleSolver.solve(board, SOLVER_NODE_LIMIT);
            }

            @Override
            protected void done() {
                try {
                    currentResult = get();
                    updateAfterSolve();
                } catch (Exception ex) {
                    boardPanel.setOverlayText("");
                    statusLabel.setText("求解失败：" + ex.getMessage());
                    answerButton.setEnabled(false);
                }
            }
        };
        worker.execute();
    }

    private void updateAfterSolve() {
        boardPanel.setOverlayText("");
        boardPanel.setSolutionPath(currentResult.path());

        if (currentResult.aborted()) {
            statusLabel.setText("求解超出节点上限，无法确认唯一性");
            answerButton.setEnabled(false);
            return;
        }

        if (currentResult.solutionCount() == 0) {
            statusLabel.setText("没有合法环");
            answerButton.setEnabled(false);
            return;
        }

        if (currentResult.solutionCount() == 1) {
            statusLabel.setText("唯一解已找到，白格 " + board.whiteCount() + " 个");
            answerButton.setEnabled(true);
            return;
        }

        statusLabel.setText("存在多个合法环，显示其中一条答案");
        answerButton.setEnabled(!currentResult.path().isEmpty());
    }
}
