package moa.streams.generators;

import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.DenseInstance;

import moa.core.Example;
import moa.core.FastVector;

import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;

import java.util.Random;

import moa.core.InstanceExample;

import com.yahoo.labs.samoa.instances.InstancesHeader;

import moa.core.ObjectRepository;
import moa.options.AbstractOptionHandler;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;

import moa.streams.InstanceStream;
import moa.tasks.TaskMonitor;

// Exclusively provided as part of submission  of 'An ensemble for classification in multi-class streams 
// with class-based concept drift' for ECML 2019. Not to be used for any other purpose.

public class RandomUniformGenerator extends AbstractOptionHandler implements InstanceStream {

    protected InstancesHeader streamHeader;
    protected int instancesGenerated = 0;
	private int numClasses;
	private int numFeatures;
	private double predictorNoise;
	private double responseNoise;
	private double boundary;
	protected double driftPointAdj;
	protected boolean invert = false;
	private long randomNumberSeed;
	private double majorityClassProp;
	private boolean balanceClasses;
	
	protected Random rng;
    
	@Override
	public String getPurposeString() {
		return "Generates Bernoulli stream.";
	}
	
	private static final long serialVersionUID = 1L;
	
	// Class boundaries = (class_label + 1)/numClasses + driftPointadj
    public IntOption numClassesOption = new IntOption("numClasses", 'c',
            "Num of classes instances are divided between",
            2,2,500);
    
    public IntOption numFeaturesOption = new IntOption("numFeatures", 'f',
            "Num of features per instance",
            1,1,10000);
    
    public FloatOption boundaryOption = new FloatOption("boundary", 'd',
            "Location of boundary between class 0 and 1",
            -0.2, -1, 1);
    
    public FloatOption predictorNoiseOption = new FloatOption("predictorNoise", 'p',
            "Degree predictor variables are perturbed",
            0, 0, 1);
    
    public FloatOption responseNoiseOption = new FloatOption("responseNoise", 'r',
            "Chance of class being perturbed",
            0, 0, 1);
    
    public FlagOption invertOption = new FlagOption("invert",
            'v', "Reverse ordering of classes.");
    
    public FlagOption setClassBalanceOption = new FlagOption("balanceClasses", 'b',
            "Manually balance classes according to majorityClassProp");
    
    public FloatOption majorityClassPropOption = new FloatOption("majorityClassProp", 'j',
            "Proportion of instances of class 0",
            0.5, 0, 1);
    
    // RandomNumberSeed
    public IntOption instanceRandomSeedOption = new IntOption("instanceRandomSeed", 'i',
            "Seed for generating random numbers",
            1);

	@Override
	public InstancesHeader getHeader() {
        return this.streamHeader;
	}


	@Override
	public boolean isRestartable() {
		return true;
	}

	@Override
	public void restart() {
		instancesGenerated = 0;
	}

	@Override
	public void getDescription(StringBuilder sb, int indent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void prepareForUseImpl(TaskMonitor monitor,
			ObjectRepository repository) {
        
		// reset stream generating characteristics
		setNumFeatures(numFeaturesOption.getValue());
		setNumClasses(numClassesOption.getValue());
		setBoundary(boundaryOption.getValue());
		setPredictorNoise(predictorNoiseOption.getValue());
		setResponseNoise(responseNoiseOption.getValue());
		setRandomNumberSeed(instanceRandomSeedOption.getValue());
		setClassBalanceOption(setClassBalanceOption.isSet());
		
		if (this.invertOption.isSet()) {
           invert = true;
        }
		
		// generate header
        FastVector featureLabels = new FastVector();
        for (int i = 0; i < numFeatures; i++) {
        	featureLabels.addElement(new Attribute("feature" + (i)));
        }
        
        FastVector classLabels = new FastVector();
        for (int i = 0; i < numClasses; i++) {
            classLabels.addElement("class" + (i + 1));
        }
        
        featureLabels.addElement(new Attribute("class", classLabels));
        this.streamHeader = new InstancesHeader(new Instances(
                getCLICreationString(InstanceStream.class), featureLabels, 0));
        this.streamHeader.setClassIndex(this.streamHeader.numAttributes() - 1);
        restart();
	}
	
	public void setClassBalanceOption(Boolean b){
		this.balanceClasses = b;
	}
	
	public void setMajorityClassProp(Double j){
		this.majorityClassProp = j;
	}
	
	public void setNumFeatures(Integer i){
		this.numFeatures = i;
	}
	
	public void setNumClasses(Integer i){
		this.numClasses = i;
	}
	
	public void setBoundary(Double d){
		this.boundary = d;
	}

	public void setPredictorNoise(Double d){
		this.predictorNoise = d;
	}
	
	public void setResponseNoise(Double d){
		this.responseNoise = d;
	}
	
	public void setRandomNumberSeed(Integer i){
		this.randomNumberSeed = i;
		this.rng = new Random(randomNumberSeed);
	}
	
	@Override
	public long estimatedRemainingInstances() {
		return -1;
	}

	@Override
	public boolean hasMoreInstances() {
		return true;
	}

	@Override
	public InstanceExample nextInstance() {
		
        // construct instance
        InstancesHeader header = getHeader();
        Instance inst = new DenseInstance(header.numAttributes());
        inst.setDataset(header);
        
        double varsum = 0;
		for (int i = 0; i < numFeatures; i++) {
			double d = rng.nextFloat() + (rng.nextFloat()*2*predictorNoise - predictorNoise);
			varsum = varsum + d;
			inst.setValue(i, d);
		}
		
		int group = 0;
		if(invert){
			for (int i = 1; i < numClasses; i++) {
				if(varsum >= 1 - boundary){
					group = 0;
					break;
				}
					
				else if (varsum <= 1 - boundary - (i - 1) * (1 - boundary)/(numClasses - 1) 
						&& varsum > 1 - boundary - i * (1 - boundary)/(numClasses - 1)){
					group = i;
					break;
				}
			}
		} else{
			for (int i = 1; i < numClasses; i++) {
				if(varsum <= boundary){
					group = 0;
					break;
				}
					
				else if (varsum > boundary + (i - 1) * (1 - boundary)/(numClasses - 1) 
						&& varsum <= boundary + i * (1 - boundary)/(numClasses - 1)){
					group = i;
					break;
				}
			}
		}

		if(responseNoise > 0.0 & rng.nextFloat() < responseNoise){
			int new_group = group;
			while(new_group == group){
				new_group = rng.nextInt(numClasses);
			}
			group = new_group;
		}
	
		inst.setClassValue(group);
        instancesGenerated++;
        
        return new InstanceExample(inst);
	}

}


