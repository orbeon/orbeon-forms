/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * A task which can be scheduled to run at specified times by the TaskScheduler <br>
 *
 * <p>
 * The user should extend this class and override the required methods (e.g. run() method).
 * <p>
 *
 * @author Efraim Berkovich
 * @version 1.0
 */

public abstract class Task implements Runnable, Externalizable {

    // instance variables
    /** has the cancel() method been called? */
    protected boolean isCancelled = false;
    /** The name of the task (for display purposes) */
    protected String name = null;
    private transient long ID;

    private boolean hasBeenScheduled = false;
    private long scheduledInitialTime = 0;
    private long scheduledInterval = 0;   // if <=0 -- means one-time execution
    private long lastRunTime = 0;

    // package view -- the task scheduler sets this when the task is scheduled with it
    TaskScheduler scheduler = null;


    // static variables
    private static Boolean mutex = new Boolean(true);
    private static long nextID = 0;


    /**
     * Creates a new task and assigns it a unique ID.
     *
     */
    protected Task() {
        synchronized (mutex) {
            this.ID = nextID;
            nextID++;
            if (nextID == Long.MAX_VALUE) nextID = 0;
        }

    }


    /**
     * Schedules the specified task for execution at the specified time. If the time
     * is in the past, the task is scheduled for immediate execution.
     * <p>
     * There are two types of scheduling: one time and periodic.
     * <p>
     * <b>One time</b> <br>
     * The task will execute exactly once.
     * <p>
     * <b>Periodic</b><br>
     * The task is scheduled for repeated periodic execution, beginning at the specified time.
     * Once a task starts running, the next occurrence of the task will start running <i>interval</i>
     * time later. It is possible for one event-firing of the task (if it runs too long) will overlap
     * another event-firing of that same task. If this is not desired, then the task should
     * synchronize within its run() method.
     * <p>
     * This method can only be called once. Calling it more than once
     * will cause an exception.
     * <p>
     * @param firstTime long system time at which to run the task.
     * @param interval long number of millis between executions of the task, passing
     *              <=0 will cause the task to be executed once.
     *
     * @exception IllegalStateException On calling this method more than once.
     */
    public synchronized final void setSchedule(long firstTime
                                               , long interval)
            throws IllegalStateException {
        if (hasBeenScheduled)
            throw new IllegalStateException("Cannot set schedule for the task more than once.");

        hasBeenScheduled = true;
        this.scheduledInitialTime = firstTime;
        this.scheduledInterval = interval;
    }


    /**
     * Get the task ID.
     * @return unique Task ID.
     */
    public long getID() {
        return ID;
    }


    /**
     * Get the first scheduled time for the task.
     * @return the time the task was first scheduled to run (as long, millis)
     */
    public long getScheduledFirstTime() {
        return scheduledInitialTime;
    }


    /**
     * Get the interval scheduled for the task.
     * @return the interval scheduled for the task. (as long, millis)
     */
    public long getScheduledInterval() {
        return scheduledInterval;
    }


    /**
     * Was the cancel method called for this task?
     * @return true if cancel was called, false if not
     */
    public boolean isCancelled() {
        return isCancelled;
    }


    /**
     * Get the name for the task
     * @return the task name
     */
    public String getName() {
        if (name == null)
            return this.getClass().toString();

        return name;
    }


    /**
     * Calling this method notifies the TaskScheduler (if the task has been scheduled)
     * and forces it to persist this task's data.
     * <p>
     * @exception various possible depending on persist strategy of the scheduler
     */
    public void persist()
            throws Exception {
        if (scheduler != null)
            scheduler.persist(this);
    }


    /**
     * Cancels this timer task. If the task has been scheduled for one-time execution and
     * has not yet run, or has not yet been scheduled, it will never run. If the task has
     * been scheduled for repeated execution, it will never run again. (If the task is
     * running when this call occurs, the task will run to completion, but will never run again.)
     * <p>
     * Note that calling this method from within the run method of a repeating timer task
     * absolutely guarantees that the timer task will not run again.
     * <p>
     * This method may be called repeatedly; the second and subsequent calls have no effect.
     *
     */
    public void cancel() {
        isCancelled = true;
        if (scheduler != null) {
            scheduler.cleanup(this);
        }
    }


