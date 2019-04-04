
package moa.streams.generators;

import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.DenseInstance;
import moa.core.FastVector;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import moa.core.InstanceExample;

import com.yahoo.labs.samoa.instances.InstancesHeader;
import moa.core.ObjectRepository;
import moa.options.AbstractOptionHandler;
import com.github.javacliparser.FlagOption;
import com.github.javacliparser.IntOption;
import moa.streams.InstanceStream;
import moa.tasks.TaskMonitor;

/**
 * Nom-Num generator
 * A modification of STAGGER with variable classes and numerical attributes
 */
public class MixedTypeGenerator extends AbstractOptionHandler implements
        InstanceStream {

    private static final long serialVersionUID = 1L;

    public IntOption rulesRandomSeedOption = new IntOption(
            "rulesRandomSeed", 'l',
            "Seed for random generation of rules.", 1);
    
    public IntOption instanceRandomSeedOption = new IntOption(
            "instanceRandomSeed", 'i',
            "Seed for random generation of instances.", 1);

    public IntOption nominalAttributesOption = new IntOption("nominalAttributes", 'o',
            "Number of nominal attributes", 5);

    public IntOption nominalAttributeDepthOption = new IntOption("nominalAttributeDepth", 'd',
            "Number of levels for nominal attributes", 5);
    
    public IntOption numericAttributesOption = new IntOption("numericalAttributes", 'u',
            "Number of numerical attributes", 5);

    public IntOption numUniformNominalAttributesOption = new IntOption("uniformNominalClasses", 'f',
            "Number of 'masked' uniform nominal attributes with no useful info shown for classifying (used for drift)", 0);
    
    public IntOption numUniformNumericAttributesOption = new IntOption("uniformNumericClasses", 'r',
            "Number of 'masked' uniform numeric attributes with random uniform noise from -1 to 1 shown for classifying (used for drift)", 0);
    
    public IntOption numClassesOption = new IntOption("numClasses", 'n',
            "Number of classes", 5);
    
    protected InstancesHeader streamHeader;
    protected Random rng;
    protected Random rng_rules;
    Integer nominalAttributes;
    Integer numericAttributes;
    Integer nominalAttributeDepth;
    Integer numClasses;
    Integer uniformNominalAttributes;
    Integer uniformNumericAttributes;
    int[] nomRules;
    double[][] numRulesMean;
    double[][] numRulesSD;

    @Override
    protected void prepareForUseImpl(TaskMonitor monitor,
            ObjectRepository repository) {
    	
    	nominalAttributes = nominalAttributesOption.getValue();
    	numericAttributes = numericAttributesOption.getValue();
    	nominalAttributeDepth = nominalAttributeDepthOption.getValue();
    	uniformNominalAttributes = numUniformNominalAttributesOption.getValue();
    	uniformNumericAttributes = numUniformNumericAttributesOption.getValue();
    	numClasses = numClassesOption.getValue();
    	
        // generate header
        FastVector attributes = new FastVector();

        for(int i = 0; i < nominalAttributes; i++){
        	FastVector thisNominalAttribute = new FastVector();
        	for(int j = 0; j < nominalAttributeDepthOption.getValue(); j++){
        		thisNominalAttribute.addElement("level" + j);
        	}
        	attributes.add(new Attribute("nominalAtt" + i, thisNominalAttribute));
        }

        for(int i = 0; i < numericAttributesOption.getValue(); i++){
        	FastVector thisNumericAttribute = new FastVector();
        	attributes.add(new Attribute("numericAtt" + i));
        }
        
        FastVector thisClassAttribute = new FastVector();
        for(int i = 0; i < numClasses; i++){
        	thisClassAttribute.addElement("class" + i);
        }
        attributes.addElement(new Attribute("class", thisClassAttribute));

        this.streamHeader = new InstancesHeader(new Instances(
                getCLICreationString(InstanceStream.class), attributes, 0));
        this.streamHeader.setClassIndex(this.streamHeader.numAttributes() - 1);
        restart();
    }

    @Override
    public long estimatedRemainingInstances() {
        return -1;
    }

    @Override
    public InstancesHeader getHeader() {
        return this.streamHeader;
    }

    @Override
    public boolean hasMoreInstances() {
        return true;
    }

    @Override
    public boolean isRestartable() {
        return true;
    }

    @Override
    public InstanceExample nextInstance() {

        int desiredClass = rng.nextInt(numClasses);
        int[] nomAtts = new int[nominalAttributes];
        double[] numAtts = new double[numericAttributes];
        
        int chosenCombination = -1;
        while(chosenCombination < 0 || nomRules[chosenCombination] != desiredClass){
        	chosenCombination = rng.nextInt(nomRules.length);
        }

        //set nominal attributes
        for(int i = 0; i < nominalAttributes; i++){
        	if(i + uniformNominalAttributes < nominalAttributes)
        		nomAtts[i] = (int)(chosenCombination/Math.pow(nominalAttributeDepth, i)) % nominalAttributeDepth;
        	else nomAtts[i] =  rng.nextInt(nominalAttributeDepth);
        }
        
        //set numeric attributes
        for(int i = 0; i < numericAttributes; i++){
        	if(i + uniformNumericAttributes < numericAttributes)
        		numAtts[i] = rng.nextGaussian() * numRulesSD[desiredClass][i] + numRulesMean[desiredClass][i];
        	else numAtts[i] = (rng.nextDouble() - 0.5) * 2.0;
        }

        // construct instance
        InstancesHeader header = getHeader();
        Instance inst = new DenseInstance(header.numAttributes());
        for(int i = 0; i < nominalAttributes + numericAttributes; i++){
        	if(i < nominalAttributes){
        		inst.setValue(i, nomAtts[i]);
        	} else {
        		inst.setValue(i, numAtts[i - nominalAttributes]);
        	}
        }
        inst.setDataset(header);
        inst.setClassValue(desiredClass);
        
        //System.out.println(inst.toString());
        
        return new InstanceExample(inst);
    }

    @Override
    public void restart() {
    	this.rng = new Random(instanceRandomSeedOption.getValue());
    	this.rng_rules = new Random(rulesRandomSeedOption.getValue());
        createClassRules();
    }

    private void createClassRules() {
		double combinations = Math.pow(nominalAttributeDepth, nominalAttributes);
		nomRules = new int[(int)combinations];
        
        boolean allClassesIncluded = false;
        while(!allClassesIncluded){
    		int[] classCombinationCount = new int[numClasses];
            Arrays.fill(classCombinationCount,  0);
    		for(int i = 0; i < combinations; i++){
    			Integer n = rng_rules.nextInt(numClasses);
    			nomRules[i] = n;
    			classCombinationCount[n]++;
    		}
    		allClassesIncluded = true;
    		for(int i = 0; i < numClasses; i++) allClassesIncluded = (classCombinationCount[i] == 0) ? false : allClassesIncluded;
        }      
		
		numRulesMean = new double[numClasses][numericAttributes];
		numRulesSD = new double[numClasses][numericAttributes];
		for(int i = 0; i < numClasses; i++){
			for(int j = 0; j < numericAttributes; j++){
				numRulesMean[i][j] = rng_rules.nextDouble() * 2.0 - 1.0;
				numRulesSD[i][j] = rng_rules.nextDouble();
			}
		}
	}

	@Override
    public void getDescription(StringBuilder sb, int indent) {
        // TODO Auto-generated method stub
    }
	
	public void setUniformNumericAttributes(int num){
		this.numUniformNumericAttributesOption.setValue(num);
		this.uniformNumericAttributes = num;
	}
	
	public void setUniformNominalAttributes(int num){
		this.numUniformNominalAttributesOption.setValue(num);
		this.uniformNominalAttributes = num;
	}
}
