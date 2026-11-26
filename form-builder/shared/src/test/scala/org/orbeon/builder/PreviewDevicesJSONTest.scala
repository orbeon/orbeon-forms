/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.builder

import io.circe.syntax.*
import org.orbeon.builder.PreviewDevicesJSON.previewDeviceEncoder
import org.scalatest.funspec.AnyFunSpec


class PreviewDevicesJSONTest extends AnyFunSpec {

  describe("PreviewDevicesJSON.parseDevices") {

    it("must round-trip valid device definitions") {
      val devices = List(
        PreviewDevice("phone-1",   "Phone 1",   390,  844,  PreviewDeviceCategory.Mobile),
        PreviewDevice("tablet-1",  "Tablet 1",  820,  1180, PreviewDeviceCategory.Tablet),
        PreviewDevice("desktop-1", "Desktop 1", 1440, 900,  PreviewDeviceCategory.Desktop)
      )
      val json   = devices.asJson.noSpaces
      val parsed = PreviewDevicesJSON.parseDevices(json)
      assert(parsed == devices)
    }

    it("must skip malformed entries and keep valid ones") {
      val json =
        """[
          |  {"id": "valid-phone", "name": "Valid Phone", "width": 393, "height": 852, "category": "mobile"},
          |  {"id": "", "name": "Blank ID", "width": 400, "height": 800, "category": "mobile"},
          |  {"id": "bad-dim", "name": "Zero Width", "width": 0, "height": 800, "category": "mobile"},
          |  {"id": "bad-cat", "name": "Watch", "width": 300, "height": 300, "category": "watch"},
          |  {"id": "valid-tab", "name": "Valid Tablet", "width": 820, "height": 1180, "category": "Tablet"}
          |]""".stripMargin

      val parsed = PreviewDevicesJSON.parseDevices(json)
      assert(
        parsed == List(
          PreviewDevice("valid-phone", "Valid Phone",  393, 852,  PreviewDeviceCategory.Mobile),
          PreviewDevice("valid-tab",   "Valid Tablet", 820, 1180, PreviewDeviceCategory.Tablet)
        )
      )
    }

    it("must fall back to DefaultDevices for empty or invalid JSON") {
      assert(PreviewDevicesJSON.parseDevices("") == PreviewDevicesJSON.DefaultDevices)
      assert(PreviewDevicesJSON.parseDevices("not json") == PreviewDevicesJSON.DefaultDevices)
      assert(PreviewDevicesJSON.parseDevices("[]") == PreviewDevicesJSON.DefaultDevices)
    }
  }

  describe("PreviewDevicesJSON viewport math") {

    it("must clamp dimensions within bounds") {
      assert(PreviewDevicesJSON.clampDimensions(100, 100) == (240, 240))
      assert(PreviewDevicesJSON.clampDimensions(5000, 3000) == (3840, 2160))
      assert(PreviewDevicesJSON.clampDimensions(393, 852) == (393, 852))
    }

    it("must rotate dimensions") {
      assert(PreviewDevicesJSON.rotateDimensions(393, 852) == (852, 393))
    }

    it("must compute fit scale without upscaling above 1.0") {
      assert(PreviewDevicesJSON.computeFitScale(393, 852, 1200.0, 900.0) == 1.0)
      assert(PreviewDevicesJSON.computeFitScale(1440, 900, 720.0, 900.0) == 0.5)
      assert(PreviewDevicesJSON.computeFitScale(1024, 1366, 1200.0, 683.0) == 0.5)
      assert(PreviewDevicesJSON.computeFitScale(1000, 1000, 0.0, 500.0) == 1.0)
    }

    it("must classify Form Runner responsive breakpoints") {
      assert(PreviewDevicesJSON.responsiveBreakpointKey(393) == "compact")
      assert(PreviewDevicesJSON.responsiveBreakpointKey(640) == "compact")
      assert(PreviewDevicesJSON.responsiveBreakpointKey(641) == "medium")
      assert(PreviewDevicesJSON.responsiveBreakpointKey(980) == "medium")
      assert(PreviewDevicesJSON.responsiveBreakpointKey(981) == "wide")
    }
  }
}
