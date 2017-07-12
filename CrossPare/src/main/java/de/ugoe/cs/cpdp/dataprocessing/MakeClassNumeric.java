
package de.ugoe.cs.cpdp.dataprocessing;

import org.apache.commons.collections4.list.SetUniqueList;

import de.ugoe.cs.cpdp.util.WekaUtils;
import weka.core.Instances;

/**
 * <p>
 * Makes the class attribute numeric, in case it was a nominal label before.
 * </p>
 * 
 * @author Steffen Herbold
 */
public class MakeClassNumeric implements ISetWiseProcessingStrategy, IProcessesingStrategy {

    /*
     * (non-Javadoc)
     * 
     * @see de.ugoe.cs.cpdp.IParameterizable#setParameter(java.lang.String)
     */
    @Override
    public void setParameter(String parameters) {
        // dummy, no parameters
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.ugoe.cs.cpdp.dataprocessing.IProcessesingStrategy#apply(weka.core.Instances,
     * weka.core.Instances)
     */
    @Override
    public void apply(Instances testdata, Instances traindata) {
        WekaUtils.makeClassNumeric(testdata);
        WekaUtils.makeClassNumeric(traindata);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.ugoe.cs.cpdp.dataprocessing.ISetWiseProcessingStrategy#apply(weka.core.Instances,
     * org.apache.commons.collections4.list.SetUniqueList)
     */
    @Override
    public void apply(Instances testdata, SetUniqueList<Instances> traindataSet) {
        WekaUtils.makeClassNumeric(testdata);
        for (Instances traindata : traindataSet) {
            WekaUtils.makeClassNumeric(traindata);
        }
    }

}
