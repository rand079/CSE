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

// Stream that has one class that changes to mimic others over time

public class MimicClassStream extends AbstractOptionHandler implements InstanceStream {

	protected InstancesHeader streamHeader;
	int mimicClassInstancesGenerated;
	InstanceStream sourceStream;
	int numClasses;
	int interval;
	int instanceRandomSeed;
	double mimicClassProp;
	protected Random rng;
	int targetMimicClass;
	
    public IntOption intervalOption = new IntOption("interval", 'n',
            "Interval at which mimic class changes target", 10000);   
	
    // RandomNumberSeed
    public IntOption instanceRandomSeedOption = new IntOption("instanceRandomSeed", 'i',
            "Seed for generating random numbers", 1);
    

    // Proportion of stream to consist of mimic prop
    public FloatOption mimicClassPropOption = new FloatOption("minorityClassProp", 'r',
            "Filter that changes given percent of instances to the mimic class",
            0, 0, 1);
    
	public void setInterval(Integer i){
		this.interval = i;
	}
	
    public void setSourceStream (InstanceStream is){
    	this.sourceStream = is;
    }

	public void setRandomNumberSeed(Integer i){
		this.instanceRandomSeed = i;
		this.rng = new Random(instanceRandomSeed);
	}
	
    public void setMimicClassProp (double d){
    	this.mimicClassProp = d;
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
		if(mimicClassInstancesGenerated % interval == 0){
			int currentMimicClass = targetMimicClass;
			while(currentMimicClass == targetMimicClass){
				targetMimicClass = rng.nextInt(numClasses - 1) + 1;
			}
		}
		
		Example<Instance> inst  = sourceStream.nextInstance();
		if(inst.getData().classValue() == 0){
			while(0 == 0){
				mimicClassInstancesGenerated++;
				inst = sourceStream.nextInstance();
				if(inst.getData().classValue() == targetMimicClass){
					inst.getData().setClassValue((double)(0));
					break;
				}
			}
		} else {
			inst = sourceStream.nextInstance();
		}
		
		
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
        numClasses = streamHeader.numClasses();
        if(mimicClassProp == 0) mimicClassProp = (double)1/numClasses;
        mimicClassInstancesGenerated = 0;
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
