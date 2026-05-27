package org.orbeon.fr.rpc

import cats.implicits.*
import chameleon.ext.circe.*
import io.circe.Json
import io.circe.generic.auto.*
import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext
import sloth.*

import scala.concurrent.Future


object SlothRouter extends SlothRouterBase {
  override val ResponseEventName: String = "fr-rpc-response"
  override protected val router: RouterCo[Json, Future] =
    Router[Json, Future].route[FormRunnerRpcApi](FormRunnerRpcApiImpl)
}
