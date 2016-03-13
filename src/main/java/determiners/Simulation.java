package determiners;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.stats.OneVariableStats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Creates a synthetic Zipfian dataset of noun-determiner pairs (with a determiner bias) and compares
 * the resulting empirical diversity with that predicted by the model of Yang (2013) or a simpler model.
 *
 * @author Christos Christodoulopoulos
 */
public class Simulation {
    /** The (Zipfian) determiner bias */
    private static final double bias = 2.0/3.0;
    /** The maximum number of types to consider */
    private static final int maxTypes = 1000;
    /** Multiplied to maxTypes to get the frequency of the most frequent noun */
    private static final double multiplier = 1.0;
    /** Run the experiment maxRounds to collect mean/std. err */
    private static final int maxRounds = 50;
    /** Whether to use a simpler approximation for the predicted diversity */
    private static final boolean useSimpleModel = false;
    /** Which number of types to output raw results for */
    private static final int numTypesRawResults = 100;

    private static Random random = new Random();
    private static OneVariableStats predicted = new OneVariableStats();
    private static OneVariableStats empirical = new OneVariableStats();
    private static List<String> rawNumbers;

    public static void main(String[] args) throws IOException {
        List<String> results = new ArrayList<>();
        results.add("totalTypes\tmeasure\tvalue\tstderr");
        int increment = 10;
        for (int totalTypes = 10; totalTypes <= maxTypes; totalTypes += increment) {
            int maxNounFreq = (int) Math.round(totalTypes * multiplier);
            Pair<Double, Double> pair;
            // Simulate maxRounds to collect mean/std. err
            for (int i = 0; i < maxRounds; i++) {
                pair = simulate(totalTypes, maxNounFreq, bias, useSimpleModel, false);
                predicted.add(pair.getFirst());
                empirical.add(pair.getSecond());
            }

            double predErr = predicted.stdErr();
            if (Double.isNaN(predErr)) predErr = 0;
            double empiricalErr = empirical.stdErr();
            results.add(totalTypes + "\tpredicted\t" + predicted.mean() + "\t" + predErr);
            results.add(totalTypes + "\tempirical\t" + empirical.mean() + "\t" + empiricalErr);
            predicted.reset();
            empirical.reset();
        }
        LineIO.write("sim-average.csv", results);

        // Simulate one round to get the raw numbers
        rawNumbers = new ArrayList<>();
        rawNumbers.add("nounFreq\texpectedDiversity\tempiricalDiversity");
        int maxNounFreq = (int) Math.round(numTypesRawResults * multiplier);
        Pair<Double, Double> pair = simulate(numTypesRawResults, maxNounFreq, bias, useSimpleModel, true);
        rawNumbers.add(System.lineSeparator() + "Sim:\t" + pair.getFirst() + "\t" + pair.getSecond());
        LineIO.write("sim-raw@" + numTypesRawResults + ".csv", rawNumbers);
    }

    /**
     * Runs one round of the simulation.
     *
     * @param totalTypes The number of total noun types
     * @param maxNounFreq The frequency of the most frequent noun
     * @param bias The determiner bias
     * @param useSimpleModel Whether to use a simpler model (set to {@code false} to use the model from Yang (2013)
     * @param addRawNumbers Whether to add the raw results of the run to the output file
     * @return A pair of values for predicted and empirical diversity
     */
    private static Pair<Double, Double> simulate(int totalTypes, int maxNounFreq, double bias,
                                                 boolean useSimpleModel, boolean addRawNumbers) {
        double aBias = (1 - bias);
        double harmonic = 0;
        for (double i = 1; i < totalTypes + 1; i++) {
            harmonic += 1/i;
        }

        double totalDiversity = 0;
        int totalSampleSize = 0;
        for (int rank = 1; rank <= totalTypes; rank++) {
            double sampleSize = maxNounFreq * (1.0 / rank);
            totalSampleSize += sampleSize;
        }
        double totalExpDiversity = 0;

        for (int rank = 1; rank <= totalTypes; rank++) {
            double nounProb = 1 / (rank * harmonic);

            // Calculate the predicted diversity of the sample
            double expectedDiversity;
            if (useSimpleModel) {
                double exclusiveA = Math.pow(1 - bias, nounProb * totalSampleSize);
                double exclusiveThe = Math.pow(bias, nounProb * totalSampleSize);
                expectedDiversity = 1 - (exclusiveA + exclusiveThe);
            }
            else {
                double nonNounProb = 1 - nounProb;
                double pow = Math.pow(nonNounProb, totalSampleSize);
                double probSum = Math.pow((aBias * nounProb + nonNounProb), totalSampleSize) - pow;
                probSum += Math.pow((bias * nounProb + nonNounProb), totalSampleSize) - pow;
                expectedDiversity = (1 - pow) - probSum;
            }

            // Calculate the (simulated) empirical diversity of the sample
            int empiricalDiversity = 0;
            int nounFreq = (int) Math.round(maxNounFreq * (1.0 / rank));
            // If there is only one noun the diversity is always 0
            if (nounFreq != 1) {
                int theDraws = 0, aDraws = 0;
                for (int i = 0; i < nounFreq; i++) {
                    // Keep sampling (with bias) until we select both determiners
                    if (random.nextFloat() > bias) {
                        aDraws++;
                    }
                    else theDraws++;
                    if (theDraws != 0 && aDraws != 0) {
                        empiricalDiversity = 1;
                        break;
                    }
                }
            }
            totalDiversity += empiricalDiversity;
            totalExpDiversity += expectedDiversity;

            if (addRawNumbers)
                rawNumbers.add(nounFreq + "\t" + expectedDiversity + "\t" + empiricalDiversity);
        }

        double predicted = (totalExpDiversity / totalTypes) * 100;
        double empirical = (totalDiversity / totalTypes) * 100;
        return new Pair<>(predicted, empirical);
    }
}
