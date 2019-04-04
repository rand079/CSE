package moa.streams.generators;

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

// Stream that has one unchanging minority class and other changing majority classes

public class MinorityClassStream extends AbstractOptionHandler implements InstanceStream {

	protected InstancesHeader streamHeader;
	int instancesGenerated;
	InstanceStream sourceStream;
	int numClasses;
	int interval;
	int instanceRandomSeed;
	double minorityClassProp;
	protected Random rng;
	boolean useOddClasses;
	
	int[] sourceClasses;
	
    public IntOption intervalOption = new IntOption("interval", 'n',
            "Interval at which majority classes drift", 10000);

    // Proportion of minority class to drop
    public FloatOption minorityClassPropOption = new FloatOption("minorityClassProp", 'r',
            "Filter that only retains given percent of minority class from source stream",
            0.5, 0, 1);
    
	
    // RandomNumberSeed
    public IntOption instanceRandomSeedOption = new IntOption("instanceRandomSeed", 'i',
            "Seed for generating random numbers", 1);
    
	
	public void setInterval(Integer i){
		this.interval = i;
	}
	
    public void setSourceStream (InstanceStream is){
    	this.sourceStream = is;
    }
    
    public void setMinorityClassProp (double d){
    	this.minorityClassProp = d;
    }
    
	public void setRandomNumberSeed(Integer i){
		this.instanceRandomSeed = i;
		this.rng = new Random(instanceRandomSeed);
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
		if(instancesGenerated % interval == 0){
			if(instancesGenerated/interval % 2 == 0)
				useOddClasses = true;
			else
				useOddClasses = false;
		}
		
		Example<Instance> inst;

		inst = sourceStream.nextInstance();
		int instClass = (int)inst.getData().classValue();

		//Minority unchanging class
		if(rng.nextDouble() >= minorityClassProp){
			while(instClass == 0){
				inst = sourceStream.nextInstance();
				instClass = (int)inst.getData().classValue();
			}
		}
		
		//Exclude unwanted majority classes
		if(instClass != 0){
			while((instClass % 2 == 0 & useOddClasses) | (instClass % 2 == 1 & !useOddClasses) | instClass == 0){
				inst = sourceStream.nextInstance();
				instClass = (int)inst.getData().classValue();
			}
		}
		
		inst.getData().setClassValue(((int)(inst.getData().classValue()+1))/2);
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
		setInterval(intervalOption.getValue());
		setMinorityClassProp(minorityClassPropOption.getValue());
		setRandomNumberSeed(instanceRandomSeedOption.getValue());
		//Generate header
		InstancesHeader h = sourceStream.getHeader();
		
		FastVector attributes = new FastVector();
		for (int i = 0; i < h.numInputAttributes(); i++){
			attributes.add(h.attribute(i));
		}
		
		FastVector classLabels = new FastVector();
        for (int i = 0; i < h.numClasses(); i = i+2) {
            classLabels.addElement("class" + (i/2));
        }
        attributes.addElement(new Attribute("class", classLabels));
        
        this.streamHeader = new InstancesHeader(new Instances(
                getCLICreationString(InstanceStream.class), attributes, 0));
        this.streamHeader.setClassIndex(this.streamHeader.numAttributes() - 1);
		
        //Prepare to start
        numClasses = streamHeader.numClasses();
        sourceClasses = new int[numClasses];
        instancesGenerated = 0;
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
