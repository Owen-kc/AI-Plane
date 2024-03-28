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

public class GameView extends JPanel implements ActionListener{
	//Some constants
	private static final long serialVersionUID	= 1L;
	private static final int MODEL_WIDTH 		= 30;
	private static final int MODEL_HEIGHT 		= 20;
	private static final int SCALING_FACTOR 	= 30;
	
	private static final int MIN_TOP 			= 2;
	private static final int MIN_BOTTOM 		= 18;
	private static final int PLAYER_COLUMN 		= 15;
	private static final int TIMER_INTERVAL 	= 100;
	
	private static final byte ONE_SET 			=  1;
	private static final byte ZERO_SET 			=  0;
	private int[] safeSpots;
	
	public NeuralNetwork net;

	/*
	 * The 30x20 game grid is implemented using a linked list of 
	 * 30 elements, where each element contains a byte[] of size 20. 
	 */
	private LinkedList<byte[]> model = new LinkedList<>();

	//These two variables are used by the cavern generator. 
	private int prevTop = MIN_TOP;
	private int prevBot = MIN_BOTTOM;
	
	//Once the timer stops, the game is over
	private Timer timer;
	private long time;
	
	private int playerRow = 11;
	private int index = MODEL_WIDTH - 1; //Start generating at the end
	private Dimension dim;
	
	//Some fonts for the UI display
	private Font font = new Font ("Dialog", Font.BOLD, 50);
	private Font over = new Font ("Dialog", Font.BOLD, 100);

	//The player and a sprite for an exploding plane
	private Sprite sprite;
	private Sprite dyingSprite;
	
	private boolean auto;

	public GameView(boolean auto) throws Exception{
		this.auto = auto; //Use the autopilot
		setBackground(Color.LIGHT_GRAY);
		setDoubleBuffered(true);
		
		//Creates a viewing area of 900 x 600 pixels
		dim = new Dimension(MODEL_WIDTH * SCALING_FACTOR, MODEL_HEIGHT * SCALING_FACTOR);
    	super.setPreferredSize(dim);
    	super.setMinimumSize(dim);
    	super.setMaximumSize(dim);
		
    	initModel();
    	
		timer = new Timer(TIMER_INTERVAL, this); //Timer calls actionPerformed() every second
		timer.start();
		
		try {
            String filename = "./game_ai.data"; // The path to your saved neural network
            net = Aicme4jUtils.load(filename); // Assuming aicme4jutils.load() is a static method to load the network
        } catch (Exception e) {
            e.printStackTrace();
            // Handle exceptions or errors as needed
        }
    }
	
	//Build our game grid
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
	
