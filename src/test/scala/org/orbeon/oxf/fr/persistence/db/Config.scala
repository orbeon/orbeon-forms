package org.orbeon.oxf.fr.persistence.db

import scala.util.Properties

private[persistence] object Config {

    def provider: Provider = Properties.propOrNone("oxf.test.db.provider") match {
        case Some("oracle")    ⇒ Oracle
        case Some("db2")       ⇒ DB2
        case Some("sqlserver") ⇒ SQLServer
        case _                 ⇒ MySQL
    }

    def jdbcURL: String = Properties.propOrNone("oxf.test.db.url") match {
        case Some(url)         ⇒ url
        case None              ⇒ "jdbc:mysql://localhost/"
    }
}
