package org.orbeon.oxf.fr.persistence.db

import scala.util.Properties

private[persistence] object Config {

  def jdbcURL: String = Properties.propOrNone("oxf.test.db.url") match {
    case Some(url)         ⇒ url
    case None              ⇒ "jdbc:mysql://localhost/"
  }
}
