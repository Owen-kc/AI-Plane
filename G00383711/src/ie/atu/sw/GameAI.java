package ie.atu.sw;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jhealy.aicme4j.NetworkBuilderFactory;
import jhealy.aicme4j.net.Activation;
import jhealy.aicme4j.net.Loss;
import jhealy.aicme4j.net.Output;

public class GameAI {

	// Method to train and test the neural network
    public void trainAndTest(String dataFilePath, String expectedFilePath) throws Exception {
    	// Load data from CSV files
        double[][] data = loadCSV(dataFilePath);
        double[][] expected = transformExpectedOutput(loadCSV(expectedFilePath));

        // Define input size
        int inputSize = 6;

        // Neural network parameters
        var net = NetworkBuilderFactory.getInstance().newNetworkBuilder()
                .inputLayer("Input", inputSize)
                .hiddenLayer("Hidden1", Activation.RELU, 30)
                .hiddenLayer("Hidden2", Activation.RELU, 15)
                .outputLayer("Output", Activation.TANH, expected[0].length) 
                .train(data, expected, 0.001, 0.9, 50000, 0.00001, Loss.SSE)
                .save("./resources/game_ai.data")	// Save file as game_ai.data
                .build();

        // Test the trained network
        for (int i = 0; i < data.length; i++) {
            var predicted = net.process(data[i], Output.NUMERIC_ROUNDED);
            var actual = expected[i][0];
            System.out.println("Predicted: " + predicted + ", Actual: " + actual + "\t" +
                    (Math.round(actual) == predicted ? "[OK]" : "[Error]"));
        }
    }

    // Method to transform expected output (if needed) 
    // If using a single neuron, return data unchanged, but can update this method to transform the data (if using multiple neurons, etc)
    private double[][] transformExpectedOutput(double[][] rawExpected) {
        return rawExpected;
    }

    // Load the data from CSV file
    private double[][] loadCSV(String filePath) throws IOException {
        List<double[]> dataList = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",");
                double[] data = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    data[i] = Double.parseDouble(parts[i]);
                }
                dataList.add(data);
            }
        }
        return dataList.toArray(new double[0][]);
    }

    public static void main(String[] args) throws Exception {
        String dataFilePath = "./resources/game_state_data.csv";
        String expectedFilePath = "./resources/expected_action_data.csv";
        new GameAI().trainAndTest(dataFilePath, expectedFilePath);
    }
}
