package moa.classifiers.meta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.ZeroException;
import org.apache.commons.math3.stat.inference.ChiSquareTest;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.InstancesHeader;
import com.yahoo.sketches.quantiles.DoublesSketch;
import com.yahoo.sketches.quantiles.QuantileKSTest;
import com.yahoo.sketches.quantiles.UpdateDoublesSketch;

import moa.classifiers.AbstractClassifier;
import moa.classifiers.Classifier;
import moa.classifiers.MultiClassClassifier;
import moa.core.Measurement;
import moa.options.ClassOption;
import moa.options.FlagOption;
import moa.options.FloatOption;
import moa.streams.InstanceStream;

// Exclusively provided as part of submission  of 'An ensemble for classification in multi-class streams 
// with class-based concept drift' for ECML 2019. Not to be used for any other purpose.

public class CSE extends AbstractClassifier implements MultiClassClassifier {

    public ClassOption baseLearnerOption = new ClassOption("baseLearner", 'l',
            "Base classifiers to train.", Classifier.class, "moa.classifiers.trees.HoeffdingTree");//"bayes.NaiveBayes");
    
	public IntOption windowSizeOption = new IntOption("windowSize", 'n', "Size of window of recent class instances.",
			128, 1, Integer.MAX_VALUE);
    
	public IntOption qsKOption = new IntOption("qsK", 'k', "k parameter for quantile sketches.",
			256, 2, Integer.MAX_VALUE);
	
	public FloatOption pValueOption = new FloatOption("pValueAlpha", 'p', "Alpha setting for test",
			0.002, 0, Integer.MAX_VALUE);
	
    public IntOption instanceRandomSeedOption = new IntOption("instanceRandomSeed", 'i',
            "Seed for generating random numbers", 1);
	
	public static FlagOption balanceClassifierExamplesOption = new FlagOption("balanceClassifierExamples", 't', "Never train on more neg than positive examples");
	
	protected InstancesHeader ih;
    protected int numClasses = -1;
    protected int windowSize;
    protected ArrayList<Classifier> classClassifiers;
    protected ArrayList<LinkedList<Instance>> classWindows;
    protected ArrayList<ArrayList<UpdateDoublesSketch>> classQuantileSketches;
    protected ArrayList<ArrayList<ArrayList<Integer>>> classNominalCounts;
    protected ArrayList<Integer> numericAtts;
    protected ArrayList<Integer> nominalAtts;
    protected int instSeen;
    protected int numClassDrifts;
    protected boolean balanceClassifierExamples;
    protected ArrayList<Integer> nominalVarsNumValues;
    protected Random rng;
    protected static double pValueAlpha;
    
    Integer[] positiveExamplesSeen;
    Integer[] negativeExamplesSeen;
    
	@Override
	public boolean isRandomizable() {
		// TODO Auto-generated method stub
		return false;
	}

	//Initialise ensemble of classifiers, instance windows per class and quantile sketches per class-feature
	public void initialise(){
		instSeen = 0;
		numClassDrifts = 0;
		this.rng = new Random(instanceRandomSeedOption.getValue());
		this.pValueAlpha = pValueOption.getValue();

		balanceClassifierExamples = balanceClassifierExamplesOption.isSet();
		classClassifiers = new ArrayList<Classifier>();
		classWindows = new ArrayList<LinkedList<Instance>>();
		classQuantileSketches = new ArrayList<ArrayList<UpdateDoublesSketch>>();
		classNominalCounts = new ArrayList<ArrayList<ArrayList<Integer>>>();
		numericAtts = new ArrayList<Integer>();
		nominalAtts = new ArrayList<Integer>();
		
		this.positiveExamplesSeen = new Integer[numClasses];
		this.negativeExamplesSeen = new Integer[numClasses];
		Arrays.fill(positiveExamplesSeen, 0);
		Arrays.fill(negativeExamplesSeen, 0);
		
		//Get num of values for nominal attributes
		nominalVarsNumValues = new ArrayList<Integer>();
		for(int a = 0; a < ih.numAttributes(); a++){
			if(ih.attribute(a).isNominal() && a != ih.classIndex()) {
				nominalVarsNumValues.add(ih.attribute(a).getAttributeValues().size());
				nominalAtts.add(a);
			} else if (a != ih.classIndex()){
				nominalVarsNumValues.add(0);
				numericAtts.add(a);
			}
		}
		
		for(int i = 0; i < numClasses; i++){
	        Classifier classifier = ((Classifier) getPreparedClassOption(this.baseLearnerOption)).copy();
	        classifier.resetLearning();
	        classifier.prepareForUse();
	        classClassifiers.add(classifier);
	        classWindows.add(new LinkedList<Instance>());
	        classQuantileSketches.add(new ArrayList<UpdateDoublesSketch>());
	        classNominalCounts.add(new ArrayList<ArrayList<Integer>>());
	        //For each numeric feature, create a quantile sketch
	        for(Integer a:numericAtts){
	        	classQuantileSketches.get(i).add(DoublesSketch.builder().setK(qsKOption.getValue()).build());
	        }
	        for(Integer a:nominalAtts){
	        	classNominalCounts.get(i).add(new ArrayList<Integer>(Collections.nCopies((int)(nominalVarsNumValues.get(a)), 0)));
	        }
		}
	}
	
