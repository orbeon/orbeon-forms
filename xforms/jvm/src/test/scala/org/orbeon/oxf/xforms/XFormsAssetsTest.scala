/**
 * Copyright (C) 2019 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms

import org.scalatest.funspec.AnyFunSpec


class XFormsAssetsTest extends AnyFunSpec{

  val BaselineAssets =
    """
      {
        "css": [
          { "full": "/ops/yui/container/assets/skins/sam/container.css",                   "min": false },
          { "full": "/ops/yui/progressbar/assets/skins/sam/progressbar.css",               "min": false },
          { "full": "/ops/yui/calendar/assets/skins/sam/calendar.css",                     "min": false },
          { "full": "/apps/fr/style/bootstrap/css/bootstrap.css",                          "min": true  },
          { "full": "/apps/fr/style/form-runner-bootstrap-override.css",                   "min": false },
          { "full": "/apps/fr/style/font-awesome/css/font-awesome.css",                    "min": true  },
          { "full": "/config/theme/xforms.css",                                            "min": false },
          { "full": "/config/theme/error.css",                                             "min": false },
          { "full": "/ops/nprogress-0.2.0/nprogress.css",                                  "min": false }
        ],

        "js": [
          { "full": "/ops/jquery/jquery-3.3.1.js",                                         "min": true  },
          { "full": "/ops/jquery/jquery-browser-mobile.js",                                "min": true  },
          { "full": "/apps/fr/style/bootstrap/js/bootstrap.js",                            "min": true  },
          { "full": "/ops/javascript/orbeon/util/jquery-orbeon.js",                        "min": true  },
          { "full": "/ops/nprogress-0.2.0/nprogress.js",                                   "min": true  },
          { "full": "/ops/bowser/bowser.js",                                               "min": true  },
          { "full": "/ops/mousetrap/mousetrap.min.js",                                     "min": false },

          { "full": "/ops/yui/yahoo/yahoo.js",                                             "min": true  },
          { "full": "/ops/yui/event/event.js",                                             "min": true  },
          { "full": "/ops/yui/dom/dom.js",                                                 "min": true  },
          { "full": "/ops/yui/element/element.js",                                         "min": true  },
          { "full": "/ops/yui/animation/animation.js",                                     "min": true  },
          { "full": "/ops/yui/progressbar/progressbar.js",                                 "min": true  },
          { "full": "/ops/yui/dragdrop/dragdrop.js",                                       "min": true  },
          { "full": "/ops/yui/container/container.js",                                     "min": true  },
          { "full": "/ops/yui/examples/container/assets/containerariaplugin.js",           "min": true  },
          { "full": "/ops/yui/calendar/calendar.js",                                       "min": true  },
          { "full": "/ops/yui/slider/slider.js",                                           "min": true  },

          { "full": "/ops/javascript/underscore/underscore.js",                            "min": true  },

          { "full": "/ops/javascript/xforms.js",                                           "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/server/AjaxServer.js",                  "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/control/CalendarResources.js",          "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/control/Calendar.js",                   "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/control/Placeholder.js",                "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/controls/Placement.js",                 "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/controls/Help.js",                      "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/controls/Hint.js",                      "min": true  },

          { "full": "/ops/javascript/scalajs/orbeon-xforms.js",                            "min": false }
        ]
      }"""

  val ExpectedAssets =
    """
      {
        "css": [
          { "full": "/apps/fr/style/bootstrap/css/bootstrap.css",                          "min": true  },
          { "full": "/apps/fr/style/form-runner-bootstrap-override.css",                   "min": false },
          { "full": "/apps/fr/style/font-awesome/css/font-awesome.css",                    "min": true  },
          { "full": "/config/theme/xforms.css",                                            "min": false },
          { "full": "/config/theme/error.css",                                             "min": false },
          { "full": "/ops/nprogress-0.2.0/nprogress.css",                                  "min": false },
          { "full": "/apps/fr/assets/foo.css",                                             "min": false }
        ],

        "js": [
          { "full": "/ops/jquery/jquery-3.3.1.js",                                         "min": true  },
          { "full": "/ops/jquery/jquery-browser-mobile.js",                                "min": true  },
          { "full": "/apps/fr/style/bootstrap/js/bootstrap.js",                            "min": true  },
          { "full": "/ops/javascript/orbeon/util/jquery-orbeon.js",                        "min": true  },
          { "full": "/ops/nprogress-0.2.0/nprogress.js",                                   "min": true  },
          { "full": "/ops/bowser/bowser.js",                                               "min": true  },
          { "full": "/ops/mousetrap/mousetrap.min.js",                                     "min": false },

          { "full": "/ops/yui/yahoo/yahoo.js",                                             "min": true  },
          { "full": "/ops/yui/event/event.js",                                             "min": true  },
          { "full": "/ops/yui/dom/dom.js",                                                 "min": true  },
          { "full": "/ops/yui/element/element.js",                                         "min": true  },
          { "full": "/ops/yui/animation/animation.js",                                     "min": true  },
          { "full": "/ops/yui/progressbar/progressbar.js",                                 "min": true  },
          { "full": "/ops/yui/dragdrop/dragdrop.js",                                       "min": true  },
          { "full": "/ops/yui/container/container.js",                                     "min": true  },
          { "full": "/ops/yui/examples/container/assets/containerariaplugin.js",           "min": true  },
          { "full": "/ops/yui/calendar/calendar.js",                                       "min": true  },
          { "full": "/ops/yui/slider/slider.js",                                           "min": true  },

          { "full": "/ops/javascript/underscore/underscore.js",                            "min": true  },

          { "full": "/ops/javascript/xforms.js",                                           "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/server/AjaxServer.js",                  "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/control/CalendarResources.js",          "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/control/Calendar.js",                   "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/control/Placeholder.js",                "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/controls/Placement.js",                 "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/controls/Help.js",                      "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/controls/Hint.js",                      "min": true  },

          { "full": "/apps/fr/resources/scalajs/orbeon-form-runner.js",                    "min": false }
        ]
      }"""

  describe(s"Updating assets") {
    val assets = XFormsAssets.fromJsonString(BaselineAssets)

    val result =
      XFormsAssets.updateAssets(
        assets,
        "/ops/javascript/scalajs/orbeon-xforms.js /ops/yui/container/assets/skins/sam/container.css",
        """+/apps/fr/resources/scalajs/orbeon-form-runner.js
           -/ops/yui/calendar/assets/skins/sam/calendar.css
           +/apps/fr/assets/foo.css
           -/ops/yui/progressbar/assets/skins/sam/progressbar.css
        """.stripMargin
      )

    it("must add and remove assets") {
      assert(XFormsAssets.fromJsonString(ExpectedAssets) == result)
    }
  }
}
