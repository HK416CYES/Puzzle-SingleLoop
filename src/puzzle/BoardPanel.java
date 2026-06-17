package puzzle;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.util.List;

/**
 * 显示棋盘并处理玩家鼠标拖动路径的 Swing 面板。
 */
public final class BoardPanel extends JPanel {
    /** 保存 private。 */
    private static final int PADDING = 22;
    /** 保存 private。 */
    private static final Color BACKGROUND = new Color(0xf4f6f8);
    /** 保存 private。 */
    private static final Color BLACK_CELL = new Color(0x20252b);
    /** 保存 private。 */
    private static final Color WHITE_CELL = new Color(0xfafafa);
    /** 保存 private。 */
    private static final Color GRID_LINE = new Color(0xcfd6de);
    /** 保存 private。 */
    private static final Color ANSWER_LINE = new Color(24, 169, 87, 130);
    /** 保存 private。 */
    private static final Color PLAYER_LINE = new Color(0x18a957);

    /** 保存 private。 */
    private Board board = Board.empty(10, 10);
    /** 保存 private。 */
    private PlayerPath playerPath = new PlayerPath(board);
    /** 保存 private。 */
    private List<Integer> solutionPath = List.of();
    /** 保存 private。 */
    private boolean showAnswer;
    /** 保存 private。 */
    private String overlayText = "";
    /** 保存 private。 */
    private Runnable pathChangeListener = () -> { };

