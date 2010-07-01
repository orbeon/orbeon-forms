/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.resources;

import org.orbeon.oxf.common.OXFException;

import java.util.Map;

/**
 * The FlatFile resource manager is able to load resources from a sandbox in a filesystem.
 *
 * NOTE: This resource manager is now deprecated. Use the Filsystem resource manager instead.
 */
public class FlatFileResourceManagerImpl extends FilesystemResourceManagerImpl {

    public FlatFileResourceManagerImpl(Map props) throws OXFException {
        super(props);
    }
}
