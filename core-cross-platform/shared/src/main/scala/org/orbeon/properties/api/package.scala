package org.orbeon.properties

import java.util as ju


package object api {
  type CacheKey            = String
  type ETag                = String
  type PropertyDefinitions = ju.Collection[PropertyDefinition]
}