    /** 创建 BoardPanel 实例。 */
    public BoardPanel() {
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(640, 640));
        MouseAdapter mouseAdapter = new MouseAdapter() {
            /** 执行 mousePressed 相关逻辑。 */
            @Override
            public void mousePressed(MouseEvent event) {
                startPlayerPath(event);
            }

            /** 执行 mouseDragged 相关逻辑。 */
            @Override
            public void mouseDragged(MouseEvent event) {
                appendPlayerPath(event);
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    /**
     * 设置当前棋盘并清空显示状态。
     *
     * @param board 新棋盘
     */
    public void setBoard(Board board) {
        this.board = board;
        this.playerPath = new PlayerPath(board);
        this.solutionPath = List.of();
        this.showAnswer = false;
        this.overlayText = "";
        notifyPathChanged();
        repaint();
    }

    /**
     * 设置标准答案路径。
     *
     * @param solutionPath 标准答案路径
     */
    public void setSolutionPath(List<Integer> solutionPath) {
        this.solutionPath = solutionPath == null ? List.of() : List.copyOf(solutionPath);
        repaint();
    }

    /**
     * 设置是否显示标准答案。
     *
     * @param showAnswer 是否显示答案
     */
    public void setShowAnswer(boolean showAnswer) {
        this.showAnswer = showAnswer;
        repaint();
    }

    /**
     * 设置棋盘中央覆盖提示文本。
     *
     * @param overlayText 提示文本
     */
    public void setOverlayText(String overlayText) {
        this.overlayText = overlayText == null ? "" : overlayText;
        repaint();
    }

    /**
     * 设置玩家路径变化监听器。
     *
     * @param pathChangeListener 路径变化回调
     */
    public void setPathChangeListener(Runnable pathChangeListener) {
        this.pathChangeListener = pathChangeListener == null ? () -> { } : pathChangeListener;
    }

    /**
     * 清空玩家当前作答路径。
     */
    public void resetPlayerPath() {
        playerPath.reset();
        notifyPathChanged();
        repaint();
    }

    /**
     * 撤销玩家路径的一步。
     *
     * @return 如果状态发生变化则返回 {@code true}
     */
    public boolean undoPlayerPath() {
        boolean changed = playerPath.undo();
        if (changed) {
            notifyPathChanged();
            repaint();
        }
        return changed;
    }

    /**
     * 重做玩家路径的一步。
     *
     * @return 如果状态发生变化则返回 {@code true}
     */
    public boolean redoPlayerPath() {
        boolean changed = playerPath.redo();
        if (changed) {
            notifyPathChanged();
            repaint();
        }
        return changed;
    }

    /**
     * 判断玩家路径是否可以撤销。
     *
     * @return 如果可以撤销则返回 {@code true}
     */
    public boolean canUndoPlayerPath() {
        return playerPath.canUndo();
    }

    /**
     * 判断玩家路径是否可以重做。
     *
     * @return 如果可以重做则返回 {@code true}
     */
    public boolean canRedoPlayerPath() {
        return playerPath.canRedo();
    }

    /**
     * 判断玩家路径是否已经闭环。
     *
     * @return 如果已经闭环则返回 {@code true}
     */
    public boolean isPlayerPathClosed() {
        return playerPath.isClosed();
    }

    /**
     * 获取玩家路径中的格子列表。
     *
     * @return 玩家路径格子列表
     */
    public List<Integer> playerPathCells() {
        return playerPath.cells();
    }

    /** 执行 paintComponent 相关逻辑。 */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Layout layout = computeLayout();
        drawCells(g, layout);
        if (showAnswer && !solutionPath.isEmpty()) {
            drawPath(g, layout, solutionPath, true, ANSWER_LINE, Math.max(3f, layout.cell * 0.09f));
        }
        if (!playerPath.cells().isEmpty()) {
            drawPath(g, layout, playerPath.cells(), playerPath.isClosed(), PLAYER_LINE, Math.max(2f, layout.cell * 0.06f));
        }
        if (!overlayText.isBlank()) {
            drawOverlay(g, layout);
        }

        g.dispose();
    }

    /** 执行 startPlayerPath 相关逻辑。 */
    private void startPlayerPath(MouseEvent event) {
        int cell = cellAt(event.getPoint());
        if (cell == -1 || !board.isWhite(board.rowOf(cell), board.colOf(cell))) {
            return;
        }
        if (playerPath.containsCell(cell)) {
            return;
        }
        playerPath.reset();
        playerPath.addCell(cell);
        notifyPathChanged();
        repaint();
    }

    /** 执行 appendPlayerPath 相关逻辑。 */
    private void appendPlayerPath(MouseEvent event) {
        int cell = cellAt(event.getPoint());
        if (cell == -1) {
            return;
        }
        if (playerPath.addCell(cell)) {
            notifyPathChanged();
            repaint();
        }
    }

    /** 执行 cellAt 相关逻辑。 */
    private int cellAt(Point point) {
        Layout layout = computeLayout();
        if (point.x < layout.x || point.y < layout.y
            || point.x >= layout.x + layout.boardWidth || point.y >= layout.y + layout.boardHeight) {
            return -1;
        }
        int col = (point.x - layout.x) / layout.cell;
        int row = (point.y - layout.y) / layout.cell;
        if (row < 0 || row >= board.rows() || col < 0 || col >= board.cols()) {
            return -1;
        }
        return board.cellIndex(row, col);
    }

    /** 执行 notifyPathChanged 相关逻辑。 */
    private void notifyPathChanged() {
        pathChangeListener.run();
    }

    /** 执行 computeLayout 相关逻辑。 */
    private Layout computeLayout() {
        int availableWidth = Math.max(1, getWidth() - PADDING * 2);
        int availableHeight = Math.max(1, getHeight() - PADDING * 2);
        int cell = Math.max(8, Math.min(availableWidth / board.cols(), availableHeight / board.rows()));
        int boardWidth = cell * board.cols();
        int boardHeight = cell * board.rows();
        int x = (getWidth() - boardWidth) / 2;
        int y = (getHeight() - boardHeight) / 2;
        return new Layout(x, y, cell, boardWidth, boardHeight);
    }

    /** 执行 drawCells 相关逻辑。 */
    private void drawCells(Graphics2D g, Layout layout) {
        for (int r = 0; r < board.rows(); r++) {
            for (int c = 0; c < board.cols(); c++) {
                int x = layout.x + c * layout.cell;
                int y = layout.y + r * layout.cell;
                g.setColor(board.isWhite(r, c) ? WHITE_CELL : BLACK_CELL);
                g.fillRect(x, y, layout.cell, layout.cell);
            }
        }

        g.setColor(GRID_LINE);
        g.setStroke(new BasicStroke(1f));
        for (int r = 0; r <= board.rows(); r++) {
            int y = layout.y + r * layout.cell;
            g.drawLine(layout.x, y, layout.x + layout.boardWidth, y);
        }
        for (int c = 0; c <= board.cols(); c++) {
            int x = layout.x + c * layout.cell;
            g.drawLine(x, layout.y, x, layout.y + layout.boardHeight);
        }
    }

    /** 执行 drawPath 相关逻辑。 */
    private void drawPath(Graphics2D g, Layout layout, List<Integer> path, boolean closed, Color color, float strokeWidth) {
        if (path.size() < 2) {
            return;
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (int i = 0; i + 1 < path.size(); i++) {
            drawSegment(g, layout, path.get(i), path.get(i + 1));
        }
        if (closed) {
            drawSegment(g, layout, path.get(path.size() - 1), path.get(0));
        }
    }

    /** 执行 drawSegment 相关逻辑。 */
    private void drawSegment(Graphics2D g, Layout layout, int a, int b) {
        Point p1 = centerOf(a, layout);
        Point p2 = centerOf(b, layout);
        g.draw(new Line2D.Double(p1.x, p1.y, p2.x, p2.y));
    }

    /** 执行 centerOf 相关逻辑。 */
    private Point centerOf(int cell, Layout layout) {
        int row = board.rowOf(cell);
        int col = board.colOf(cell);
        int x = layout.x + col * layout.cell + layout.cell / 2;
        int y = layout.y + row * layout.cell + layout.cell / 2;
        return new Point(x, y);
    }

    /** 执行 drawOverlay 相关逻辑。 */
    private void drawOverlay(Graphics2D g, Layout layout) {
        FontMetrics metrics = g.getFontMetrics();
        int textWidth = metrics.stringWidth(overlayText);
        int textHeight = metrics.getHeight();
        int boxWidth = textWidth + 28;
        int boxHeight = textHeight + 18;
        int x = layout.x + (layout.boardWidth - boxWidth) / 2;
        int y = layout.y + (layout.boardHeight - boxHeight) / 2;

        g.setColor(new Color(255, 255, 255, 225));
        g.fillRoundRect(x, y, boxWidth, boxHeight, 8, 8);
        g.setColor(new Color(0x26323c));
        g.drawString(overlayText, x + 14, y + 12 + metrics.getAscent());
    }

    /** 表示 Layout 记录。 */
    private record Layout(int x, int y, int cell, int boardWidth, int boardHeight) {
    }
}
