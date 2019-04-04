package moa.streams.generators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import com.yahoo.labs.samoa.instances.InstancesHeader;

import moa.core.Example;
import moa.core.FastVector;
import moa.core.ObjectRepository;
import moa.options.AbstractOptionHandler;
import moa.streams.InstanceStream;
import moa.tasks.TaskMonitor;

// Stream that has a given chance every instance per class for the class to 'drift' (perturb attributes)

public class RandomDriftStream extends AbstractOptionHandler implements InstanceStream {

	protected InstancesHeader streamHeader;
	int instancesGenerated;
	InstanceStream sourceStream;
	int numClasses;
	int interval;
	int instanceRandomSeed;
	protected Random rng;
	double driftChance;
	int numAtts;
	double[][] attScale;
	double[][] attTranslate;
	int[][] adjNominals;
	int[] numNominalValues;
	ArrayList<Integer> nominalVars;
	
    public FloatOption driftChanceOption = new FloatOption("driftChance", 'n',
            "Chance per instance to drift", 0.0001);   
	
    // RandomNumberSeed
    public IntOption instanceRandomSeedOption = new IntOption("instanceRandomSeed", 'i',
            "Seed for generating random numbers", 1);
 
    
	
    public void setSourceStream (InstanceStream is){
    	this.sourceStream = is;
    }

	public void setRandomNumberSeed(Integer i){
		this.instanceRandomSeed = i;
		this.rng = new Random(instanceRandomSeed);
	}
	
	public void setDriftChance(Double d){
		this.driftChance = d;
	}
    
	@Override
	public InstancesHeader getHeader() {
		return streamHeader;
	}

	@Override
	public long estimatedRemainingInstances() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean hasMoreInstances() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public Example<Instance> nextInstance() {

		Example<Instance> inst = sourceStream.nextInstance();
		double thisClass = inst.getData().classValue();
		
		//Create class drift on chance
		if(rng.nextDouble() < driftChance){
			//System.out.println("Drift on " + thisClass);
			for(int c = 0; c < numClasses; c++){
				for(int a = 0; a < numAtts; a++){
					attScale[(int)c][a] = rng.nextDouble() - 0.5;
					attTranslate[(int)c][a] = rng.nextDouble() - 0.5;
					if(nominalVars.contains(a)) adjNominals[(int)c][a] = rng.nextInt(numNominalValues[a]);
				}
			}
		}
		
		//Scale instance attributes
		for(int a = 0; a < numAtts; a++){
			if(!nominalVars.contains(a)){
				inst.getData().setValue(a, inst.getData().value(a) * attScale[(int)thisClass][a]);
				inst.getData().setValue(a, inst.getData().value(a) + attTranslate[(int)thisClass][a]);
			} else {
				inst.getData().setValue(a, (inst.getData().value(a) + adjNominals[(int)thisClass][a]) % numNominalValues[a]);
			}
		}
		instancesGenerated++;
		return inst;
	}

	@Override
	public boolean isRestartable() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void restart() {

		sourceStream.restart();
		setDriftChance(driftChanceOption.getValue());
		setRandomNumberSeed(instanceRandomSeedOption.getValue());
		
		//Generate header
		InstancesHeader h = sourceStream.getHeader();
		
		FastVector attributes = new FastVector();
		for (int i = 0; i < h.numInputAttributes(); i++){
			attributes.add(h.attribute(i));
		}
		
		FastVector classLabels = new FastVector();
        for (int i = 0; i < h.numClasses(); i++) {
            classLabels.addElement("class" + i);
        }
        attributes.addElement(new Attribute("class", classLabels));
        
        this.streamHeader = new InstancesHeader(new Instances(
                getCLICreationString(InstanceStream.class), attributes, 0));
        this.streamHeader.setClassIndex(this.streamHeader.numAttributes() - 1);
		
        //Prepare to start
        numAtts = streamHeader.numInputAttributes();
        numClasses = streamHeader.numClasses();
        instancesGenerated = 0;
        attScale = new double[numClasses][numAtts];
        attTranslate = new double[numClasses][numAtts];
        adjNominals = new int[numClasses][numAtts];
        for(int i = 0; i < numClasses; i++){
            Arrays.fill(attScale[i], 1.0);
            Arrays.fill(attTranslate[i], 0);
            Arrays.fill(adjNominals[i], 0);
        }
        
        numNominalValues = new int[numAtts];
        Arrays.fill(numNominalValues, 0);
        nominalVars = new ArrayList<Integer>();
        for(int a = 0; a < numAtts; a++){
    		if(this.streamHeader.attribute(a).isNominal() && a != this.streamHeader.classIndex()) {
    			nominalVars.add(a);
    			numNominalValues[a] = this.streamHeader.attribute(a).getAttributeValues().size();
    		}
        }
	}

	@Override
	public void getDescription(StringBuilder sb, int indent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void prepareForUseImpl(TaskMonitor monitor, ObjectRepository repository) {
		restart();
		
	}

}
