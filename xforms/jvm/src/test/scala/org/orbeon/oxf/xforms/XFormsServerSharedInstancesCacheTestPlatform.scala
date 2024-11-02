package org.orbeon.oxf.xforms

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}


// Prepare for when we can move `XFormsServerSharedInstancesCacheTest` to be JVM/JS
trait XFormsServerSharedInstancesCacheTestPlatform
  extends DocumentTestBase
     with ResourceManagerSupport