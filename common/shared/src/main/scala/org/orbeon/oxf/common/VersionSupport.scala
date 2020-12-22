package org.orbeon.oxf.common

import org.orbeon.oxf.util.StringUtils._


object VersionSupport {

  def compare(leftVersion: String, rightVersion: String): Option[Int] = {
    (majorMinor(leftVersion), majorMinor(rightVersion)) match {
      case (Some((leftMajor, leftMinor)), Some((rightMajor, rightMinor))) =>
        if      (leftMajor > rightMajor || (leftMajor == rightMajor && leftMinor > rightMinor)) Some( 1)
        else if (leftMajor < rightMajor || (leftMajor == rightMajor && leftMinor < rightMinor)) Some(-1)
        else                                                                                    Some( 0)
      case _ =>
        None
    }
  }

  private def majorMinor(versionString: String): Option[(Int, Int)] = {
    // Allow `-` as separator as well so we can handle things like "2016.3-SNAPSHOT"
    val ints = versionString.splitTo[Array](sep = ".-") take 2 map (_.toInt)
    if (ints.size == 2) Some(ints(0), ints(1)) else None
  }
}
