package de.ugoe.cs.cpdp;

import de.ugoe.cs.cpdp.execution.IExecutionStrategy;
import de.ugoe.cs.util.console.Console;
import de.ugoe.cs.util.console.TextConsole;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * <p>
 * Tests the ExperimentConfiguration
 * </p>
 * 
 * @author Steffen Herbold
 */
public class NewTotalRun {

    public static void main(String[] args) {


        String filename;
        String[] strategys = {"Nam15","Peters15","Turhan09"};
        String[] projects  = {"AEEEM-New","MORPH-New","RELINK-New","SOFTLAB-New"};

        for(String strategy : strategys){

            for(String project : projects){

                filename = "data/benchmark/config/" + project + "-" + strategy + ".xml";
                run(filename);
            }
        }

    }

    public static void run(String arg){

        new TextConsole(Level.FINE);
        final int concurrentThreads = Runtime.getRuntime().availableProcessors();
        Console.traceln(Level.FINE, "exuection max " + concurrentThreads + " at the same time");
        final ExecutorService threadPool = Executors.newFixedThreadPool(concurrentThreads);

        File file = new File(arg);
        if (file.isFile()) {

            createConfig(threadPool, file.getAbsolutePath());
        } else if (file.isDirectory() && file.listFiles() != null) {
            for (File subfile : file.listFiles()) {
                if (subfile.isFile()) {
                    createConfig(threadPool, subfile.getAbsolutePath());
                }
            }
        }
        threadPool.shutdown();
        try {
            threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * Creates the config and starts the corresponding experiment
     *
     * @param threadPool
     *            thread pool in which the configurations are executed
     * @param configFile
     *            location of the config file
     */
    public static void createConfig(ExecutorService threadPool, String configFile) {
        ExperimentConfiguration config = null;
        try {
            config = new ExperimentConfiguration(configFile);
        } catch (Exception e) {
            Console.printerrln("Failure initializing the experiment configuration for configuration file " +
                            configFile);
            e.printStackTrace();
        }

        if (config != null) {
            Console.trace(Level.FINEST, config.toString());
            // Instantiate the class like it was given as parameter in the config file and cast it
            // to the interface
            try {
                // Because we need to pass a parameter, a normal new Instance call is not possible
                Class<?> executionStrategyClass =
                        Class.forName("de.ugoe.cs.cpdp.execution." + config.getExecutionStrategy());

                Constructor<?> executionStrategyConstructor =
                        executionStrategyClass.getConstructor(ExperimentConfiguration.class);

                IExecutionStrategy experiment =
                        (IExecutionStrategy) executionStrategyConstructor.newInstance(config);

                threadPool.execute(experiment);
            } catch (NoSuchMethodException e) {
                Console.printerrln("Class \"" + config.getExecutionStrategy() +
                        "\" does not have the right Constructor");
                e.printStackTrace();
            } catch (SecurityException e) {
                Console.printerrln("Security manager prevents reflection");
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                Console.printerrln("Class \"" + config.getExecutionStrategy() +
                        "\" does not have a Constructor, which" + "matches the given arguments");
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                Console.printerrln("Constructor in Class \"" + config.getExecutionStrategy() +
                        "\" is not public");
                e.printStackTrace();
            } catch (InstantiationException e) {
                Console.printerrln("Cannot instantiate Class \"" + config.getExecutionStrategy() +
                        "\"");
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                Console.printerrln("Cannot access Class \"" + config.getExecutionStrategy() + "\"");
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                Console.printerrln("Class \"" + config.getExecutionStrategy() + "\" was not found");
                e.printStackTrace();
            }

        }
    }
}
