package ie.atu.sw;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.ThreadLocalRandom.current;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;

import javax.swing.JPanel;
import javax.swing.Timer;

import jhealy.aicme4j.net.Aicme4jUtils;
import jhealy.aicme4j.net.NeuralNetwork;
import jhealy.aicme4j.net.Output;

public class GameView extends JPanel implements ActionListener {
    private static final long serialVersionUID = 1L;
    private static final int MODEL_WIDTH = 30;
    private static final int MODEL_HEIGHT = 20;
    private static final int SCALING_FACTOR = 30;
    private static final int MIN_TOP = 2;
    private static final int MIN_BOTTOM = 18;
    private static final int PLAYER_COLUMN = 15;
    private static final int TIMER_INTERVAL = 100;
    private static final byte ONE_SET = 1;
    private static final byte ZERO_SET = 0;
    private LinkedList<byte[]> model = new LinkedList<>();
    private int prevTop = MIN_TOP;
    private int prevBot = MIN_BOTTOM;
    private Timer timer;
    private long time;
    private int playerRow = 11;
    private int index = MODEL_WIDTH - 1;
    private Dimension dim;
    private Font font = new Font("Dialog", Font.BOLD, 50);
    private Font over = new Font("Dialog", Font.BOLD, 100);
    private Sprite sprite;
    private Sprite dyingSprite;
    private boolean auto;
    private int previousMove = 0;
    public NeuralNetwork net;

