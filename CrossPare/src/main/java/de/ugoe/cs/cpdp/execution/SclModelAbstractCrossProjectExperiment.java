// Copyright 2015 Georg-August-Universität Göttingen, Germany
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package de.ugoe.cs.cpdp.execution;

import de.ugoe.cs.cpdp.ExperimentConfiguration;
import de.ugoe.cs.cpdp.dataprocessing.IProcessesingStrategy;
import de.ugoe.cs.cpdp.dataprocessing.ISetWiseProcessingStrategy;
import de.ugoe.cs.cpdp.dataselection.IPointWiseDataselectionStrategy;
import de.ugoe.cs.cpdp.dataselection.ISetWiseDataselectionStrategy;
import de.ugoe.cs.cpdp.eval.IEvaluationStrategy;
import de.ugoe.cs.cpdp.eval.IResultStorage;
import de.ugoe.cs.cpdp.loader.IVersionLoader;
import de.ugoe.cs.cpdp.training.*;
import de.ugoe.cs.cpdp.versions.IVersionFilter;
import de.ugoe.cs.cpdp.versions.SoftwareVersion;
import de.ugoe.cs.util.console.Console;
import org.apache.commons.collections4.list.SetUniqueList;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Class responsible for executing an experiment according to an {@link ExperimentConfiguration}.
 * The steps of an experiment are as follows:
 * <ul>
 * <li>load the data from the provided data path</li>
 * <li>filter the data sets according to the provided version filters</li>
 * <li>execute the following steps for each data sets as test data that is not ignored through the
 * test version filter:
 * <ul>
 * <li>filter the data sets to setup the candidate training data:
 * <ul>
 * <li>remove all data sets from the same project</li>
 * <li>filter all data sets according to the training data filter
 * </ul>
 * </li>
 * <li>apply the setwise preprocessors</li>
 * <li>apply the setwise data selection algorithms</li>
 * <li>apply the setwise postprocessors</li>
 * <li>train the setwise training classifiers</li>
 * <li>unify all remaining training data into one data set</li>
 * <li>apply the preprocessors</li>
 * <li>apply the pointwise data selection algorithms</li>
 * <li>apply the postprocessors</li>
 * <li>train the normal classifiers</li>
 * <li>evaluate the results for all trained classifiers on the training data</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * Note that this class implements {@link Runnable}, i.e., each experiment can be started in its own
 * thread.
 * 
 * @author Steffen Herbold
 */
public abstract class SclModelAbstractCrossProjectExperiment implements IExecutionStrategy {

    /**
     * configuration of the experiment
     */
    protected final ExperimentConfiguration config;

    /**
     * Constructor. Creates a new experiment based on a configuration.
     *
     * @param config
     *            configuration of the experiment
     */
    @SuppressWarnings("hiding")
    public SclModelAbstractCrossProjectExperiment(ExperimentConfiguration config) {
        this.config = config;
    }

    /**
     * <p>
     * Defines which products are allowed for training.
     * </p>
     *
     * @param trainingVersion
     *            training version
     * @param testVersion
     *            test candidate
     * @param versions
     *            all software versions in the data set
     * @return true if test candidate can be used for training
     */
    protected abstract boolean isTrainingVersion(SoftwareVersion trainingVersion,
                                                 SoftwareVersion testVersion,
                                                 List<SoftwareVersion> versions);

    /**
     * Helper method that combines a set of Weka {@link Instances} sets into a single
     * {@link Instances} set.
     * 
     * @param traindataSet
     *            set of {@link Instances} to be combines
     * @return single {@link Instances} set
     */
    public static Instances makeSingleTrainingSet(SetUniqueList<Instances> traindataSet) {
        Instances traindataFull = null;
        for (Instances traindata : traindataSet) {
            if (traindataFull == null) {
                traindataFull = new Instances(traindata);
            }
            else {
                for (int i = 0; i < traindata.numInstances(); i++) {
                    traindataFull.add(traindata.instance(i));
                }
            }
        }
        return traindataFull;
    }

