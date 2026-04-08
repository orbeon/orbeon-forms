package org.orbeon.oxf.util

import java.time.OffsetDateTime


trait DateUtilsPlatform {

  // Default timezone offset in minutes
  // This is obtained once at the time the current object initializes. This searches `user.timezone`, the JDK timezone,
  // and then UTC in order. This is ony used for setting an absolute point in time before subtracting.
  val DefaultOffsetMinutes: Int =
    OffsetDateTime.now.getOffset.getTotalSeconds / 60
}