	@Override
	public double[] getVotesForInstance(Instance inst) {
		Instance instCopy = inst.copy();
		//double actualClass = inst.classValue();
		
		double[] votes = new double[numClasses];
		for(double d = 0; d < numClasses; d++){
			instCopy.setClassValue(0.0);
			double[] preds = classClassifiers.get((int)d).getVotesForInstance(instCopy);
			
			if(preds.length < 2){
				votes[(int)d] = 0.0;
			} else {
				//votes[(int)d] = normaliseVotes(classClassifiers.get((int)d).getVotesForInstance(instCopy))[1];
				votes[(int)d] = normaliseVotes(classClassifiers.get((int)d).getVotesForInstance(instCopy))[1];
			}
		}
		
		return votes;
	}

	//Clears ensemble, will initialise on arrival of first instance
	@Override
	public void prepareForUse() {
        this.classClassifiers = null; 
	}
	
	public int getNumClassDrifts(){
		return numClassDrifts;
	}
	
	public void resetLearningImpl(){
		prepareForUse();
		getOptions();
	}
	
	 public void setModelContext(InstancesHeader instHeader){
        numClasses = instHeader.numClasses();
        this.ih = instHeader;
        initialise();
	 }
	
	@Override
	public void trainOnInstanceImpl(Instance inst) {
		instSeen++;
		//Train classifier
		Instance instCopy = inst.copy();
		double actualClass = instCopy.classValue();
		
		double[] votes = new double[numClasses];
		for(double d = 0; d < numClasses; d++){
			
			if(actualClass == d){
				instCopy.setClassValue(1);
				positiveExamplesSeen[(int)d]++;
				classClassifiers.get((int)d).trainOnInstance(instCopy);
			} else if(balanceClassifierExamples) {
				instCopy.setClassValue(0);
				
				if(rng.nextDouble() < (double)positiveExamplesSeen[(int) d]/Math.max((double)(negativeExamplesSeen[(int) d] + positiveExamplesSeen[(int) d]), 1.0)){
					classClassifiers.get((int)d).trainOnInstance(instCopy);
					negativeExamplesSeen[(int)d]++;
				}
				//instCopy.setWeight(((double)positiveExamplesSeen[(int) d]/(double)negativeExamplesSeen[(int) d]));
			} else {
				instCopy.setClassValue(0);
				negativeExamplesSeen[(int)d]++;
				classClassifiers.get((int)d).trainOnInstance(instCopy);
			}

	        
		}
		
		//Add instance to window of recent instances and push older instances to QS/table of counts
		classWindows.get((int)actualClass).push(inst);
		if(classWindows.get((int)actualClass).size() > windowSizeOption.getValue()){
			Instance oldInst = classWindows.get((int)actualClass).removeLast();
			int index = 0;
			for(Integer a:numericAtts){
	        	classQuantileSketches.get((int)actualClass).get(index).update(oldInst.value(a));
	        	index++;
	        }
			index = 0;
			for(Integer a:nominalAtts){
	        	classNominalCounts.get((int)actualClass).get(index).set((int)oldInst.value(a), 
	        			classNominalCounts.get((int)actualClass).get(index).get((int)oldInst.value(a))+1);
	        	index++;
	        }
		}
			
		//Test for difference between QS and instWindow
		if(classWindows.get((int)actualClass).size() >= windowSizeOption.getValue() & 
				classQuantileSketches.get((int)actualClass).get(0).getN() >= windowSizeOption.getValue()
				& positiveExamplesSeen[(int)actualClass] % windowSizeOption.getValue() == 0){
			ArrayList<Double> qsPValues = new ArrayList<Double>();				
			
			int index = 0;
			for(Integer a:numericAtts){
				KolmogorovSmirnovTest ksTest = new KolmogorovSmirnovTest();
				QuantileKSTest ksTest2 = new QuantileKSTest();
	        	//Build QS for feature in window
	        	UpdateDoublesSketch qs = DoublesSketch.builder().setK(windowSizeOption.getValue()).build();
	        	for(Instance i:classWindows.get((int)actualClass)){
	        		qs.update(i.value(a));
	        	}
	        	
	        	//Test
				double tStat = QuantileKSTest.computeKSDelta(classQuantileSketches.get((int)actualClass).get(index), qs);
				qsPValues.add(ksTest.approximateP(tStat, 
						Math.min((int)(classQuantileSketches.get((int)actualClass).get(index).getK()),
								(int)(classQuantileSketches.get((int)actualClass).get(index).getN())), 
						(int)(classWindows.get((int)actualClass).size())));
				index++;
				
	        }
			
			index = 0;
			for(Integer a:nominalAtts){
	        	
				//Build table of counts for window
				long[] observed = new long[nominalVarsNumValues.get(a)];
				Arrays.fill(observed, 0);
	        	for(Instance i:classWindows.get((int)actualClass)){
	        		observed[(int)i.value(a)]++;
	        	}
	        	
	        	//Chi-square test table of counts for nominal attributes
				ChiSquareTest cst = new ChiSquareTest();
				long[] expected = convertIntegerListToLongArray(classNominalCounts.get((int)actualClass).get(index));
				index++;
				long[][] results = trimZeroes(observed, expected);
				if(results == null){
					continue;
				}
				
				double testStat = cst.chiSquareDataSetsComparison(results[0],results[1]);
				qsPValues.add(chiSquareTestDataSetsComparison(testStat,(double) results[1].length - 1));
				
	        }
	        if(aggregateP(qsPValues)){
	        	//System.out.println(instSeen + " - drift in class " + actualClass);
	        	numClassDrifts++;
	        	//Delete old QS and table of counts
	        	index = 0;
				for(Integer a:numericAtts){
	        		classQuantileSketches.get((int)actualClass).get(index).reset();
	        		index++;
	        	}
	        	index = 0;
				for(Integer a:nominalAtts){
					classNominalCounts.get((int)actualClass).set(index, 
						new ArrayList<Integer>(Collections.nCopies((int)(nominalVarsNumValues.get(a)), 0)));
	        		index++;
	        	}
	        	
				positiveExamplesSeen[(int)actualClass] = classWindows.get((int)actualClass).size();
				negativeExamplesSeen[(int)actualClass] = classWindows.get((int)actualClass).size();
				
	        	//Train a new model on window of instances and push them into the QS
	        	//System.out.println(instSeen + "Class drift for class " + ((int)actualClass));
	        	Classifier newClassifier = ((Classifier) getPreparedClassOption(this.baseLearnerOption)).copy();
	        	int windowSize = classWindows.get((int)actualClass).size();
	        	for(int i = 0; i < windowSize; i++){
	        		Instance windowInst = classWindows.get((int)actualClass).removeLast();
	        		//if(Math.random() > 0.5)
	        		Instance posExample = windowInst.copy();
	        		posExample.setClassValue(1.0);
	        		newClassifier.trainOnInstance(posExample);
	        		Instance negExample = getOtherInstance((int)actualClass);
	        		if(negExample != null){
		        		negExample.setClassValue(0.0);
		        		newClassifier.trainOnInstance(negExample);
	        		}
	        		index = 0;
					for(Integer a:numericAtts){
		        		classQuantileSketches.get((int)actualClass).get(index).update(windowInst.value(a));
		        		index++;
		        	}
	        		index = 0;
					for(Integer a:nominalAtts){
						classNominalCounts.get((int)actualClass).get(index).set((int)windowInst.value(a), 
			        			classNominalCounts.get((int)actualClass).get(index).get((int)windowInst.value(a))+1);
						

		        		index++;
		        	}
	        	}
	        	classClassifiers.set(((int)actualClass), newClassifier);
	        }
		}
	}
	
