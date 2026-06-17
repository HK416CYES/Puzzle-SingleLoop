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

/**
 * 黑白环形连线谜题的 Swing 应用入口。
 */
public final class PuzzleApp {
    /** 保存 private。 */
    private static final long SOLVER_NODE_LIMIT = 40_000_000L;

    /** 保存 private。 */
    private final JFrame frame = new JFrame("Black White Cycle Puzzle");
    /** 保存 private。 */
    private final BoardPanel boardPanel = new BoardPanel();
    /** 保存 private。 */
    private final JLabel statusLabel = new JLabel(" ");
    /** 保存 private。 */
    private final JToggleButton answerButton = new JToggleButton("显示答案");
    /** 保存 private。 */
    private final JButton generateButton = new JButton("随机生成");
    /** 保存 private。 */
    private final JButton submitButton = new JButton("提交");
    /** 保存 private。 */
    private final JButton undoButton = new JButton("←");
    /** 保存 private。 */
    private final JButton redoButton = new JButton("→");
    /** 保存 private。 */
    private final JButton saveButton = new JButton("保存棋盘");
    /** 保存 private。 */
    private final JComboBox<BoardGenerator.Difficulty> difficultyBox =
        new JComboBox<>(BoardGenerator.Difficulty.values());
    /** 保存 private。 */
    private Board board;
    /** 保存 private。 */
    private boolean autoGenerateOnShow;
    /** 保存 private。 */
    private CycleSolver.Result currentResult = new CycleSolver.Result(0, false, List.of(), 0);

    /**
     * 启动命令行模式或图形界面模式。
     *
     * @param args 命令行参数
     */
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

    /** 执行 runCliCheck 相关逻辑。 */
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

    /** 执行 runCliGenerate 相关逻辑。 */
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

    /** 执行 loadInitialBoard 相关逻辑。 */
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

    /** 创建 PuzzleApp 实例。 */
    private PuzzleApp(Board initialBoard) {
        configureFrame();
        boardPanel.setPathChangeListener(this::updatePlayerControls);
        if (initialBoard == null) {
            showEmptyBoard();
            autoGenerateOnShow = true;
        } else {
            loadBoard(initialBoard);
        }
    }

