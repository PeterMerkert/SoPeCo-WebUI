package org.sopeco.frontend.server.execute;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.sopeco.config.Configuration;
import org.sopeco.config.IConfiguration;
import org.sopeco.config.exception.ConfigurationException;
import org.sopeco.engine.status.StatusBroker;
import org.sopeco.frontend.client.rpc.PushRPC.Type;
import org.sopeco.frontend.server.persistence.UiPersistence;
import org.sopeco.frontend.server.persistence.entities.ScheduledExperiment;
import org.sopeco.frontend.server.rpc.PushRPCImpl;
import org.sopeco.frontend.server.user.UserManager;
import org.sopeco.frontend.shared.entities.FrontendScheduledExperiment;
import org.sopeco.frontend.shared.push.AttachementPackage;
import org.sopeco.persistence.metadata.entities.DatabaseInstance;
import org.sopeco.runner.SoPeCoRunner;

/**
 * 
 * @author Marius Oehler
 * 
 */
public class ControllerQueue {

	private static final Logger LOGGER = Logger.getLogger(ControllerQueue.class.getName());
	private static ExecutorService threadPool;

	/** Queue of waiting experiments. */
	private List<QueuedExperiment> experimentQueue;

	/** The experiment which is performed at the moment. */
	private QueuedExperiment runningExperiment;

	private String currentToken;
	private Future<?> executeStatus;

	/**
	 * Creates an ThreadPool, which is responsible for the SoPeCo Runners.
	 * 
	 * @return ExecutorService
	 */
	private static ExecutorService getThreadPool() {
		if (threadPool == null) {
			threadPool = Executors.newCachedThreadPool();
		}
		return threadPool;
	}

	/**
	 * Constructor.
	 */
	public ControllerQueue() {
		experimentQueue = new ArrayList<QueuedExperiment>();
	}

	/**
	 * Returns whether the active experiment is executed by a thread.
	 * 
	 * @return true if a runner is running
	 */
	public boolean isExecuting() {
		if (executeStatus != null) {
			synchronized (executeStatus) {
				if (executeStatus.isDone() || executeStatus.isCancelled()) {
					return false;
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether an experiment is loaded, and perhaps is executed.
	 * 
	 * @return true if an experiment is active
	 */
	public boolean experimentIsLoaded() {
		if (runningExperiment != null) {
			return true;
		}
		return false;
	}

	/**
	 * Adds an experiment to this ControllerQueue. If no experiment is executed
	 * yet, this will be executed.
	 * 
	 * @param experiment
	 */
	public void addExperiment(QueuedExperiment experiment) {
		LOGGER.info("Adding experiment id:" + experiment.getId() + " to queue.");
		experiment.setTimeQueued(System.currentTimeMillis());
		experimentQueue.add(experiment);
		checkQueue();
	}

	/**
	 * Checks if the controller is ready and a experiment is waiting in the
	 * queue. If so, the next experiment is started.
	 */
	private void checkQueue() {
		synchronized (experimentQueue) {
			LOGGER.info("Looking for waiting experiment..");
			if (isExecuting()) {
				LOGGER.info("Controller is running.");
				return;
			} else if (experimentIsLoaded()) {
				LOGGER.info("Experiment is already loaded.");
			} else if (experimentQueue.isEmpty()) {
				LOGGER.info("Queue is empty.");
			} else {
				runningExperiment = experimentQueue.get(0);
				experimentQueue.remove(0);
				execute();
			}
		}
	}

	/**
	 * Start the execution of the given experiment.
	 * 
	 * @param experiment
	 */
	private void execute() {
		LOGGER.info("Start experiment id:" + runningExperiment.getId() + " on: " + runningExperiment.getControllerUrl());

		try {
			String randomId = System.currentTimeMillis() + "" + Math.random();

			IConfiguration config = Configuration.getSessionSingleton(randomId);
			config.overwrite((Configuration) runningExperiment.getConfiguration());
			config.setMeasurementControllerURI(runningExperiment.getControllerUrl());
			config.setScenarioDescription(runningExperiment.getScenarioDefinition());
			config.setProperty(IConfiguration.SENDING_STATUS_MESSAGES, "true");

			SoPeCoRunner runner = new SoPeCoRunner(randomId);
			executeStatus = getThreadPool().submit(runner);

			runningExperiment.setTimeStarted(System.currentTimeMillis());

			currentToken = waitForToken(randomId);

			ProgressWatcher.get().continueLoop();

			notifyAccount();
		} catch (ConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Waiting for the token string with which the status of the current SoPeCo
	 * Runner can be requested.
	 * 
	 * @param id
	 *            which is used for the configuration of the runner
	 * @return token string or null if a timeout is exceeded
	 */
	private String waitForToken(String id) {
		LOGGER.info("Waiting for Token..");
		int loopLimit = 100;
		String token = null;
		while (token == null && loopLimit-- > 0) {
			try {
				Thread.sleep(10);
			} catch (Exception e) {
			}
			token = StatusBroker.get().getToken(id + runningExperiment.getControllerUrl());
		}
		return token;
	}

	/**
	 * Returns the current token with which the status can be requested of the
	 * {@link StatusBroker#getManager(String)} or null if no status is
	 * available.
	 * 
	 * @return token string
	 */
	public String getCurrentToken() {
		return currentToken;
	}

	/**
	 * Ends the execution of the current experiment and stores information about
	 * it.
	 */
	public synchronized void finished() {
		LOGGER.info("Experiment id:" + runningExperiment.getId() + " finished on: "
				+ runningExperiment.getControllerUrl());

		runningExperiment.setTimeEnded(System.currentTimeMillis());
		saveDurationInExperiment();

		notifyAccount();

		executeStatus = null;
		runningExperiment = null;
		checkQueue();
	}

	/**
	 * Stores the duration of this execution in the list, which is stored in the
	 * ScheduledExperiment.
	 */
	private void saveDurationInExperiment() {
		ScheduledExperiment exp = UiPersistence.getUiProvider().loadScheduledExperiment(runningExperiment.getId());
		if (exp != null) {
			if (exp.getDurations() == null) {
				exp.setDurations(new ArrayList<Long>());
			}
			long duration = runningExperiment.getTimeEnded() - runningExperiment.getTimeStarted();
			exp.getDurations().add(duration);
			UiPersistence.getUiProvider().storeScheduledExperiment(exp);
		}
	}

	/**
	 * Returns the experiment which is loaded at the moment.
	 * 
	 * @return QueuedExperiment
	 */
	public QueuedExperiment getCurrentlyRunning() {
		return runningExperiment;
	}

	
	private void notifyAccount() {
		List<ScheduledExperiment> resultList = UiPersistence.getUiProvider().loadScheduledExperimentsByAccount(
				runningExperiment.getAccount());

		ArrayList<FrontendScheduledExperiment> fseList = new ArrayList<FrontendScheduledExperiment>();
		for (ScheduledExperiment experiment : resultList) {
			fseList.add(experiment.createFrontendScheduledExperiment());
		}

		AttachementPackage fsePackage = new AttachementPackage();
		fsePackage.setType(Type.PUSH_SCHEDULED_EXPERIMENT);
		fsePackage.setAttachement(fseList);

		for (String sId : UserManager.getAllUsers().keySet()) {
			DatabaseInstance db = UserManager.getUser(sId).getCurrentAccount();
			if (db != null && db.getDbName().equals(runningExperiment.getAccount())) {
				PushRPCImpl.push(sId, fsePackage);
			}
		}
	}
}