    public GameView(boolean auto) throws Exception {
        this.auto = auto;
        setBackground(Color.LIGHT_GRAY);
        setDoubleBuffered(true);
        dim = new Dimension(MODEL_WIDTH * SCALING_FACTOR, MODEL_HEIGHT * SCALING_FACTOR);
        super.setPreferredSize(dim);
        super.setMinimumSize(dim);
        super.setMaximumSize(dim);
        initModel();
        timer = new Timer(TIMER_INTERVAL, this);
        timer.start();
        try {
            String filename = "./game_ai.data";
            net = Aicme4jUtils.load(filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initModel() {
        for (int i = 0; i < MODEL_WIDTH; i++) {
            model.add(new byte[MODEL_HEIGHT]);
        }
    }

    public void setSprite(Sprite s) {
        this.sprite = s;
    }

    public void setDyingSprite(Sprite s) {
        this.dyingSprite = s;
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2 = (Graphics2D) g;
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, dim.width, dim.height);

        for (int x = 0; x < MODEL_WIDTH; x++) {
            for (int y = 0; y < MODEL_HEIGHT; y++) {
                int x1 = x * SCALING_FACTOR;
                int y1 = y * SCALING_FACTOR;

                if (model.get(x)[y] != 0) {
                    if (y == playerRow && x == PLAYER_COLUMN) {
                        timer.stop();
                    }
                    g2.setColor(Color.BLACK);
                    g2.fillRect(x1, y1, SCALING_FACTOR, SCALING_FACTOR);
                }

                if (x == PLAYER_COLUMN && y == playerRow) {
                    if (timer.isRunning()) {
                        g2.drawImage(sprite.getNext(), x1, y1, null);
                    } else {
                        g2.drawImage(dyingSprite.getNext(), x1, y1, null);
                    }
                }
            }
        }

        g2.setFont(font);
        g2.setColor(Color.RED);
        g2.fillRect(1 * SCALING_FACTOR, 15 * SCALING_FACTOR, 400, 3 * SCALING_FACTOR);
        g2.setColor(Color.WHITE);
        g2.drawString("Time: " + (int)(time * (TIMER_INTERVAL/1000.0d)) + "s", 1 * SCALING_FACTOR + 10, (15 * SCALING_FACTOR) + (2 * SCALING_FACTOR));

        if (!timer.isRunning()) {
            g2.setFont(over);
            g2.setColor(Color.RED);
            g2.drawString("Game Over!", MODEL_WIDTH / 5 * SCALING_FACTOR, MODEL_HEIGHT / 2 * SCALING_FACTOR);
        }
    }

    public void move(int step) {
        playerRow += step;
        previousMove = step;
    }

    private void autoMove() {
        // Make sure autoMove uses the neural network to decide on the move...
        if (auto) {
            try {
                double[] gameState = sample();
                double decision = net.process(gameState, Output.NUMERIC); // Assuming numeric output
                move((int) Math.round(decision)); // Cast decision to int and move
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        time++;
        this.repaint();

        if (auto) autoMove();
        
        index++;
        index = (index == MODEL_WIDTH) ? 0 : index;

        generateNext();
        if (auto && time > 20) {
            try {
                autoMove();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }

        if (time % 10 == 0) {
            double[] gameState = sample();
            // Define paths for game state data and expected action data
            String gameStateFilePath = "./game_state_data.csv";
            String actionFilePath = "./expected_action_data.csv";
            // Call the updated writeDataToFile method with both paths
            writeDataToFile(gameState, previousMove, gameStateFilePath, actionFilePath);
        }

    }

    private void generateNext() {
        byte[] next = model.pollFirst();
        model.addLast(next);
        Arrays.fill(next, ONE_SET);
        prevTop += current().nextBoolean() ? 1 : -1;
        prevBot += current().nextBoolean() ? 1 : -1;
        prevTop = max(MIN_TOP, min(prevTop, prevBot - 4));
        prevBot = min(MIN_BOTTOM, max(prevBot, prevTop + 4));
        Arrays.fill(next, prevTop, prevBot, ZERO_SET);
    }

    public double[] sample() {
        var vector = new double[6]; // Expanded to include additional features
        
        boolean obstacleDirectlyAhead = false;

        // Calculate distances to the nearest obstacle above and below in the next two columns
        for (int j = 0; j < 3; j++) {
            byte[] column = model.get((PLAYER_COLUMN + 1 + j) % MODEL_WIDTH);
            double distanceAbove = 0.0;
            double distanceBelow = 0.0;
            

            // Calculate distance to the nearest obstacle above
            for (int i = playerRow - 1; i >= 0; i--) {
                if (column[i] == ONE_SET) {
                    break;
                }
                distanceAbove += 1.0;
            }

            // Calculate distance to the nearest obstacle below
            for (int i = playerRow + 1; i < MODEL_HEIGHT; i++) {
                if (column[i] == ONE_SET) {
                    break;
                }
                distanceBelow += 1.0;
            }

            // Check if there's an obstacle directly ahead in the immediate next column
            if (j == 0 && column[playerRow] == ONE_SET) {
                obstacleDirectlyAhead = true;
            }

            vector[j * 2] = distanceAbove / MODEL_HEIGHT; // Normalize
            vector[j * 2 + 1] = distanceBelow / MODEL_HEIGHT; // Normalize
        }

        // Additional features
        vector[4] = obstacleDirectlyAhead ? 1.0 : 0.0; // Binary flag for immediate obstacle
        vector[5] = playerRow / (double) MODEL_HEIGHT; // Normalized player row position

        return vector;
    }


    private void writeDataToFile(double[] gameState, int lastMove, String gameStateFilePath, String actionFilePath) {
        // Write the game state to its file
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(gameStateFilePath, true))) {
            for (double d : gameState) {
                bw.write(d + ",");
            }
            bw.newLine(); // Finish this entry
        } catch (IOException ex) {
            System.err.println("Error writing game state to file: " + ex.getMessage());
        }

        // Write the last move (expected action) to its file
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(actionFilePath, true))) {
            bw.write(Integer.toString(lastMove));
            bw.newLine(); // Finish this entry
        } catch (IOException ex) {
            System.err.println("Error writing expected action to file: " + ex.getMessage());
        }
    }


    public void reset() {
        model.forEach(n -> Arrays.fill(n, ZERO_SET));
        playerRow = 11;
        time = 0;
        timer.restart();
    }
}
