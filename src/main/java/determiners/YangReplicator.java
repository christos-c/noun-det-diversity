package determiners;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.stats.Counter;
import edu.illinois.cs.cogcomp.core.utilities.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Replication of the experiments of Yang (2013) and the results of the simulation experiments of
 * Silvey and Christodoulopoulos (2016).
 *
 * The preprocessed data (cleaned and tagged) need to reside in a single folder with names: {@code <child>.txt}
 *
 * @author Christos Christodoulopoulos
 */
public class YangReplicator {
    private static final boolean useZipfianDetBias = false;

    public static void main(String[] args) throws IOException {
        System.out.println("child\t#samples\t#noun-types\tempirical\tpredicted\tsimulated");
        for (String child : new String[]{"naomi", "eve", "sarah", "adam", "peter", "nina"})
            new YangReplicator(child);
    }

	public YangReplicator(String child) throws IOException {
        String dataFolder = "data/tagged";
		String file = dataFolder + File.separator + child + ".txt";

		Counter<Pair<String, String>> sampleCounter = new Counter<>();
		Counter<String> detInSampleCounter = new Counter<>();
		List<String> determiners = new ArrayList<>();
		Counter<String> nounInSampleCounter = new Counter<>();
		List<String> nouns = new ArrayList<>();
		List<String> lines = removeRepeatedSents(LineIO.read(file));
		Pair<String, String> prevPair = null;
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i);
			String[] splits = line.split("\\s+");
			String word = splits[0];
			String pos = splits[1];
			if (pos.equals(",") || pos.equals("SENT")) continue;
			if (pos.equals("NN") && getDet(lines.get(i - 1)) != null) {
				String det = getDet(lines.get(i - 1));
				Pair<String, String> detNounPair = new Pair<>(det, word);
				if (prevPair != null && detNounPair.equals(prevPair)) continue;
				determiners.add(det);
				nouns.add(word);

				detInSampleCounter.incrementCount(det);
				sampleCounter.incrementCount(detNounPair);
				nounInSampleCounter.incrementCount(word);
				prevPair = new Pair<>(detNounPair.getFirst(), detNounPair.getSecond());
			}
			else if (!pos.equals("DT")) prevPair = null;
		}
		Collections.shuffle(determiners);
		Counter<Pair<String,String>> randomSample = new Counter<>();
		for (String noun : nouns) {
			String determiner = determiners.remove(0);
			randomSample.incrementCount(new Pair<>(determiner, noun));
		}

		double empiricalProb = getEmpiricalProb(sampleCounter, nounInSampleCounter);
		double simulatedProb = getEmpiricalProb(randomSample, nounInSampleCounter);
		double predictedProb = getPredictedProb(detInSampleCounter, nounInSampleCounter.size(), sampleCounter.getTotal());

		System.out.println(child + "\t" + sampleCounter.getTotal() + "\t" + nounInSampleCounter.size() + "\t" +
				StringUtils.getFormattedTwoDecimal(empiricalProb) + "\t" +
				StringUtils.getFormattedTwoDecimal(predictedProb) + "\t" +
				StringUtils.getFormattedTwoDecimal(simulatedProb));
	}

	private double getEmpiricalProb(Counter<Pair<String, String>> pairCounter, Counter<String> nounWithDetCounter) {
		double totalNounsInPair = nounWithDetCounter.size();
		double totalDiverse = 0;
		for (String noun : nounWithDetCounter.items()) {
			double countA = pairCounter.getCount(new Pair<>("a", noun));
			double countThe = pairCounter.getCount(new Pair<>("the", noun));
			if (countA > 0 && countThe > 0)
				totalDiverse++;
		}
		return (totalDiverse / totalNounsInPair) * 100;
	}

	private double getPredictedProb(Counter<String> detInSampleCounter, int numNounsTypes, double sampleSize) {
		double harmonic = getHarmonic(numNounsTypes);
		double totalExpProb = 0;

		double detAProb, detTheProb;
        if (useZipfianDetBias) {
            detAProb = detInSampleCounter.getCount("a") / detInSampleCounter.getTotal();
            detTheProb = detInSampleCounter.getCount("the") / detInSampleCounter.getTotal();
        }
        else {
            detTheProb = 2.0/3.0;
            detAProb = 1 - detTheProb;
        }

        for (int rank = 1; rank < numNounsTypes + 1; rank++) {
			double nounProb = 1 / (rank * harmonic);
			double pow = Math.pow(1 - nounProb, sampleSize);
			double probSum = Math.pow((detAProb * nounProb + 1 - nounProb), sampleSize) - pow;
			probSum += Math.pow((detTheProb * nounProb + 1 - nounProb), sampleSize) - pow;
			double expectedProb = (1 - pow) - probSum;
			totalExpProb += expectedProb;
		}
		return (totalExpProb/ numNounsTypes) * 100;
	}

	private double getHarmonic(int numTypes) {
		double harm = 0;
		for (double i = 1; i < numTypes + 1; i++) {
			harm += 1/i;
		}
		return harm;
	}

	private String getDet(String line) {
		String lemma = line.split("\\s+")[2];
		if (lemma.equals("a") || lemma.equals("the"))
			return lemma;
		return null;
	}

	private List<String> removeRepeatedSents(List<String> lines) {
		List<String> cleanSents = new ArrayList<>();
		String sent = "", prevSent = "";
		List<String> sentLines = new ArrayList<>();
		for (String line : lines) {
			String[] splits = line.split("\\s+");
			String word = splits[0];
			String pos = splits[1];
			sent += word + " ";
			sentLines.add(line);
			if (pos.equals("SENT")) {
				if (!sent.equals(prevSent)) {
					cleanSents.addAll(sentLines);
				}
				sentLines.clear();
				prevSent = sent;
				sent = "";
			}
		}
		return cleanSents;
	}
}
