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
// with class-based concept drift' for IJCNN 2019. Not to be used for any other purpose.

public class CIRCLESGeneratorMC extends AbstractOptionHandler implements InstanceStream {

    protected InstancesHeader streamHeader;
    protected int instancesGenerated = 0;
	private int startCircle;
	private int endCircle;
	private int numClasses;
	protected int driftPoint;
	protected double driftSlope;
	private long randomNumberSeed;
	private boolean balanceClasses = true;
	
	protected Random rng;
	
	protected double oldRadius;
	protected double newRadius;
    
	@Override
	public String getPurposeString() {
		return "Generates CIRCLES concept functions.";
	}
	
	private static final long serialVersionUID = 1L;
	
	// Circles: centre = {0.5, 0.5}, radius: 0 -> 0.2, 1 -> 0.25; 2 -> 0.3; 3 -> 0.35; 4 -> 0.4
    public IntOption startCircleOption = new IntOption("startCircle", 's',
            "Starting function for generating instances",
            0, 0, 4);
	
    public IntOption endCircleOption = new IntOption("endCircle", 'e',
            "End function for generating instances",
            1, 0, 4);
    
    public IntOption numClassesOption = new IntOption("numClasses", 'n',
            "Number of classes to represent",
            2, 2, 100);
    
    public IntOption driftPointOption = new IntOption("driftPoint", 'd',
            "Num of instances before starting drift",
            999000);
    
    // Increase in probability of using new concept by time-step once driftPoint is reached
    public FloatOption driftSlopeOption = new FloatOption("driftSlope", 'r',
            "Speed in introducing new concept",
            0.001, 0, 1);
    
    // RandomNumberSeed
    public IntOption instanceRandomSeedOption = new IntOption("instanceRandomSeed", 'i',
            "Seed for generating random numbers",
            1);

	@Override
	public InstancesHeader getHeader() {
        return this.streamHeader;
	}

	public void setStartCircle(Integer i){
		this.startCircle = i;
		this.oldRadius = startCircle * 0.05 + 0.2;
	}
	
	public void setEndCircle(Integer i){
		this.endCircle = i;
		this.newRadius = endCircle * 0.05 + 0.2;
	}
	
	public void setDriftSlope(Double d){
		this.driftSlope = d;
	}
	
	public void setDriftPoint(Integer i){
		this.driftPoint = i;
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
		double x = 0;
		double y = 0;
		double group = 0, concept = 0;
		
		double[] radius = new double[numClasses-1];
		// decide class and concept. radius describes the decision barrier for two class problem further classes are created within this barrier
		group = (int)(rng.nextDouble() / ((double)1/(double)numClasses));
		double newConceptProb = Math.max(0, (instancesGenerated - driftPoint) * driftSlope);
		radius[0] = (rng.nextDouble() <= newConceptProb ? newRadius : oldRadius);
		
		if(radius[0] == newRadius){
			//System.out.println("gogo");
		}
		
		// Calculate circle radii by equally splitting radius in inner circle
		for(int i = 1; i < (numClasses-1); i ++){
			radius[i] = radius[i-1]-(radius[0]/(double)(numClasses-1));
		}
		
		// find coordinates
		// uses http://stackoverflow.com/questions/481144/equation-for-testing-if-a-point-is-inside-a-circle
		boolean validValues = false;
		while(!validValues){
			int selectedClass = 0;
			x = rng.nextDouble();
			y = rng.nextDouble();
			for(int i = numClasses - 1; i >= 1; i--){
				if((Math.pow(x - 0.5, 2) + Math.pow(y - 0.5, 2)) < Math.pow(radius[i-1], 2)){
					selectedClass = i;
					break;
				}
			}
			if(selectedClass == (int)group) validValues = true;	
		}

        // construct instance
        InstancesHeader header = getHeader();
        Instance inst = new DenseInstance(header.numAttributes());
        inst.setValue(0, x);
        inst.setValue(1, y);
        inst.setDataset(header);
        inst.setClassValue(group);
        
        instancesGenerated++;
        
        return new InstanceExample(inst);
	}

	@Override
	public boolean isRestartable() {
		return true;
	}

	@Override
	public void restart() {
		instancesGenerated = 0;
		this.rng = new Random(randomNumberSeed);
	}

	@Override
	public void getDescription(StringBuilder sb, int indent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void prepareForUseImpl(TaskMonitor monitor,
			ObjectRepository repository) {
        
		// reset stream generating characteristics
		setStartCircle(startCircleOption.getValue());
		setEndCircle(endCircleOption.getValue());
		setDriftPoint(driftPointOption.getValue());
		setDriftSlope(driftSlopeOption.getValue());
		setRandomNumberSeed(instanceRandomSeedOption.getValue());
		this.numClasses = numClassesOption.getValue();
		
		
		// generate header
		FastVector attributes = new FastVector();
        attributes.addElement(new Attribute("x"));
        attributes.addElement(new Attribute("y"));

        FastVector classLabels = new FastVector();
        for (int i = 0; i < numClasses; i++) {
            classLabels.addElement("class" + (i + 1));
        }
        attributes.addElement(new Attribute("class", classLabels));
        this.streamHeader = new InstancesHeader(new Instances(
                getCLICreationString(InstanceStream.class), attributes, 0));
        this.streamHeader.setClassIndex(this.streamHeader.numAttributes() - 1);
        restart();
	}
    
    
}


