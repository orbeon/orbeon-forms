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


/**
 * Demo task class.
 * @author Efraim Berkovich
 * @version 1.0
 */

public class TestTask extends Task {

    private long numRun = 0;
    private Boolean semaphore = new Boolean(true);
    private String status = "Initialized.";

    public TestTask() {
        name = "Test task (" + getID() + ")";
    }


    public String getStatus() {
        synchronized (semaphore) {
            return status;
        }
    }


    public void run() {
        numRun++;
        synchronized (semaphore) {
            status = "Ran " + numRun + " times. ";
        }
        System.out.println(name + ": " + getStatus());
    }


    // test code
    public static void main(String[] argv) {
        try {
            // initialize
            FilePersistStrategy persister = new FilePersistStrategy(new java.io.File("C:\\TEMP"));
            TaskScheduler scheduler = TaskScheduler.getInstance();
            scheduler.setPersistStrategy(persister);

            // make some tasks
            TestTask task1 = new TestTask();
            TestTask task2 = new TestTask();
            TestTask task3 = new TestTask();

            task1.setSchedule(System.currentTimeMillis(), 5000);
            task2.setSchedule(System.currentTimeMillis(), 10000);
            task3.setSchedule(System.currentTimeMillis(), 3000);

            scheduler.schedule(task1);
            scheduler.schedule(task2);
            scheduler.schedule(task3);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}