	private Instance getOtherInstance(int actualClass) {
		int new_class = actualClass;
		int instIndex = 0;
		while(0 < 1){
			new_class = (int)(rng.nextDouble() * numClasses);
			if(new_class != actualClass){
				if(classWindows.get(new_class).size() == 0) return null; //can't return classes from empty windows 
				instIndex = (int)(rng.nextDouble()*classWindows.get(new_class).size());
				break;
			}
		}
		return classWindows.get(new_class).get(instIndex).copy();
		
	}

	//Uses Benjamini & Yekutieli (2001) approach to adjust for multiple comparisons of p-values
	public static boolean aggregateP(ArrayList<Double> qsPValues) {
		Collections.sort(qsPValues, Collections.reverseOrder());
		double q = 0.0;
		for(double i = 0; i < qsPValues.size(); i++) q+= 1.0/(i+1);
		for(int i = 0; i < qsPValues.size(); i++){
			double z = qsPValues.size()/(qsPValues.size()-i);
			double new_val = q*(((double)qsPValues.size())/((double)(qsPValues.size()-i)))*qsPValues.get(i);
			if(q*(((double)qsPValues.size())/((double)(qsPValues.size()-i)))*qsPValues.get(i) < pValueAlpha) 
				return true;
		}
		return false;
	}

	
	@Override
	protected Measurement[] getModelMeasurementsImpl() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void getModelDescription(StringBuilder out, int indent) {
		// TODO Auto-generated method stub
		
	}
	
