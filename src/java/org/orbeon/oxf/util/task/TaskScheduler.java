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

// imports

import org.apache.log4j.Logger;
import org.orbeon.oxf.util.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Class for running Tasks with scheduling/thread pooling etc. <br>
 *
 * <p>
 * The tasks are Task objects. A user would extend the Task class and override
 * the abstract methods ( e.g. its run() method ).
 * </p>
 * <p>
 * Persistence of the Task objects allows the TaskScheduler to be shutdown and
 * restarted with all the scheduled tasks read from storage.
 * </p>
 *
 * @author Efraim Berkovich
 * @version 1.0
 *
 */

public class TaskScheduler {

    private static Logger logger = LoggerFactory.createLogger(TaskScheduler.class);

    // instance variables
    private Timer schedulerThread;
    private ArrayList taskList;
    private long initTime;

    private TaskPersistStrategy persistStrategy = null;


    /**
     * Create a task scheduler with a default thread pool size.
     */
    private TaskScheduler() {
        schedulerThread = new Timer(false);
        initTime = System.currentTimeMillis();
        taskList = new ArrayList();
    }


    /**
     * Set the particular methodology for persisting Tasks
     * @param strategy The TaskPersistStrategy to use
     */
    public void setPersistStrategy(TaskPersistStrategy strategy) {
        synchronized (this) {
            persistStrategy = strategy;
        }
    }


    /**
     * Get the time the TaskScheduler was started.
     * @return the time (in millis) when the Scheduler was started.
     */
    public long getSchedulerStartTime() {
        return initTime;
    }


    /**
     * Schedules the specified task for execution according to the task's
     * scheduling properties.
     * @param task The task to schedule for execution.
     *
     * @exception IllegalStateException if task was already scheduled or cancelled, timer
     *    was cancelled, or timer thread terminated.
     * @exception Exception if cannot persist task
     */
    public void schedule(Task task)
            throws IllegalStateException
            , Exception {
        synchronized (this) {
            cleanupAll();
            if (taskList.contains(task))
                throw new IllegalStateException("Task was already scheduled");

            Date time = new Date(task.getScheduledFirstTime());

            RunTask runTask = new RunTask(task);

            if (task.getScheduledInterval() <= 0) {
                schedulerThread.schedule(runTask, time);
            } else {
                schedulerThread.scheduleAtFixedRate(runTask, time, task.getScheduledInterval());
            }

            task.scheduler = this;
            taskList.add(task);

            this.persist(task);
        }
    }


    /**
     * Cancel all executing tasks and timer threads.
     * @param withRestart if true, all timer threads will restart; however, all scheduled task
     * will have been cancelled.
     *
     */
    public void cancelAll(boolean withRestart) {
        synchronized (this) {
            taskList.clear();
            schedulerThread.cancel();

            if (withRestart)
                schedulerThread = new Timer(false);
        }
    }


    /**
     * Fetch all non-cancelled tasks at the time the method is called. If tasks are
     * cancelling during this time, some cancelled tasks may be returned.
     *
     * @return array of Tasks which are not cancelled
     */
    public Task[] getRunningTasks() {
        ArrayList list = new ArrayList();
        cleanupAll();

        synchronized (this) {
            for (int i = 0; i < taskList.size(); i++) {
                Task task = (Task) taskList.get(i);
                if (!task.isCancelled()) {
                    list.add(task);
                }
            }
        }
        Task[] out = new Task[list.size()];
        for (int i = 0; i < out.length; i++)
            out[i] = (Task) list.get(i);

        return out;
    }


    /**
     * Find a particular task by its ID
     * @param taskID The task to find
     * @return Task for this ID or null if not found
     */
    public Task findTaskByID(long taskID) {
        synchronized (this) {
            for (int i = 0; i < taskList.size(); i++) {
                Task task = (Task) taskList.get(i);
                if (task.getID() == taskID) {
                    return task;
                }
            }
        }
        return null;
    }


    /**
     * Check and remove a particular task
     * @param task the potentially cancelled task to remove
     */
    void cleanup(Task task) {
        synchronized (this) {
            if (taskList.contains(task)) {
                if (task.isCancelled()) {
                    taskList.remove(task);
                    if (persistStrategy != null)
                        persistStrategy.delete(task);
                }
            }
        }
    }


    /**
     * Persists the task by telling the persist strategy to do so
     * @exception various possible depending on strategy
     */
    void persist(Task task)
            throws Exception {
        synchronized (this) {
            if (persistStrategy != null)
                persistStrategy.write(task);
        }
    }


    /**
     * Go through the taskList and eliminate cancelled tasks
     */
    private void cleanupAll() {
        synchronized (this) {
            for (int i = 0; i < taskList.size(); i++) {
                Task task = (Task) taskList.get(i);
                if (task.isCancelled()) {
                    taskList.remove(i);
                    if (persistStrategy != null)
                        persistStrategy.delete(task);
                    i--;
                }
            }
        }
    }


    // static variables
    private static Boolean semaphore = new Boolean(true);
    private static TaskScheduler globalTaskScheduler = null;


    /**
     * Get a singleton TaskScheduler. This can be used if the application wants
     * to share one task scheduler across the whole JVM.
     */
    public static TaskScheduler getInstance() {
        synchronized (semaphore) {
            if (globalTaskScheduler == null)
                globalTaskScheduler = new TaskScheduler();

            return globalTaskScheduler;
        }
    }


    /**
     * Shutdown the scheduler
     */
    public static void shutdown() {
        synchronized (semaphore) {
            globalTaskScheduler.schedulerThread.cancel();
        }
    }


    /**
     * RunTask class is scheduler to on the Timer
     * and is associated with a Task which it runs.
     */
    private class RunTask extends TimerTask {
        // instance variables
        private Task task;


        /**
         * Create a new RunTask
         * @param task The Task to run
         */
        public RunTask(Task task) {
            this.task = task;
        }


        /**
         * Run the Task
         */
        public void run() {
            if (task.isCancelled()) {
                this.cancel();
                return;
            }

            // NOTE: TBD
            //    For now we just launch a thread to do the task.
            //    This is potentially not scaleable. In the future,
            //    we will pick threads from a pool.

            Thread runner = new Thread(task);
            runner.start();
            task.setLastRunTime(System.currentTimeMillis());
        }

    }


}