    /**
     * Executes the experiment with the steps as described in the class comment.
     * 
     * @see Runnable#run()
     */
    @SuppressWarnings("boxing")
    @Override
    public void run() {
        final List<SoftwareVersion> versions = new LinkedList<>();

        for (IVersionLoader loader : this.config.getLoaders()) {
            versions.addAll(loader.load());
        }

        for (IVersionFilter filter : this.config.getVersionFilters()) {
            filter.apply(versions);
        }
        boolean writeHeader = true;
        int versionCount = 1;
        int testVersionCount = 0;

        for (SoftwareVersion testVersion : versions) {
            if (isVersion(testVersion, this.config.getTestVersionFilters())) {
                testVersionCount++;
            }
        }
        testVersionCount*=10;

        // sort versions
        Collections.sort(versions);
        for(int time = 0;time < 10;time++) {

            Random rand = new Random(time + 1);
            for(int i = 0;i < versions.size();i++){
                 versions.get(i).getInstances().randomize(rand);
            }

            for (int fold = 0; fold < 10; fold++) {

                SetUniqueList<Instances> traindataSet = SetUniqueList.setUniqueList(new LinkedList<Instances>());

                for (SoftwareVersion version : versions) {
                    //Setup traindata
                    traindataSet.add(version.getInstances().testCV(10, fold));
                }

                for (SoftwareVersion version : versions) {

                    // Setup testdata
                    Instances testdata = version.getInstances().trainCV(10, fold);
                    List<Double> efforts = getEfforts(testdata);
                    List<Double> numBugs = getNumBugs(testdata);

                    for (ISetWiseProcessingStrategy processor : this.config.getSetWisePreprocessors()) {
                        Console.traceln(Level.FINE, String
                                .format("[%s] [%02d/%02d] %s: applying setwise preprocessor %s",
                                        this.config.getExperimentName(), versionCount, testVersionCount,
                                        version.getVersion(), processor.getClass().getName()));
                        processor.apply(testdata, traindataSet);
                    }
                    for (ISetWiseDataselectionStrategy dataselector : this.config
                            .getSetWiseSelectors()) {
                        Console
                                .traceln(Level.FINE,
                                        String.format("[%s] [%02d/%02d] %s: applying setwise selection %s",
                                                this.config.getExperimentName(), versionCount,
                                                testVersionCount, version.getVersion(),
                                                dataselector.getClass().getName()));
                        dataselector.apply(testdata, traindataSet);
                    }
                    for (ISetWiseProcessingStrategy processor : this.config
                            .getSetWisePostprocessors()) {
                        Console.traceln(Level.FINE, String
                                .format("[%s] [%02d/%02d] %s: applying setwise postprocessor %s",
                                        this.config.getExperimentName(), versionCount, testVersionCount,
                                        version.getVersion(), processor.getClass().getName()));
                        processor.apply(testdata, traindataSet);
                    }
                    for (ISetWiseTrainingStrategy setwiseTrainer : this.config.getSetWiseTrainers()) {
                        Console
                                .traceln(Level.FINE,
                                        String.format("[%s] [%02d/%02d] %s: applying setwise trainer %s",
                                                this.config.getExperimentName(), versionCount,
                                                testVersionCount, version.getVersion(),
                                                setwiseTrainer.getName()));
                        setwiseTrainer.apply(traindataSet);
                    }
                    for (ISetWiseTestdataAwareTrainingStrategy setwiseTestdataAwareTrainer : this.config
                            .getSetWiseTestdataAwareTrainers()) {
                        Console.traceln(Level.FINE, String
                                .format("[%s] [%02d/%02d] %s: applying testdata aware setwise trainer %s",
                                        this.config.getExperimentName(), versionCount, testVersionCount,
                                        version.getVersion(), setwiseTestdataAwareTrainer.getName()));
                        setwiseTestdataAwareTrainer.apply(traindataSet, testdata);
                    }

                    Instances traindata = makeSingleTrainingSet(traindataSet);

                    //model building
                    for (IProcessesingStrategy processor : this.config.getPreProcessors()) {
                        Console.traceln(Level.FINE,
                                String.format("[%s] [%02d/%02d] %s: applying preprocessor %s",
                                        this.config.getExperimentName(), versionCount,
                                        testVersionCount, version.getVersion(),
                                        processor.getClass().getName()));
                        processor.apply(testdata, traindata);
                    }
                    for (IPointWiseDataselectionStrategy dataselector : this.config
                            .getPointWiseSelectors()) {
                        Console.traceln(Level.FINE, String
                                .format("[%s] [%02d/%02d] %s: applying pointwise selection %s",
                                        this.config.getExperimentName(), versionCount, testVersionCount,
                                        version.getVersion(), dataselector.getClass().getName()));
                        traindata = dataselector.apply(testdata, traindata);
                    }
                    for (IProcessesingStrategy processor : this.config.getPostProcessors()) {
                        Console.traceln(Level.FINE, String
                                .format("[%s] [%02d/%02d] %s: applying setwise postprocessor %s",
                                        this.config.getExperimentName(), versionCount, testVersionCount,
                                        version.getVersion(), processor.getClass().getName()));
                        processor.apply(testdata, traindata);
                    }
                    for (ITrainingStrategy trainer : this.config.getTrainers()) {
                        Console.traceln(Level.FINE,
                                String.format("[%s] [%02d/%02d] %s: applying trainer %s",
                                        this.config.getExperimentName(), versionCount,
                                        testVersionCount, version.getVersion(),
                                        trainer.getName()));
                        trainer.apply(traindata);
                    }
                    for (ITestAwareTrainingStrategy trainer : this.config.getTestAwareTrainers()) {
                        Console.traceln(Level.FINE,
                                String.format("[%s] [%02d/%02d] %s: applying trainer %s",
                                        this.config.getExperimentName(), versionCount,
                                        testVersionCount, version.getVersion(),
                                        trainer.getName()));
                        trainer.apply(testdata, traindata);
                    }

                    File resultsDir = new File(this.config.getResultsPath());
                    if (!resultsDir.exists()) {
                        resultsDir.mkdir();
                    }
                    for (IEvaluationStrategy evaluator : this.config.getEvaluators()) {

                        Console.traceln(Level.FINE, String.format("[%s] [%02d/%02d] %s: applying evaluator %s",
                                this.config.getExperimentName(), versionCount,
                                testVersionCount, version.getVersion(),
                                evaluator.getClass().getName()));

                        List<ITrainer> allTrainers = new LinkedList<>();

                        for (ISetWiseTrainingStrategy setwiseTrainer : this.config.getSetWiseTrainers()) {
                            allTrainers.add(setwiseTrainer);
                        }
                        for (ISetWiseTestdataAwareTrainingStrategy setwiseTestdataAwareTrainer : this.config.getSetWiseTestdataAwareTrainers()) {
                            allTrainers.add(setwiseTestdataAwareTrainer);
                        }
                        for (ITrainingStrategy trainer : this.config.getTrainers()) {
                            allTrainers.add(trainer);
                        }
                        for (ITestAwareTrainingStrategy trainer : this.config.getTestAwareTrainers()) {
                            allTrainers.add(trainer);
                        }
                        if (writeHeader) {
                            evaluator.setParameter(this.config.getResultsPath() + "/" +
                                    this.config.getExperimentName() + ".csv");
                        }
                        evaluator.apply(testdata, traindata, allTrainers, efforts, numBugs, writeHeader, this.config.getResultStorages());
                        writeHeader = false;
                    }
                    Console.traceln(Level.INFO,
                            String.format("[%s] [%02d/%02d] %s: finished",
                                    this.config.getExperimentName(), versionCount,
                                    testVersionCount, version.getVersion()));
                    versionCount++;

                }
            }
        }
    }

