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

import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.properties.Property
import org.orbeon.oxf.xforms.XFormsProperties.AssetsBaselineUpdatesProperty
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.scalatest.funspec.AnyFunSpec


class XFormsAssetsTest extends AnyFunSpec{

  val FRPrefix    = "fr"
  val FR          = "http://orbeon.org/oxf/xml/form-runner"
  val FRNamespace = Namespace(FRPrefix, FR)

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
            { "full": "/webjars/nprogress/0.2.0/nprogress.css",                              "min": false },
            { "full": "/ops/css-loader/css-loader.css",                                      "min": false }
          ],

          "js": [
            { "full": "/webjars/jquery/3.6.1/dist/jquery.js",                                "min": true  },
            { "full": "/apps/fr/style/bootstrap/js/bootstrap.js",                            "min": true  },
            { "full": "/ops/javascript/orbeon/util/jquery-orbeon.js",                        "min": true  },
            { "full": "/webjars/nprogress/0.2.0/nprogress.js",                               "min": false },
            { "full": "/webjars/bowser/1.9.1/bowser.js",                                     "min": false },
            { "full": "/webjars/mousetrap/1.6.2/mousetrap.js",                               "min": true  },
            { "full": "/webjars/whatwg-fetch/3.0.0/dist/fetch.umd.js",                       "min": false },

            { "full": "/ops/web-streams-polyfill/ponyfill.es2018.js",                        "min": false },

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
            { "full": "/ops/javascript/orbeon/xforms/control/Placeholder.js",                "min": true  },

            { "full": "/ops/javascript/scalajs/orbeon-xforms-web.js",                        "min": false }
          ],

          "xbl": [
            "fr:number",
            "fr:date",
            "fr:code-mirror",
            "fr:section"
          ]
        }"""


  val XblBaseline = Set(
    QName("number",      FRNamespace),
    QName("date",        FRNamespace),
    QName("code-mirror", FRNamespace),
    QName("section",     FRNamespace),
  )

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
            { "full": "/webjars/nprogress/0.2.0/nprogress.css",                              "min": false },
            { "full": "/ops/css-loader/css-loader.css",                                      "min": false },
            { "full": "/apps/fr/assets/foo.css",                                             "min": false }
          ],

          "js": [
            { "full": "/webjars/jquery/3.6.1/dist/jquery.js",                                "min": true  },
            { "full": "/apps/fr/style/bootstrap/js/bootstrap.js",                            "min": true  },
            { "full": "/ops/javascript/orbeon/util/jquery-orbeon.js",                        "min": true  },
            { "full": "/webjars/nprogress/0.2.0/nprogress.js",                               "min": false },
            { "full": "/webjars/bowser/1.9.1/bowser.js",                                     "min": false },
            { "full": "/webjars/mousetrap/1.6.2/mousetrap.js",                               "min": true  },
            { "full": "/webjars/whatwg-fetch/3.0.0/dist/fetch.umd.js",                       "min": false },

            { "full": "/ops/web-streams-polyfill/ponyfill.es2018.js",                        "min": false },

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
            { "full": "/ops/javascript/orbeon/xforms/control/Placeholder.js",                "min": true  },

            { "full": "/apps/fr/resources/scalajs/orbeon-form-runner.js",                    "min": false }
          ],

          "xbl": [
            "fr:number",
            "fr:date",
            "fr:section",
            "fr:foo"
          ]
        }"""

  val Namespaces = Map(FRPrefix -> FR)

  describe(s"Updating assets") {

    val assets = XFormsAssetsBuilder.fromJsonString(BaselineAssets, Namespaces).xformsAssets

    val result =
      XFormsAssetsBuilder.updateAssets(
        globalAssetsBaseline = assets,
        globalXblBaseline    = XblBaseline,
        localExcludesProp    = Some("/ops/javascript/scalajs/orbeon-xforms-web.js /ops/yui/container/assets/skins/sam/container.css"),
        localUpdatesProp     = Some(
          Property(
            XS_STRING_QNAME,
            """+/apps/fr/resources/scalajs/orbeon-form-runner.js
               -/ops/yui/calendar/assets/skins/sam/calendar.css
               +/apps/fr/assets/foo.css
               -fr:code-mirror
               +fr:foo
            """,
            Namespaces,
            AssetsBaselineUpdatesProperty
          )
        )
      )

    it("must add and remove assets") {
      assert(XFormsAssetsBuilder.fromJsonString(ExpectedAssets, Namespaces) == result)
    }
  }
}
