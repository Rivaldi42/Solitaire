- Class GamePanel

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class GamePanel extends JPanel implements MouseListener, MouseMotionListener {

    private static final int CARD_WIDTH = 80;
    private static final int CARD_HEIGHT = 110;
    private static final int H_GAP = 20;
    private static final int V_GAP = 30;
    private static final int TOP_MARGIN = 40;
    private static final int LEFT_MARGIN = 40;
    private static final int TABLEAU_Y = TOP_MARGIN + CARD_HEIGHT + 40;
    private static final int TABLEAU_V_OFFSET = 25;

    private List<Pile> tableauPiles;
    private List<Pile> foundationPiles;
    private Pile stockPile;
    private Pile wastePile;

    private MoveManager moveManager;

    private List<Card> draggingCards;
    private Pile dragSourcePile;
    private int dragOffsetX;
    private int dragOffsetY;
    private int dragX;
    private int dragY;

    // flip animation state
    private javax.swing.Timer flipTimer = null;
    private Card flippingCard = null;      
    private int flippingX = 0, flippingY = 0;
    private int flipStep = 0;             

    public GamePanel() {
        setPreferredSize(new Dimension(1024, 720));
        setOpaque(true);

        tableauPiles = new ArrayList<>();
        foundationPiles = new ArrayList<>();
        moveManager = new MoveManager();

        addMouseListener(this);
        addMouseMotionListener(this);

        newGame();
    }

    public void newGame() {
        if (flipTimer != null && flipTimer.isRunning()) {
            flipTimer.stop();
            flipTimer = null;
            flippingCard = null;
        }

        tableauPiles.clear();
        foundationPiles.clear();
        moveManager.clear();
        draggingCards = null;
        dragSourcePile = null;
        flippingCard = null;
        flipStep = 0;

        stockPile = new Pile(Pile.Type.STOCK, LEFT_MARGIN, TOP_MARGIN);
        wastePile = new Pile(Pile.Type.WASTE, LEFT_MARGIN + CARD_WIDTH + H_GAP, TOP_MARGIN);

        for (int i = 0; i < 4; i++) {
            int x = LEFT_MARGIN + (3 + i) * (CARD_WIDTH + H_GAP);
            int y = TOP_MARGIN;
            foundationPiles.add(new Pile(Pile.Type.FOUNDATION, x, y));
        }

        for (int i = 0; i < 7; i++) {
            int x = LEFT_MARGIN + i * (CARD_WIDTH + H_GAP);
            int y = TABLEAU_Y;
            tableauPiles.add(new Pile(Pile.Type.TABLEAU, x, y));
        }

        Deck deck = new Deck();
        deck.shuffle();

        for (int col = 0; col < 7; col++) {
            Pile pile = tableauPiles.get(col);
            for (int row = 0; row <= col; row++) {
                Card card = deck.draw();
                if (card == null) break;
                if (row == col) {
                    card.setFaceUp(true);
                } else {
                    card.setFaceUp(false);
                }
                pile.addCard(card);
            }
        }

        while (!deck.isEmpty()) {
            Card c = deck.draw();
            c.setFaceUp(false);
            stockPile.addCard(c);
        }

        repaint();
    }

    // ==== Rendering ====

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Paint old = g2.getPaint();
        GradientPaint gp = new GradientPaint(0, 0, new Color(30, 10, 60),
                getWidth(), getHeight(), new Color(80, 10, 120));
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setPaint(old);

        g2.setColor(new Color(255, 255, 255, 40));
        for (int i = 0; i < 40; i++) {
            int sx = (i * 53) % Math.max(1, getWidth());
            int sy = (i * 97) % Math.max(1, getHeight());
            g2.fillOval(sx, sy, 4, 4);
        }

        g2.setColor(new Color(240, 230, 255));
        g2.setFont(new Font("Serif", Font.BOLD, 28));
        g2.drawString("Whimsical Magic Solitaire", LEFT_MARGIN, 30);

        drawPilePlaceholder(g2, stockPile);
        drawPilePlaceholder(g2, wastePile);
        for (Pile f : foundationPiles) drawPilePlaceholder(g2, f);
        for (Pile t : tableauPiles) drawTableauPlaceholder(g2, t);

        drawPileCards(g2, stockPile, true);  // as deck visual
        drawPileCards(g2, wastePile, false);
        for (Pile f : foundationPiles) drawPileCards(g2, f, false);
        for (Pile t : tableauPiles) drawTableauPile(g2, t);

        if (draggingCards != null && !draggingCards.isEmpty()) {
            drawDragGlitter(g2);
        }

        if (draggingCards != null && !draggingCards.isEmpty()) {
            int x = dragX - dragOffsetX;
            int y = dragY - dragOffsetY;
            for (int i = 0; i < draggingCards.size(); i++) {
                Card c = draggingCards.get(i);
                c.draw(g2, x, y + i * TABLEAU_V_OFFSET, CARD_WIDTH, CARD_HEIGHT, true);
            }
        }

        if (flippingCard != null) {
            boolean showFace = (flipStep >= 6); // show face in later steps
            flippingCard.draw(g2, flippingX, flippingY, CARD_WIDTH, CARD_HEIGHT, false);
            if (!showFace) {
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fillRoundRect(flippingX, flippingY, CARD_WIDTH, CARD_HEIGHT, 16, 16);
            }
        }

        g2.dispose();
    }

    private void drawPilePlaceholder(Graphics2D g2, Pile pile) {
        int x = pile.getX();
        int y = pile.getY();
        if (!pile.isEmpty()) return;

        g2.setColor(new Color(255, 255, 255, 30));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(x, y, CARD_WIDTH, CARD_HEIGHT, 16, 16);

        if (pile.getType() == Pile.Type.FOUNDATION) {
            g2.setFont(new Font("Serif", Font.PLAIN, 18));
            g2.drawString("★", x + CARD_WIDTH / 2 - 6, y + CARD_HEIGHT / 2);
        }
    }

    private void drawTableauPlaceholder(Graphics2D g2, Pile pile) {
        if (!pile.isEmpty()) return;
        int x = pile.getX();
        int y = pile.getY();
        g2.setColor(new Color(255, 255, 255, 30));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(x, y, CARD_WIDTH, CARD_HEIGHT, 16, 16);
    }


    private void drawPileCards(Graphics2D g2, Pile pile, boolean asDeckVisual) {
        List<Card> cards = pile.getCards();
        if (cards.isEmpty()) {
            return;
        }
        int x = pile.getX();
        int y = pile.getY();

        if (pile == stockPile && asDeckVisual) {
            int stack = Math.min(cards.size(), 6);
            for (int i = stack - 1; i >= 0; i--) {
                int ox = x + i / 2;
                int oy = y + i / 3;
                Card backDummy = cards.get(0);
                backDummy.draw(g2, ox, oy, CARD_WIDTH, CARD_HEIGHT, false);
            }
            return;
        }

        Card top = pile.peekTop();
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            boolean highlight = (c == top);
            if (pile.getType() == Pile.Type.TABLEAU) {
                int yPos = pile.getY() + i * TABLEAU_V_OFFSET;
                if (draggingCards != null && draggingCards.contains(c)) continue;
                c.draw(g2, pile.getX(), yPos, CARD_WIDTH, CARD_HEIGHT, highlight);
            } else {
                if (i == cards.size() - 1) {
                    c.draw(g2, x, y, CARD_WIDTH, CARD_HEIGHT, highlight);
                }
            }
        }
    }

    private void drawTableauPile(Graphics2D g2, Pile pile) {
        List<Card> cards = pile.getCards();
        int x = pile.getX();
        int baseY = pile.getY();
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            if (draggingCards != null && draggingCards.contains(c)) continue;
            int y = baseY + i * TABLEAU_V_OFFSET;
            c.draw(g2, x, y, CARD_WIDTH, CARD_HEIGHT, false);
        }
    }

    private void drawDragGlitter(Graphics2D g2) {
        Random rand = new Random(123 + System.identityHashCode(this) + System.currentTimeMillis()/200 );
        for (int i = 0; i < 24; i++) {
            int sx = dragX + rand.nextInt(60) - 30;
            int sy = dragY + rand.nextInt(60) - 30;
            int size = 1 + rand.nextInt(4);
            int alpha = 120 + rand.nextInt(135);
            g2.setColor(new Color(255, 255, 255, Math.min(255, alpha)));
            g2.fillOval(sx, sy, size, size);
        }
    }

    // ==== Mouse handling ====

    @Override
    public void mousePressed(MouseEvent e) {
        int mx = e.getX();
        int my = e.getY();

        draggingCards = null;
        dragSourcePile = null;

        if (isInsidePile(mx, my, stockPile)) {
            handleStockClick();
            return;
        }

        Pile p = findPileForDrag(mx, my);
        if (p != null) startDraggingFromPile(p, mx, my);
    }

    private Pile findPileForDrag(int mx, int my) {
        for (Pile t : tableauPiles) {
            int index = getTableauCardIndexAt(t, mx, my);
            if (index != -1) {
                Card c = t.getCards().get(index);
                if (!c.isFaceUp()) return null; // cannot drag face-down
                return t;
            }
        }
        if (isInsideTopCard(mx, my, wastePile) && !wastePile.isEmpty()) return wastePile;
        for (Pile f : foundationPiles) {
            if (isInsideTopCard(mx, my, f) && !f.isEmpty()) return f;
        }
        return null;
    }

    private void startDraggingFromPile(Pile pile, int mx, int my) {
        if (pile.getType() == Pile.Type.TABLEAU) {
            int index = getTableauCardIndexAt(pile, mx, my);
            if (index == -1) return;
            Card clicked = pile.getCards().get(index);
            if (!clicked.isFaceUp()) return;
            draggingCards = pile.removeFromIndex(index);
            dragSourcePile = pile;
            int cardX = pile.getX();
            int cardY = pile.getY() + index * TABLEAU_V_OFFSET;
            dragOffsetX = mx - cardX;
            dragOffsetY = my - cardY;
        } else {
            Card top = pile.peekTop();
            if (top == null || !top.isFaceUp()) return;
            draggingCards = new ArrayList<>();
            draggingCards.add(pile.removeTop());
            dragSourcePile = pile;
            dragOffsetX = mx - pile.getX();
            dragOffsetY = my - pile.getY();
        }
        dragX = mx; dragY = my;
        repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (draggingCards == null) return;
        dragX = e.getX();
        dragY = e.getY();
        repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (draggingCards == null || dragSourcePile == null) return;
        int mx = e.getX();
        int my = e.getY();

        Pile target = findDropTarget(mx, my);
        if (target != null && canDropOnPile(draggingCards, dragSourcePile, target)) {
            Card flippedCard = null;
            if (dragSourcePile.getType() == Pile.Type.TABLEAU && !dragSourcePile.isEmpty()) {
                Card top = dragSourcePile.peekTop();
                if (!top.isFaceUp()) {
                    top.setFaceUp(true);
                    flippedCard = top;
                }
            }
            target.addCards(draggingCards);
            Move move = new Move(dragSourcePile, target, draggingCards.size(), flippedCard);
            moveManager.pushMove(move);
            checkWinCondition();
        } else {
            dragSourcePile.addCards(draggingCards);
        }

        draggingCards = null;
        dragSourcePile = null;
        repaint();
    }

    private Pile findDropTarget(int mx, int my) {
        for (Pile t : tableauPiles) {
            if (isInsidePile(mx, my, t)) return t;
        }
        for (Pile f : foundationPiles) {
            if (isInsidePile(mx, my, f)) return f;
        }
        return null;
    }

    private boolean isInsidePile(int mx, int my, Pile pile) {
        int x = pile.getX();
        int y = pile.getY();
        int height = CARD_HEIGHT;
        if (pile.getType() == Pile.Type.TABLEAU) {
            int size = pile.size();
            if (size > 1) height = CARD_HEIGHT + (size - 1) * TABLEAU_V_OFFSET;
        }
        return mx >= x && mx <= x + CARD_WIDTH && my >= y && my <= y + height;
    }

    private boolean isInsideTopCard(int mx, int my, Pile pile) {
        if (pile.isEmpty()) return false;
        return isInsidePile(mx, my, pile);
    }

    private int getTableauCardIndexAt(Pile pile, int mx, int my) {
        List<Card> cards = pile.getCards();
        if (cards.isEmpty()) return -1;
        int x = pile.getX();
        int baseY = pile.getY();
        for (int i = cards.size() - 1; i >= 0; i--) {
            int y = baseY + i * TABLEAU_V_OFFSET;
            Rectangle rect = new Rectangle(x, y, CARD_WIDTH, CARD_HEIGHT);
            if (rect.contains(mx, my)) return i;
        }
        return -1;
    }

    private void handleStockClick() {
        if (!stockPile.isEmpty()) {
            Card c = stockPile.removeTop();
            flippingCard = c;
            flippingX = stockPile.getX();
            flippingY = stockPile.getY();
            flipStep = 0;
            c.setFaceUp(true);
            wastePile.addCard(c);
            Move move = new Move(stockPile, wastePile, 1, null);
            moveManager.pushMove(move);
            startFlipTimer();
        } else {
            if (wastePile.isEmpty()) return;
            int count = wastePile.size();
            List<Card> moved = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Card c = wastePile.removeTop();
                c.setFaceUp(false);
                moved.add(c);
                stockPile.addCard(c);
            }
            Move move = new Move(wastePile, stockPile, count, null);
            moveManager.pushMove(move);
        }
        repaint();
    }

    private void startFlipTimer() {
        if (flipTimer != null && flipTimer.isRunning()) flipTimer.stop();
        flipTimer = new javax.swing.Timer(60, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                flipStep++;
                if (flipStep > 8) {
                    flipTimer.stop();
                    flipTimer = null;
                    flippingCard = null;
                    flipStep = 0;
                }
                repaint();
            }
        });
        flipTimer.start();
    }

    private boolean canDropOnPile(List<Card> movingCards, Pile from, Pile to) {
        if (movingCards.isEmpty()) return false;
        Card first = movingCards.get(0);

        if (to.getType() == Pile.Type.FOUNDATION) {
            if (movingCards.size() != 1) return false;
            Card top = to.peekTop();
            if (top == null) {
                return first.getRank().value == 1;
            } else {
                return first.getSuit() == top.getSuit() &&
                        first.getRank().value == top.getRank().value + 1;
            }
        }

        if (to.getType() == Pile.Type.TABLEAU) {
            Card top = to.peekTop();
            if (top == null) {
                return first.getRank().value == 13; 
            } else {
                boolean colorOk = first.isRed() != top.isRed();
                boolean rankOk = first.getRank().value == top.getRank().value - 1;
                return colorOk && rankOk;
            }
        }

        return false;
    }

    // ==== Undo / Redo ====

    public void undoMove() {
        Move move = moveManager.popUndo();
        if (move == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        int n = move.cardCount;
        List<Card> temp = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Card c = move.to.removeTop();
            temp.add(0, c);
        }
        move.from.addCards(temp);

        if (move.from == stockPile && move.to == wastePile && n == 1) {
            Card c = move.from.peekTop();
            if (c != null) c.setFaceUp(false);
        } else if (move.from == wastePile && move.to == stockPile) {
            for (Card c : temp) c.setFaceUp(true);
        }

        if (move.flippedCard != null) move.flippedCard.setFaceUp(false);

        repaint();
    }

    public void redoMove() {
        Move move = moveManager.popRedo();
        if (move == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        int n = move.cardCount;
        List<Card> temp = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Card c = move.from.removeTop();
            temp.add(0, c);
        }
        move.to.addCards(temp);

        if (move.from == stockPile && move.to == wastePile && n == 1) {
            Card c = move.to.peekTop();
            if (c != null) c.setFaceUp(true);
        } else if (move.from == wastePile && move.to == stockPile) {
            for (Card c : temp) c.setFaceUp(false);
        }

        if (move.flippedCard != null) move.flippedCard.setFaceUp(true);

        repaint();
    }

    private void checkWinCondition() {
        int total = 0;
        for (Pile f : foundationPiles) total += f.size();
        if (total == 52) {
            JOptionPane.showMessageDialog(this,
                    "✨ Selamat! Kamu menyelesaikan Whimsical Magic Solitaire! ✨",
                    "You Win",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseMoved(MouseEvent e) {}
}



- Class Card
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.ImageIO;


public class Card {

    public enum Suit { CLUBS, DIAMONDS, HEARTS, SPADES }

    public enum Rank {
        ACE(1, "A"), TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"),
        FIVE(5, "5"), SIX(6, "6"), SEVEN(7, "7"), EIGHT(8, "8"),
        NINE(9, "9"), TEN(10, "10"), JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K");

        public final int value;
        public final String label;
        Rank(int v, String l) { value = v; label = l; }
    }

    private Suit suit;
    private Rank rank;
    private boolean faceUp = false;

    private static BufferedImage BACK_IMAGE = null;
    private static java.util.Map<String, BufferedImage> faceArt = new java.util.HashMap<>();

    static {
        loadImages();
    }

    private static void loadImages() {
        try {
            BACK_IMAGE = ImageIO.read(new File("images/back.png"));
        } catch (Exception ex) {
            BACK_IMAGE = null;
        }

        String[] ranks = {"J","Q","K"};
        String[] suits = {"CLUBS","DIAMONDS","HEARTS","SPADES"};
        for (String r : ranks) {
            for (String s : suits) {
                String name = r + "_" + s + ".png";
                try {
                    BufferedImage im = ImageIO.read(new File("images/" + name));
                    if (im != null) faceArt.put(r + "_" + s, im);
                } catch (Exception ex) {
                }
            }
        }
    }

    public Card(Suit s, Rank r) {
        this.suit = s;
        this.rank = r;
    }

    public Suit getSuit(){ return suit; }
    public Rank getRank(){ return rank; }
    public boolean isFaceUp(){ return faceUp; }
    public void setFaceUp(boolean f){ faceUp = f; }

    public boolean isRed(){ return suit == Suit.HEARTS || suit == Suit.DIAMONDS; }

    private String getSuitSymbol() {
        switch (suit) {
            case CLUBS: return "♣";
            case DIAMONDS: return "♦";
            case HEARTS: return "♥";
            case SPADES: return "♠";
            default: return "?";
        }
    }


    public void draw(Graphics2D g2, int x, int y, int width, int height, boolean highlighted) {
        if (!faceUp) {
            drawBack(g2, x, y, width, height, highlighted);
            return;
        }
        drawFront(g2, x, y, width, height, highlighted);
    }

    private void drawBack(Graphics2D g2, int x, int y, int width, int height, boolean highlighted) {
        if (BACK_IMAGE != null) {
            g2.setColor(new Color(70, 20, 110));
            g2.fillRoundRect(x, y, width, height, 18, 18);

            int pad = Math.max(6, width/12);
            int iw = width - pad*2;
            int ih = height - pad*2;
            g2.drawImage(BACK_IMAGE, x+pad, y+pad, iw, ih, null);

            g2.setStroke(new BasicStroke(3f));
            g2.setColor(new Color(255,255,255,150));
            g2.drawRoundRect(x+3, y+3, width-6, height-6, 16, 16);
            return;
        }

        g2.setColor(new Color(80, 0, 120));
        g2.fillRoundRect(x, y, width, height, 18, 18);
        g2.setColor(new Color(180, 120, 255));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(x + 3, y + 3, width - 6, height - 6, 18, 18);
        g2.setFont(new Font("Serif", Font.BOLD, 22));
        g2.setColor(new Color(255,255,255,200));
        g2.drawString("★", x + width / 2 - 8, y + height / 2 + 6);
    }

    private void drawFront(Graphics2D g2, int x, int y, int width, int height, boolean highlighted) {
        g2.setColor(new Color(255, 255, 245));
        g2.fillRoundRect(x, y, width, height, 18, 18);

        if (highlighted) {
            g2.setColor(new Color(255, 240, 130));
            g2.setStroke(new BasicStroke(4f));
        } else {
            g2.setColor(new Color(200, 180, 255));
            g2.setStroke(new BasicStroke(3f));
        }
        g2.drawRoundRect(x, y, width, height, 18, 18);

        if ((rank == Rank.JACK || rank == Rank.QUEEN || rank == Rank.KING)) {
            String key = rank.label + "_" + suit.name();
            BufferedImage art = faceArt.get(key);
            if (art != null) {
                int marginX = Math.max(6, width / 12);
                int marginY = Math.max(8, height / 10);
                int iw = width - marginX*2;
                int ih = height - marginY*2;
                g2.drawImage(art, x + marginX, y + marginY, iw, ih, null);
                return;
            }
        }

        g2.setFont(new Font("Serif", Font.BOLD, Math.max(14, width/7)));
        g2.setColor(isRed() ? new Color(180, 0, 40) : new Color(20, 20, 80));
        String topLabel = rank.label;
        String suitSym = getSuitSymbol();
        g2.drawString(topLabel, x + 10, y + 22);
        g2.setFont(new Font("Serif", Font.PLAIN, Math.max(12, width/9)));
        g2.drawString(suitSym, x + 10, y + 40);

        g2.setFont(new Font("Serif", Font.PLAIN, Math.max(12, width/9)));
        g2.drawString(topLabel, x + width - 28, y + height - 10);
        g2.drawString(suitSym, x + width - 28, y + height - 28);

        g2.setFont(new Font("Serif", Font.BOLD, Math.max(28, width/3)));
        g2.drawString(suitSym, x + width/2 - 10, y + height/2 + 10);
    }

    public static BufferedImage getBackImage() { return BACK_IMAGE; }

    public boolean hasFaceArt() {
        if (!(rank == Rank.JACK || rank == Rank.QUEEN || rank == Rank.KING)) return false;
        String key = rank.label + "_" + suit.name();
        return faceArt.containsKey(key);
    }
}

- Class Deck
import java.util.*;

public class Deck {
    private java.util.List<Card> cards;

    public Deck() {
        cards = new ArrayList<Card>();
        for (Card.Suit suit : Card.Suit.values()) {
            for (Card.Rank rank : Card.Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
        shuffle();
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public Card draw() {
        if (cards.isEmpty()) return null;
        return cards.remove(cards.size() - 1);
    }
}


- Class Move
public class Move {
    public Pile from;
    public Pile to;
    public int cardCount;
    public Card flippedCard;

    public Move(Pile from, Pile to, int cardCount, Card flippedCard) {
        this.from = from;
        this.to = to;
        this.cardCount = cardCount;
        this.flippedCard = flippedCard;
    }
}


- Class MoveManager
import java.util.*;

public class MoveManager {
    private Deque<Move> undoStack;
    private Deque<Move> redoStack;

    public MoveManager() {
        undoStack = new ArrayDeque<Move>();
        redoStack = new ArrayDeque<Move>();
    }

    public void pushMove(Move move) {
        undoStack.push(move);
        redoStack.clear();
    }

    public Move popUndo() {
        if (undoStack.isEmpty()) return null;
        Move move = undoStack.pop();
        redoStack.push(move);
        return move;
    }

    public Move popRedo() {
        if (redoStack.isEmpty()) return null;
        Move move = redoStack.pop();
        undoStack.push(move);
        return move;
    }

    public boolean hasUndo() {
        return !undoStack.isEmpty();
    }

    public boolean hasRedo() {
        return !redoStack.isEmpty();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}


- Class Pile
import java.util.*;
import java.awt.*;

public class Pile {

    public enum Type {
        TABLEAU, FOUNDATION, STOCK, WASTE
    }

    private java.util.List<Card> cards;
    private Type type;
    private int x;
    private int y;

    public Pile(Type type, int x, int y) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.cards = new ArrayList<Card>();
    }

    public Type getType() {
        return type;
    }

    public java.util.List<Card> getCards() {
        return cards;
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public Card peekTop() {
        if (cards.isEmpty()) return null;
        return cards.get(cards.size() - 1);
    }

    public Card removeTop() {
        if (cards.isEmpty()) return null;
        return cards.remove(cards.size() - 1);
    }

    public void addCard(Card card) {
        cards.add(card);
    }

    public void addCards(java.util.List<Card> newCards) {
        cards.addAll(newCards);
    }

    public java.util.List<Card> removeFromIndex(int index) {
        java.util.List<Card> moving = new ArrayList<Card>();
        while (cards.size() > index) {
            moving.add(cards.remove(index));
        }
        return moving;
    }

    public int size() {
        return cards.size();
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

- Class SolitaireGame
import javax.swing.*;
import java.awt.*;

public class SolitaireGame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFrame frame = new JFrame("Whimsical Magic Solitaire");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setLayout(new BorderLayout());

                GamePanel gamePanel = new GamePanel();

                JPanel controlPanel = new JPanel();
                JButton newGameButton = new JButton("New Game");
                JButton undoButton = new JButton("Undo");
                JButton redoButton = new JButton("Redo");

                controlPanel.add(newGameButton);
                controlPanel.add(undoButton);
                controlPanel.add(redoButton);

                newGameButton.addActionListener(e -> gamePanel.newGame());
                undoButton.addActionListener(e -> gamePanel.undoMove());
                redoButton.addActionListener(e -> gamePanel.redoMove());

                frame.add(gamePanel, BorderLayout.CENTER);
                frame.add(controlPanel, BorderLayout.SOUTH);

                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }
}
