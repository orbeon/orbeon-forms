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
 * This interface is used by the TaskScheduler if we want to implement
 * a strategy for persisting and reading task information.
 * <p>
 * @author Efraim Berkovich
 * @version 1.0
 */

public interface TaskPersistStrategy {
    /**
     * Write an individual task to storage.
     * @exception Various possible depending on strategy e.g. IOException
     */
    public void write(Task task) throws Exception;


    /**
     * Delete an individual task from storage.
     */
    public void delete(Task task);

    /**
     * Delete all tasks in storage.
     */
    public void deleteAll();

    /**
     * Read all the tasks in storage
     * @return array of Tasks
     * @exception Various possible depending on strategy e.g. IOException
     */
    public Task[] readAll() throws Exception;


}