    /**
     * Helper method that checks if a version passes all filters.
     * 
     * @param version
     *            version that is checked
     * @param filters
     *            list of the filters
     * @return true, if the version passes all filters, false otherwise
     */
    private static boolean isVersion(SoftwareVersion version, List<IVersionFilter> filters) {
        boolean result = true;
        for (IVersionFilter filter : filters) {
            result &= !filter.apply(version);
        }
        return result;
    }

    /**
     * <p>
     * helper function that checks if the results are already in the data store
     * </p>
     *
     * @param version
     *            version for which the results are checked
     * @return
     */
    private int resultsAvailable(SoftwareVersion version) {
        if (this.config.getResultStorages().isEmpty()) {
            return 0;
        }

        List<ITrainer> allTrainers = new LinkedList<>();
        for (ISetWiseTrainingStrategy setwiseTrainer : this.config.getSetWiseTrainers()) {
            allTrainers.add(setwiseTrainer);
        }
        for (ISetWiseTestdataAwareTrainingStrategy setwiseTestdataAwareTrainer : this.config
            .getSetWiseTestdataAwareTrainers())
        {
            allTrainers.add(setwiseTestdataAwareTrainer);
        }
        for (ITrainingStrategy trainer : this.config.getTrainers()) {
            allTrainers.add(trainer);
        }
        for (ITestAwareTrainingStrategy trainer : this.config.getTestAwareTrainers()) {
            allTrainers.add(trainer);
        }

        int available = Integer.MAX_VALUE;
        for (IResultStorage storage : this.config.getResultStorages()) {
            String classifierName = ((IWekaCompatibleTrainer) allTrainers.get(0)).getName();
            int curAvailable = storage.containsResult(this.config.getExperimentName(),
                                                      version.getVersion(), classifierName);
            if (curAvailable < available) {
                available = curAvailable;
            }
        }
        return available;
    }

    /**
     * <p>
     * Sets the efforts for the instances
     * </p>
     *
     * @param data
     *            the data
     * @return
     */
    @SuppressWarnings("boxing")
    public static List<Double> getEfforts(Instances data) {
        // attribute in the JURECZKO data and default
        Attribute effortAtt = data.attribute("loc");
        if (effortAtt == null) {
            // attribute in the NASA/SOFTMINE/MDP data
            effortAtt = data.attribute("LOC_EXECUTABLE");
        }
        if (effortAtt == null) {
            // attribute in the AEEEM data
            effortAtt = data.attribute("numberOfLinesOfCode");
        }
        if (effortAtt == null) {
            // attribute in the RELINK data
            effortAtt = data.attribute("CountLineCodeExe");
        }
        if (effortAtt == null) {
            // attribute in the SMARTSHARK data
            effortAtt = data.attribute("LOC");
        }
        List<Double> efforts = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            if(effortAtt!=null) {
                efforts.add(data.get(i).value(effortAtt));
            } else {
                // add constant effort per instance (default)
                efforts.add(1.0);
            }
        }
        return efforts;
    }

    /**
     * <p>
     * Retrieves the number of bugs from the class attribute of the data and stores it separately in
     * a list.
     * </p>
     *
     * @param data
     *            the data
     * @return list with bug counts
     */
    private static List<Double> getNumBugs(Instances data) {
        List<Double> numBugs = new ArrayList<>(data.size());
        for (Instance instance : data) {
            numBugs.add(instance.classValue());
        }
        return numBugs;
    }
}