	//Called every second by actionPerformed(). Paint methods are usually ugly.
	public void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2 = (Graphics2D)g;
        
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, dim.width, dim.height);
        
        int x1 = 0, y1 = 0;
        for (int x = 0; x < MODEL_WIDTH; x++) {
        	for (int y = 0; y < MODEL_HEIGHT; y++){  
    			x1 = x * SCALING_FACTOR;
        		y1 = y * SCALING_FACTOR;

        		if (model.get(x)[y] != 0) {
            		if (y == playerRow && x == PLAYER_COLUMN) {
            			timer.stop(); //Crash...
            		}
            		g2.setColor(Color.BLACK);
            		g2.fillRect(x1, y1, SCALING_FACTOR, SCALING_FACTOR);
        		}
        		
        		if (x == PLAYER_COLUMN && y == playerRow) {
        			if (timer.isRunning()) {
            			g2.drawImage(sprite.getNext(), x1, y1, null);
        			}else {
            			g2.drawImage(dyingSprite.getNext(), x1, y1, null);
        			}
        			
        		}
        	}
        }
        
        /*
         * Not pretty, but good enough for this project... The compiler will
         * tidy up and optimise all of the arithmetics with constants below.
         */
        g2.setFont(font);
        g2.setColor(Color.RED);
        g2.fillRect(1 * SCALING_FACTOR, 15 * SCALING_FACTOR, 400, 3 * SCALING_FACTOR);
        g2.setColor(Color.WHITE);
        g2.drawString("Time: " + (int)(time * (TIMER_INTERVAL/1000.0d)) + "s", 1 * SCALING_FACTOR + 10, (15 * SCALING_FACTOR) + (2 * SCALING_FACTOR));
        
        if (!timer.isRunning()) {
			g2.setFont(over);
			g2.setColor(Color.RED);
			g2.drawString("Game Over!", MODEL_WIDTH / 5 * SCALING_FACTOR, MODEL_HEIGHT / 2* SCALING_FACTOR);
        }
	}

	//Move the plane up or down
	public void move(int step) {
		playerRow += step;
	}
	
	
	/*
	 * ----------
	 * AUTOPILOT!
	 * ----------
	 * The following implementation randomly picks a -1, 0, 1 to control the plane. You 
	 * should plug the trained neural network in here. This method is called by the timer
	 * every TIMER_INTERVAL units of time from actionPerformed(). There are other ways of
	 * wiring your neural network into the application, but this way might be the easiest. 
	 *  
	 */
	private void autoMove() throws Exception {
        double[] gameState = sample(); // Sampling the current game state
        double networkOutput = net.process(gameState, Output.NUMERIC); // Getting the decision from the neural network

        // Custom logic based on the game state and potentially the distances to obstacles
        int action = determineExpectedAction(); // Deciding based on distances to obstacles
        move(action);
    }

	
	//Called every second by the timer 
	public void actionPerformed(ActionEvent e) {
        time++; // Update our timer
        this.repaint(); // Repaint the cavern

        // Update the next index to generate
        index++;
        index = (index == MODEL_WIDTH) ? 0 : index;

        generateNext(); // Generate the next part of the cave
        if (auto && time > 20)
            try {
                autoMove();
            } catch (Exception e1) {
                e1.printStackTrace();
            }

        if (time % 10 == 0) {
            double[] trainingRow = sample();
            System.out.println("Sampled State: " + Arrays.toString(trainingRow));


            // Determine the expected action based on the current game state
            int expectedAction = determineExpectedAction();

            // Save the game state and the determined action to CSV files
            writeDataToFile(trainingRow, expectedAction, "./game_state_data.csv", "./expected_action_data.csv");
        }
    }

	private int determineExpectedAction() {
	    // Indicates if there is a clear path ahead without needing to move up or down.
	    boolean isPathClear = this.safeSpots[2] == MODEL_HEIGHT;
	    int safeSpotsUp = this.safeSpots[1];
	    int safeSpotsDown = this.safeSpots[2];

	    // Enhanced logging for better understanding of decision-making.
	    System.out.println("Determining Expected Action:");
	    System.out.println("Safe Spots Up: " + safeSpotsUp);
	    System.out.println("Safe Spots Down: " + safeSpotsDown);
	    System.out.println("Is Path Clear: " + isPathClear);

	    // Additional condition to prefer staying on course if safe spots are above a comfortable threshold.
	    final int COMFORTABLE_THRESHOLD = 3;

	    if (isPathClear) {
	        System.out.println("Action: Stay (Path is clear)");
	        return 0;
	    }

	    // Prioritize moving towards the direction with more safe spots,
	    // but only if the difference is significant to warrant a move.
	    final int SIGNIFICANT_DIFFERENCE = 2;
	    if (safeSpotsUp - safeSpotsDown >= SIGNIFICANT_DIFFERENCE) {
	        System.out.println("Action: Move Up (More space above)");
	        return -1;
	    } else if (safeSpotsDown - safeSpotsUp >= SIGNIFICANT_DIFFERENCE) {
	        System.out.println("Action: Move Down (More space below)");
	        return 1;
	    }

	    // If moving is not significantly safer in either direction, prefer staying in place
	    // unless the space in the current direction falls below a comfortable threshold.
	    if (safeSpotsDown > COMFORTABLE_THRESHOLD || safeSpotsUp > COMFORTABLE_THRESHOLD) {
	        System.out.println("Action: Stay (Comfortable in current position)");
	        return 0;
	    }

	    // Default action to stay if conditions above do not trigger a move.
	    System.out.println("Action: Stay (Default)");
	    return 0;
	}




	private int calculateDistanceToObstacleAbove(int playerRow) {
	    int distance = 0;
	    // Loop upwards from the player's row to find the first obstacle
	    for (int i = playerRow - 1; i >= 0; i--) {
	        boolean obstacleFound = false;
	        for (int j = 0; j < MODEL_WIDTH; j++) {
	            if (model.get(j)[i] == ONE_SET) {
	                obstacleFound = true;
	                break;
	            }
	        }
	        if (obstacleFound) {
	            break;
	        }
	        distance++;
	    }
	    return distance;
	}

	private int calculateDistanceToObstacleBelow(int playerRow) {
	    int distance = 0;
	    // Loop downwards from the player's row to find the first obstacle
	    for (int i = playerRow + 1; i < MODEL_HEIGHT; i++) {
	        boolean obstacleFound = false;
	        for (int j = 0; j < MODEL_WIDTH; j++) {
	            if (model.get(j)[i] == ONE_SET) {
	                obstacleFound = true;
	                break;
	            }
	        }
	        if (obstacleFound) {
	            break;
	        }
	        distance++;
	    }
	    return distance;
	}






	
	// Method to write game state and expected action data to files
    private void writeDataToFile(double[] gameState, int action, String dataFilePath, String expectedFilePath) {
        // Convert gameState to a comma-separated string
        StringBuilder gameStateBuilder = new StringBuilder();
        for (double d : gameState) {
            gameStateBuilder.append(d).append(",");
        }
        String gameStateString = gameStateBuilder.toString();

        // Write game state data to the specified file
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(dataFilePath, true))) { // true for append mode
            bw.write(gameStateString); // Write gameStateString
            bw.newLine(); // Add a new line after each row
        } catch (IOException e) {
            System.err.println("An error occurred while writing game state data to file: " + e.getMessage());
        }

        // Write expected action data to the specified file
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(expectedFilePath, true))) { // true for append mode
            bw.write(action + ""); // Write the action
            bw.newLine(); // Add a new line after each row
        } catch (IOException e) {
            System.err.println("An error occurred while writing expected action data to file: " + e.getMessage());
        }
    }
	
	
	/*
	 * Generate the next layer of the cavern. Use the linked list to
	 * move the current head element to the tail and then randomly
	 * decide whether to increase or decrease the cavern. 
	 */
	private void generateNext() {
		var next = model.pollFirst(); 
		model.addLast(next); //Move the head to the tail
		Arrays.fill(next, ONE_SET); //Fill everything in
		
		
		//Flip a coin to determine if we could grow or shrink the cave
		var minspace = 4; //Smaller values will create a cave with smaller spaces
		prevTop += current().nextBoolean() ? 1 : -1; 
		prevBot += current().nextBoolean() ? 1 : -1;
		prevTop = max(MIN_TOP, min(prevTop, prevBot - minspace)); 		
		prevBot = min(MIN_BOTTOM, max(prevBot, prevTop + minspace));

		//Fill in the array with the carved area
		Arrays.fill(next, prevTop, prevBot, ZERO_SET);
	}
	
	
	
	/*
	 * Use this method to get a snapshot of the 30x20 matrix of values
	 * that make up the game grid. The grid is flatmapped into a single
	 * dimension double array... (somewhat) ready to be used by a neural 
	 * net. You can experiment around with how much of this you actually
	 * will need. The plane is always somehere in column PLAYER_COLUMN
	 * and you probably do not need any of the columns behind this. You
	 * can consider all of the columns ahead of PLAYER_COLUMN as your
	 * horizon and this value can be reduced to save space and time if
	 * needed, e.g. just look 1, 2 or 3 columns ahead. 
	 * 
	 * You may also want to track the last player movement, i.e.
	 * up, down or no change. Depending on how you design your neural
	 * network, you may also want to label the data as either okay or 
	 * dead. Alternatively, the label might be the movement (up, down
	 * or straight). 
	 *  
	 */
    public double[] sample() {
        var vector = new double[5]; // for two columns (top and bottom distances) and the player row position
        int[] safeSpots = new int[4]; // for holding the counts of safe spots for the two columns

        for (int j = 0; j < 2; j++) {
            byte[] frontColumn = model.get((PLAYER_COLUMN + 1 + j) % MODEL_WIDTH);
            int top = 0;
            int bott = 0;

            for (int i = 0; i < MODEL_HEIGHT; i++) {
                if (frontColumn[i] == ONE_SET) {
                    top++;
                } else {
                    break; // Exiting loop when first empty spot is found
                }
            }
            
            for (int i = MODEL_HEIGHT - 1; i >= 0; i--) {
                if (frontColumn[i] == ONE_SET) {
                    bott++;
                } else {
                    break; // Exiting loop when first empty spot from bottom is found
                }
            }

            // Normalize the counts by dividing by MODEL_HEIGHT
            vector[j * 2] = (MODEL_HEIGHT - top) / (double) MODEL_HEIGHT;
            vector[j * 2 + 1] = (MODEL_HEIGHT - bott) / (double) MODEL_HEIGHT;

            // Correct calculation for safe spots based on empty spots found
            safeSpots[j * 2] = MODEL_HEIGHT - top - bott; // Correctly account for safe spots at the top
            safeSpots[j * 2 + 1] = safeSpots[j * 2]; // Assuming symmetric safe spots for simplicity
        }

        vector[4] = playerRow / (double) MODEL_HEIGHT; // Normalizing player row position
        this.safeSpots = safeSpots; // Updating the class variable

        return vector;
    }


	
	
	/*
	 * Resets and restarts the game when the "S" key is pressed
	 */
	public void reset() {
		model.stream() 		//Zero out the grid
		     .forEach(n -> Arrays.fill(n, 0, n.length, ZERO_SET));
		playerRow = 11;		//Centre the plane
		time = 0; 			//Reset the clock
		timer.restart();	//Start the animation
	}
}