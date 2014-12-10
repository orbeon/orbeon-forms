/**
 *  Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.client

import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.orbeon.oxf.client.fb._
import org.orbeon.oxf.client.fr.{Grid, Currency}

// List all client tests which we want to run with a single run of the driver
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CombinedClientTest
    extends OrbeonClientBase
    with RepeatSettings
    with XForms
    with FormRunnerSummary
    with OrbeonFormsDemoPath
    with ControlResourcesEditor
    with Permissions
    with Services
    with Currency
    with Grid
    with BasicControls