	private long[] convertIntegerListToLongArray(ArrayList<Integer> integerList) {
		long[] result = new long[integerList.size()];
		for(int i = 0; i < integerList.size(); i++){
			result[i] = integerList.get(i).longValue();
		}
		return result;
	}

	//Modified from apache package to directly take test statistic
    public double chiSquareTestDataSetsComparison(double testStat, double length)
            throws DimensionMismatchException, NotPositiveException, ZeroException,
            MaxCountExceededException {

            // pass a null rng to avoid unneeded overhead as we will not sample from this distribution
            final ChiSquaredDistribution distribution =
                    new ChiSquaredDistribution(null, length);
            return 1 - distribution.cumulativeProbability(testStat);

        }
	
	public double[] normaliseVotes(double[] votes){
		double sum = 0.0;
		double[] normVotes = new double[votes.length];
		for(double v:votes) sum += v;
		if(sum > 0){
			for(int i=0; i < votes.length; i++) normVotes[i] = votes[i]/sum;
		} else {
			for(int i=0; i < votes.length; i++) normVotes[i] = 1/numClasses;
		}
		return normVotes;
	}
	
	//Where two table of counts have zero for an entry, we remove both entries as being unobserved. Check chi-sq assumptions met
	public long[][] trimZeroes(long observed[], long expected[]){
		boolean[] zeroForBoth = new boolean[observed.length];
		int newLength = 0;
		for(int i = 0; i < observed.length; i++){
			if(observed[i] == 0 & expected[i] == 0) {
				zeroForBoth[i] = true;
			} else {
				zeroForBoth[i] = false;
				newLength++;
			}
			if(expected[i] < 5 & !zeroForBoth[i]) return null;
		}
		if(newLength < 2) return null;
		long[][] results = new long [2][newLength];
		if(newLength == observed.length){
			results[0] = observed;
			results[1] = expected;
			return results;
		}
		long observedNew[] = new long[newLength];
		long expectedNew[] = new long[newLength];
		int index = 0;
		for(int i = 0; i < observed.length; i++){
			if(!zeroForBoth[i]){
				observedNew[index] = observed[i];
				expectedNew[index] = expected[i];
				index++;
			}
		}
		results[0] = observedNew;
		results[1] = expectedNew;
		return results;
	}

	public int getK() {
		return qsKOption.getValue();
	}
	
	public int getW() {
		return windowSizeOption.getValue();
	}
	
	public double getP() {
		return pValueAlpha;
	}
}
