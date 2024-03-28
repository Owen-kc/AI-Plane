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

    public void trainAndTest(String dataFilePath, String expectedFilePath) throws Exception {
        double[][] data = loadCSV(dataFilePath);
        double[][] expected = transformExpectedOutput(loadCSV(expectedFilePath));

        // Assuming 5 inputs as per your game state vector
        int inputSize = 5;
        
        // Assuming a single output for simplicity, but you can adjust as needed
        int outputSize = 1; // Adjust if using one-hot encoded output

        var net = NetworkBuilderFactory.getInstance().newNetworkBuilder()
                .inputLayer("Input", inputSize)
                .hiddenLayer("Hidden1", Activation.RELU, 30)
                .hiddenLayer("Hidden2", Activation.RELU, 15)
                // If sticking with single output, use TANH to allow for -1 to 1 range
                .outputLayer("Output", Activation.TANH, expected[0].length) 
                .train(data, expected, 0.001, 0.9, 50000, 0.00001, Loss.SSE)
                .save("./game_ai.data")
                .build();

        // Optional: Test the trained network
        for (int i = 0; i < data.length; i++) {
            var predicted = net.process(data[i], Output.NUMERIC_ROUNDED);
            var actual = expected[i][0]; // Adjust based on actual output structure
            System.out.println("Predicted: " + predicted + ", Actual: " + actual + "\t" +
                    (Math.round(actual) == predicted ? "[OK]" : "[Error]"));
        }
    }

    private double[][] transformExpectedOutput(double[][] rawExpected) {
        // If using a single output neuron, this transformation may be unnecessary
        // For a more complex mapping or one-hot encoding, implement transformation logic here
        return rawExpected;
    }

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
        String dataFilePath = "./game_state_data.csv";
        String expectedFilePath = "./expected_action_data.csv";
        new GameAI().trainAndTest(dataFilePath, expectedFilePath);
    }
}
