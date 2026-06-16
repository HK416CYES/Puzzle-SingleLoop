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
import java.awt.geom.Line2D;
import java.util.List;

public final class BoardPanel extends JPanel {
    private static final int PADDING = 22;
    private static final Color BACKGROUND = new Color(0xf4f6f8);
    private static final Color BLACK_CELL = new Color(0x20252b);
    private static final Color WHITE_CELL = new Color(0xfafafa);
    private static final Color GRID_LINE = new Color(0xcfd6de);
    private static final Color ANSWER_LINE = new Color(0x18a957);

    private Board board = Board.defaultBoard();
    private List<Integer> solutionPath = List.of();
    private boolean showAnswer;
    private String overlayText = "";

    public BoardPanel() {
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(640, 640));
    }

    public void setBoard(Board board) {
        this.board = board;
        this.solutionPath = List.of();
        this.showAnswer = false;
        this.overlayText = "";
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

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Layout layout = computeLayout();
        drawCells(g, layout);
        if (showAnswer && !solutionPath.isEmpty()) {
            drawAnswer(g, layout);
        }
        if (!overlayText.isBlank()) {
            drawOverlay(g, layout);
        }

        g.dispose();
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

    private void drawAnswer(Graphics2D g, Layout layout) {
        g.setColor(ANSWER_LINE);
        g.setStroke(new BasicStroke(Math.max(2f, layout.cell * 0.07f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (int i = 0; i < solutionPath.size(); i++) {
            int a = solutionPath.get(i);
            int b = solutionPath.get((i + 1) % solutionPath.size());
            Point p1 = centerOf(a, layout);
            Point p2 = centerOf(b, layout);
            g.draw(new Line2D.Double(p1.x, p1.y, p2.x, p2.y));
        }
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
