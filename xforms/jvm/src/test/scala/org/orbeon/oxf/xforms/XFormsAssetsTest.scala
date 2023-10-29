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

import org.orbeon.oxf.properties.Property
import org.orbeon.oxf.xforms.XFormsProperties.AssetsBaselineUpdatesProperty
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.scalatest.funspec.AnyFunSpec


class XFormsAssetsTest extends AnyFunSpec{

  val BaselineAssets =
    """
      {
        "css": [
          { "full": "/ops/yui/container/assets/skins/sam/container.css",                   "min": false },
          { "full": "/apps/fr/style/bootstrap/css/bootstrap.css",                          "min": true  },
          { "full": "/apps/fr/style/form-runner-bootstrap-override.css",                   "min": false },
          { "full": "/apps/fr/style/fontawesome-free-6.3.0-web/css/all.css",               "min": true  },
          { "full": "/apps/fr/style/fontawesome-free-6.3.0-web/css/v4-shims.css",          "min": true  },
          { "full": "/config/theme/xforms.css",                                            "min": false },
          { "full": "/config/theme/error.css",                                             "min": false },
          { "full": "/ops/nprogress-0.2.0/nprogress.css",                                  "min": false }
        ],

        "js": [
          { "full": "/ops/jquery/jquery-3.6.1.js",                                         "min": true  },
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
          { "full": "/ops/yui/dragdrop/dragdrop.js",                                       "min": true  },
          { "full": "/ops/yui/container/container.js",                                     "min": true  },
          { "full": "/ops/yui/examples/container/assets/containerariaplugin.js",           "min": true  },

          { "full": "/ops/javascript/underscore/underscore.min.js",                        "min": false },

          { "full": "/ops/javascript/xforms.js",                                           "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/server/AjaxServer.js",                  "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/control/Placeholder.js",                "min": true  },

          { "full": "/ops/javascript/scalajs/orbeon-xforms.js",                            "min": false }
        ]
      }"""

  val ExpectedAssets =
    """
      {
        "css": [
          { "full": "/apps/fr/style/bootstrap/css/bootstrap.css",                          "min": true  },
          { "full": "/apps/fr/style/form-runner-bootstrap-override.css",                   "min": false },
          { "full": "/apps/fr/style/fontawesome-free-6.3.0-web/css/all.css",               "min": true  },
          { "full": "/apps/fr/style/fontawesome-free-6.3.0-web/css/v4-shims.css",          "min": true  },
          { "full": "/config/theme/xforms.css",                                            "min": false },
          { "full": "/config/theme/error.css",                                             "min": false },
          { "full": "/ops/nprogress-0.2.0/nprogress.css",                                  "min": false },
          { "full": "/apps/fr/assets/foo.css",                                             "min": false }
        ],

        "js": [
          { "full": "/ops/jquery/jquery-3.6.1.js",                                         "min": true  },
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
          { "full": "/ops/yui/dragdrop/dragdrop.js",                                       "min": true  },
          { "full": "/ops/yui/container/container.js",                                     "min": true  },
          { "full": "/ops/yui/examples/container/assets/containerariaplugin.js",           "min": true  },

          { "full": "/ops/javascript/underscore/underscore.min.js",                        "min": false },

          { "full": "/ops/javascript/xforms.js",                                           "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/server/AjaxServer.js",                  "min": true  },
          { "full": "/ops/javascript/orbeon/xforms/control/Placeholder.js",                "min": true  },

          { "full": "/apps/fr/resources/scalajs/orbeon-form-runner.js",                    "min": false }
        ]
      }"""

  // TODO: Test updating with XBL QNames.
  describe(s"Updating assets") {

    val assets = XFormsAssetsBuilder.fromJsonString(BaselineAssets, Map.empty)

    val result =
      XFormsAssetsBuilder.updateAssets(
        assets,
        Some("/ops/javascript/scalajs/orbeon-xforms.js /ops/yui/container/assets/skins/sam/container.css"),
        Some(
          Property(
            XS_STRING_QNAME,
            """+/apps/fr/resources/scalajs/orbeon-form-runner.js
               -/ops/yui/calendar/assets/skins/sam/calendar.css
               +/apps/fr/assets/foo.css
            """,
            Map.empty,
            AssetsBaselineUpdatesProperty
          )
        )
      )

    it("must add and remove assets") {
      assert(XFormsAssetsBuilder.fromJsonString(ExpectedAssets, Map.empty) == result)
    }
  }
}
