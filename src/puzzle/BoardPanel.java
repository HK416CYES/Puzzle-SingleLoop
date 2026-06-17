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

public final class BoardPanel extends JPanel {
    private static final int PADDING = 22;
    private static final Color BACKGROUND = new Color(0xf4f6f8);
    private static final Color BLACK_CELL = new Color(0x20252b);
    private static final Color WHITE_CELL = new Color(0xfafafa);
    private static final Color GRID_LINE = new Color(0xcfd6de);
    private static final Color ANSWER_LINE = new Color(24, 169, 87, 130);
    private static final Color PLAYER_LINE = new Color(0x18a957);

    private Board board = Board.empty(10, 10);
    private PlayerPath playerPath = new PlayerPath(board);
    private List<Integer> solutionPath = List.of();
    private boolean showAnswer;
    private String overlayText = "";
    private Runnable pathChangeListener = () -> { };

    public BoardPanel() {
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(640, 640));
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                startPlayerPath(event);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                appendPlayerPath(event);
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    public void setBoard(Board board) {
        this.board = board;
        this.playerPath = new PlayerPath(board);
        this.solutionPath = List.of();
        this.showAnswer = false;
        this.overlayText = "";
        notifyPathChanged();
        repaint();
    }

    public void setSolutionPath(List<Integer> solutionPath) {
        this.solutionPath = solutionPath == null ? List.of() : List.copyOf(solutionPath);
        repaint();
    }

    public void setShowAnswer(boolean showAnswer) {
        this.showAnswer = showAnswer;
        repaint();
    }

    public void setOverlayText(String overlayText) {
        this.overlayText = overlayText == null ? "" : overlayText;
        repaint();
    }

    public void setPathChangeListener(Runnable pathChangeListener) {
        this.pathChangeListener = pathChangeListener == null ? () -> { } : pathChangeListener;
    }

    public void resetPlayerPath() {
        playerPath.reset();
        notifyPathChanged();
        repaint();
    }

    public boolean undoPlayerPath() {
        boolean changed = playerPath.undo();
        if (changed) {
            notifyPathChanged();
            repaint();
        }
        return changed;
    }

    public boolean redoPlayerPath() {
        boolean changed = playerPath.redo();
        if (changed) {
            notifyPathChanged();
            repaint();
        }
        return changed;
    }

    public boolean canUndoPlayerPath() {
        return playerPath.canUndo();
    }

    public boolean canRedoPlayerPath() {
        return playerPath.canRedo();
    }

    public boolean isPlayerPathClosed() {
        return playerPath.isClosed();
    }

    public List<Integer> playerPathCells() {
        return playerPath.cells();
    }

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

    private void startPlayerPath(MouseEvent event) {
        int cell = cellAt(event.getPoint());
        if (cell == -1 || !board.isWhite(board.rowOf(cell), board.colOf(cell))) {
            return;
        }
        playerPath.reset();
        playerPath.addCell(cell);
        notifyPathChanged();
        repaint();
    }

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

    private void notifyPathChanged() {
        pathChangeListener.run();
    }

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

    private void drawSegment(Graphics2D g, Layout layout, int a, int b) {
        Point p1 = centerOf(a, layout);
        Point p2 = centerOf(b, layout);
        g.draw(new Line2D.Double(p1.x, p1.y, p2.x, p2.y));
    }

    private Point centerOf(int cell, Layout layout) {
        int row = board.rowOf(cell);
        int col = board.colOf(cell);
        int x = layout.x + col * layout.cell + layout.cell / 2;
        int y = layout.y + row * layout.cell + layout.cell / 2;
        return new Point(x, y);
    }

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

    private record Layout(int x, int y, int cell, int boardWidth, int boardHeight) {
    }
}