    /** 执行 configureFrame 相关逻辑。 */
    private void configureFrame() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(createToolbar(), BorderLayout.NORTH);
        frame.add(boardPanel, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    /** 执行 createToolbar 相关逻辑。 */
    private JPanel createToolbar() {
        JButton openButton = new JButton("加载棋盘");

        answerButton.setEnabled(false);
        saveButton.setEnabled(false);
        submitButton.setEnabled(false);
        undoButton.setEnabled(false);
        redoButton.setEnabled(false);
        answerButton.addActionListener(event -> {
            boardPanel.setShowAnswer(answerButton.isSelected());
            answerButton.setText(answerButton.isSelected() ? "隐藏答案" : "显示答案");
        });
        openButton.addActionListener(event -> openBoardFile());
        saveButton.addActionListener(event -> saveBoardFile());
        generateButton.addActionListener(event -> generateRandomBoard());
        submitButton.addActionListener(event -> submitAnswer());
        undoButton.addActionListener(event -> {
            boardPanel.undoPlayerPath();
            updatePlayerControls();
        });
        redoButton.addActionListener(event -> {
            boardPanel.redoPlayerPath();
            updatePlayerControls();
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        toolbar.add(difficultyBox);
        toolbar.add(generateButton);
        toolbar.add(openButton);
        toolbar.add(saveButton);
        toolbar.add(undoButton);
        toolbar.add(redoButton);
        toolbar.add(submitButton);
        toolbar.add(answerButton);
        return toolbar;
    }

    /** 执行 show 相关逻辑。 */
    private void show() {
        frame.setVisible(true);
        if (autoGenerateOnShow) {
            autoGenerateOnShow = false;
            generateRandomBoard();
        }
    }

    /** 执行 showEmptyBoard 相关逻辑。 */
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
        boardPanel.resetPlayerPath();
        updatePlayerControls();
        statusLabel.setText("正在准备自动生成棋盘...");
    }

    /** 执行 openBoardFile 相关逻辑。 */
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

    /** 执行 saveBoardFile 相关逻辑。 */
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

    /** 执行 withDefaultTxtExtension 相关逻辑。 */
    private Path withDefaultTxtExtension(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.contains(".")) {
            return path;
        }
        return path.resolveSibling(fileName + ".txt");
    }

    /** 执行 generateRandomBoard 相关逻辑。 */
    private void generateRandomBoard() {
        BoardGenerator.Difficulty difficulty = selectedDifficulty();
        answerButton.setSelected(false);
        answerButton.setText("显示答案");
        answerButton.setEnabled(false);
        saveButton.setEnabled(false);
        generateButton.setEnabled(false);
        difficultyBox.setEnabled(false);
        boardPanel.setShowAnswer(false);
        boardPanel.resetPlayerPath();
        updatePlayerControls();
        boardPanel.setOverlayText("正在生成");
        statusLabel.setText("正在随机生成" + difficulty.label() + "难度唯一解棋盘...");

        SwingWorker<BoardGenerator.Generated, Void> worker = new SwingWorker<>() {
            /** 执行 doInBackground 相关逻辑。 */
            @Override
            protected BoardGenerator.Generated doInBackground() {
                return BoardGenerator.generate(difficulty);
            }

            /** 执行 done 相关逻辑。 */
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
                    boardPanel.resetPlayerPath();
                    statusLabel.setText(
                        difficulty.label() + "难度棋盘已生成，seed=" + generated.seed()
                            + "，尝试 " + generated.attempts()
                            + " 次，用时 " + generated.elapsedMillis()
                            + "ms，白格 " + board.whiteCount() + " 个"
                    );
                    answerButton.setEnabled(true);
                    saveButton.setEnabled(true);
                    updatePlayerControls();
                } catch (Exception ex) {
                    boardPanel.setOverlayText("");
                    statusLabel.setText("随机生成失败：" + ex.getMessage());
                    answerButton.setEnabled(false);
                    saveButton.setEnabled(board != null);
                    updatePlayerControls();
                }
            }
        };
        worker.execute();
    }

    /** 执行 selectedDifficulty 相关逻辑。 */
    private BoardGenerator.Difficulty selectedDifficulty() {
        Object selected = difficultyBox.getSelectedItem();
        if (selected instanceof BoardGenerator.Difficulty difficulty) {
            return difficulty;
        }
        return BoardGenerator.Difficulty.EASY;
    }

    /** 执行 loadBoard 相关逻辑。 */
    private void loadBoard(Board newBoard) {
        generateButton.setEnabled(true);
        difficultyBox.setEnabled(true);
        this.board = newBoard;
        this.currentResult = new CycleSolver.Result(0, false, List.of(), 0);
        boardPanel.setBoard(newBoard);
        boardPanel.resetPlayerPath();
        answerButton.setSelected(false);
        answerButton.setText("显示答案");
        answerButton.setEnabled(false);
        saveButton.setEnabled(true);
        statusLabel.setText("正在求解 " + board.rows() + "x" + board.cols() + "，白格 " + board.whiteCount() + " 个...");
        boardPanel.setOverlayText("正在求解");

        SwingWorker<CycleSolver.Result, Void> worker = new SwingWorker<>() {
            /** 执行 doInBackground 相关逻辑。 */
            @Override
            protected CycleSolver.Result doInBackground() {
                return CycleSolver.solve(board, SOLVER_NODE_LIMIT);
            }

            /** 执行 done 相关逻辑。 */
            @Override
            protected void done() {
                try {
                    currentResult = get();
                    updateAfterSolve();
                } catch (Exception ex) {
                    boardPanel.setOverlayText("");
                    statusLabel.setText("求解失败：" + ex.getMessage());
                    answerButton.setEnabled(false);
                    updatePlayerControls();
                }
            }
        };
        worker.execute();
    }

    /** 执行 submitAnswer 相关逻辑。 */
    private void submitAnswer() {
        if (board == null || currentResult.path().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "当前没有可提交的标准答案", "提交失败", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean accepted = AnswerChecker.matches(
            board,
            boardPanel.playerPathCells(),
            boardPanel.isPlayerPathClosed(),
            currentResult.path()
        );
        if (accepted) {
            statusLabel.setText("提交通过，答案正确");
            JOptionPane.showMessageDialog(frame, "答案正确，通过！", "提交结果", JOptionPane.INFORMATION_MESSAGE);
        } else {
            statusLabel.setText("提交不通过，请检查路径是否闭环并覆盖所有白格");
            JOptionPane.showMessageDialog(frame, "答案不正确", "提交结果", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 执行 updatePlayerControls 相关逻辑。 */
    private void updatePlayerControls() {
        boolean hasBoard = board != null;
        boolean hasAnswer = hasBoard && !currentResult.path().isEmpty();
        undoButton.setEnabled(hasBoard && boardPanel.canUndoPlayerPath());
        redoButton.setEnabled(hasBoard && boardPanel.canRedoPlayerPath());
        submitButton.setEnabled(hasAnswer && boardPanel.isPlayerPathClosed());
    }

    /** 执行 updateAfterSolve 相关逻辑。 */
    private void updateAfterSolve() {
        boardPanel.setOverlayText("");
        boardPanel.setSolutionPath(currentResult.path());

        if (currentResult.aborted()) {
            statusLabel.setText("求解超出节点上限，无法确认唯一性");
            answerButton.setEnabled(false);
            updatePlayerControls();
            return;
        }

        if (currentResult.solutionCount() == 0) {
            statusLabel.setText("没有合法环");
            answerButton.setEnabled(false);
            updatePlayerControls();
            return;
        }

        if (currentResult.solutionCount() == 1) {
            statusLabel.setText("唯一解已找到，白格 " + board.whiteCount() + " 个");
            answerButton.setEnabled(true);
            updatePlayerControls();
            return;
        }

        statusLabel.setText("存在多个合法环，显示其中一条答案");
        answerButton.setEnabled(!currentResult.path().isEmpty());
        updatePlayerControls();
    }
}
