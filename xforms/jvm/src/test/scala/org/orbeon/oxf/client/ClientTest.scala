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
import org.orbeon.oxf.client.fr.{ClientGridTest, ClientCurrencyTest}

// List all client tests which we want to run with a single run of the driver
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ClientTest
  extends OrbeonClientBase
  with ClientRepeatSettingsTest
  with ClientXFormsTest
  with ClientFormRunnerSummaryTest
  with ClientOrbeonFormsDemoPathTest
  with ClientControlResourcesEditorTest
  with ClientPermissionsTest
  with ClientServicesTest
  with ClientCurrencyTest
  with ClientGridTest
  with ClientBasicControlsTest
  with ClientNewFormParamsTest