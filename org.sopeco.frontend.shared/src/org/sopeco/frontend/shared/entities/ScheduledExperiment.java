package org.sopeco.frontend.shared.entities;

/**
 * 
 * @author Marius Oehler
 * 
 */
public class ScheduledExperiment {

	private String label;
	private long startTime;
	private long nextExecutionTime;
	private String repeatDays;
	private String repeatHours;
	private String repeatMinutes;
	private boolean isRepeating;
	private long addedTime;

	public String getLabel() {
		return label;
	}

	public void setLabel(String pLabel) {
		this.label = pLabel;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long pStartTime) {
		this.startTime = pStartTime;
	}

	public long getNextExecutionTime() {
		return nextExecutionTime;
	}

	public void setNextExecutionTime(long pNextExecutionTime) {
		this.nextExecutionTime = pNextExecutionTime;
	}

	public String getRepeatDays() {
		return repeatDays;
	}

	public void setRepeatDays(String pRepeatDays) {
		this.repeatDays = pRepeatDays;
	}

	public String getRepeatHours() {
		return repeatHours;
	}

	public void setRepeatHours(String pRepeatHours) {
		this.repeatHours = pRepeatHours;
	}

	public String getRepeatMinutes() {
		return repeatMinutes;
	}

	public void setRepeatMinutes(String pRepeatMinutes) {
		this.repeatMinutes = pRepeatMinutes;
	}

	public boolean isRepeating() {
		return isRepeating;
	}

	public void setRepeating(boolean pIsRepeating) {
		this.isRepeating = pIsRepeating;
	}

	public long getAddedTime() {
		return addedTime;
	}

	public void setAddedTime(long pAddedTime) {
		this.addedTime = pAddedTime;
	}

}