    /**
     * Returns the scheduled execution time of the most recent actual execution of this task.
     * (If this method is invoked while task execution is in progress, the return value is
     * the scheduled execution time of the ongoing task execution.)
     * <p>
     * This method is typically invoked from within a task's run method, to determine whether
     * the current execution of the task is sufficiently timely to warrant performing the
     * scheduled activity:
     * <p>
     * <pre>
     public void run() {
     if (System.currentTimeMillis() - lastExecutionTime() >=
     MAX_TARDINESS)
     return;  // Too late; skip this execution.
     // Perform the task
     }
     </pre>
     *
     * <p>
     * @return the time at which the most recent execution of this task was scheduled to occur,
     *       in the format returned by Date.getTime(). The return value is undefined if the
     *       task has yet to commence its first execution.
     */
    public long lastExecutionTime() {
        return lastRunTime;
    }


    /**
     * Set the last run time. This is only called by the TaskScheduler
     * @param lastRunTime. The last run time
     */
    void setLastRunTime(long lastRunTime) {
        this.lastRunTime = lastRunTime;
    }


    /**
     * This method persists the task. Tasks should override this method
     * if they carry instance data. (Make sure to call super.writeExternal()
     * if you override).
     *
     * @param ObjectOutput The stream to write the object to
     * @exception IOException On write problems
     */
    public void writeExternal(ObjectOutput out)
            throws IOException {
        out.writeLong(this.scheduledInitialTime);
        out.writeLong(this.scheduledInterval);
        out.writeLong(this.lastRunTime);
        out.writeObject(this.name);
    }


    /**
     * This method instantiates the task from persistent storage.
     * Tasks should override this method if they carry instance data.
     * (Make sure to call super.writeExternal() if you override).
     * <p>
     * When a Task is instantiated from deserialization, the scheduledInitialTime
     * is set according to the following rules:
     * <ul>
     * <li>If the scheduledInitialTime is in the future, that time is kept.
     * <li>If the scheduledInitialTime is in the past and the Task is a repeating
     * task, then the scheduledInitialTime is set to the next time the task would run
     * had the Task not been serialized. For example, if a Task runs once a day at 1pm,
     * then the scheduledInitialTime will be set to the next 1pm running time. (The
     * logic does not distinguish between fixed-rate vs. fixed-delay Tasks).
     *
     * </ul>
     *
     *
     * @param ObjectInput The stream to write the object to
     * @exception IOException On write problems
     * @exception ClassNotFoundException On the class not being in the stream.
     */
    public void readExternal(ObjectInput in)
            throws IOException, java.lang.ClassNotFoundException {
        this.scheduledInitialTime = in.readLong();
        this.scheduledInterval = in.readLong();
        this.lastRunTime = in.readLong();
        this.name = (String) in.readObject();

        // set scheduledInitialTime to make it in the future
        if (scheduledInitialTime < System.currentTimeMillis()) {
            long n = System.currentTimeMillis() - scheduledInitialTime;
            n = n % scheduledInterval;
            this.scheduledInitialTime = System.currentTimeMillis() + n;
        }

        // assume all deserialized tasks had setSchedule() called
        hasBeenScheduled = true;
    }


    /**
     * This method is the action to be performed. This method should be overriden.
     * It will be called once at every time the task runs.
     * <p>
     * If a task encounters an error during run, it may call the cancel() method to
     * prevent the task from running again.
     * <p>
     * If the task modifies its instance data during execution, then it should call
     * the Task persist() method. This will notify the TaskScheduler to write the task
     * to persistent storage by calling the writeExternal() method.
     *
     */
    public abstract void run();


    /**
     * This method should provide a status message regarding the task. Care should
     * be taken to make sure the method is thread-safe, as code in the run()
     * method and the caller of getStatus() will likely be in different threads.
     *
     * @return a task-specific status message
     */
    public abstract String getStatus();


}