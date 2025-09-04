package org.orbeon.fr.rpc

import java.net.URI
import scala.concurrent.Future


trait FormRunnerRpcApi {
  def retrieveResource(clientId: String): Future[Option[URI]]
  def setCurrentPage(pagerElementId: String, pageNumber: Int): Future[Unit]
}
