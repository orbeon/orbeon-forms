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

import java.io.*;

/**
 * Implementation of TaskPersistStrategy to a file system.
 * @author Efraim Berkovich
 * @version 1.0
 */

public class FilePersistStrategy
        implements TaskPersistStrategy {

    protected String TASK_FILE_EXT = ".task";

    // instance variables
    private File persistStorage;
    private TaskFileFilter taskFileFilter;


    protected FilePersistStrategy() {
    }


    /**
     * Create the strategy. Set the directory which is our persistent storage.
     * @param directory The directory to which to write the files.
     * @exception IOException if the passed File is not a directory
     */
    public FilePersistStrategy(File directory)
            throws IOException {
        this.init(directory);
    }


    protected void init(File directory)
            throws IOException {
        persistStorage = directory;
        if (!persistStorage.isDirectory())
            throw new IOException("Expected a directory: " + directory.getPath());

        taskFileFilter = new TaskFileFilter();
    }


    /**
     * filters to .task files
     */
    private class TaskFileFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            if (name.endsWith(TASK_FILE_EXT))
                return true;
            else
                return false;
        }
    }


    /**
     * Read all the tasks in persisted storage
     * @exception IOException on file issues
     * @exception ClassNotFoundException on bad file
     */
    public Task[] readAll()
            throws IOException
            , ClassNotFoundException {
        synchronized (this) {
            ObjectInputStream inStream;
            FileInputStream file;

            File[] taskFiles = persistStorage.listFiles(taskFileFilter);
            Task[] tasks = new Task[taskFiles.length];

            for (int i = 0; i < taskFiles.length; i++) {
                file = new FileInputStream(taskFiles[i]);
                inStream = new ObjectInputStream(file);
                tasks[i] = (Task) inStream.readObject();
                inStream.close();
                file.close();
            }

            return tasks;
        }
    }


    /**
     * Write the task
     * @param task The Task to write
     *
     */
    public void write(Task task)
            throws IOException {
        synchronized (this) {
            ObjectOutputStream outStream;
            FileOutputStream file;

            String fileName = new String(persistStorage.getPath()
                    + File.separator
                    + task.getID()
                    + TASK_FILE_EXT);

            file = new FileOutputStream(fileName);
            outStream = new ObjectOutputStream(file);
            outStream.writeObject(task);
            outStream.close();
        }
    }


    /**
     * Delete the task
     */
    public void delete(Task task) {
        synchronized (this) {
            String fileName = new String(persistStorage.getPath()
                    + File.separator
                    + task.getID()
                    + TASK_FILE_EXT);

            File file = new File(fileName);

            file.delete();
        }
    }


    /**
     * Clear all tasks
     */
    public void deleteAll() {
        synchronized (this) {
            File[] taskFiles = persistStorage.listFiles(taskFileFilter);

            for (int i = 0; i < taskFiles.length; i++) {
                taskFiles[i].delete();
            }
        }
    }

}