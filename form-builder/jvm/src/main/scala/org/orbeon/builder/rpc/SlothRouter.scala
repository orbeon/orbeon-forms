package org.orbeon.builder.rpc

import sloth.*

import scala.concurrent.Future
import chameleon.ext.circe.*
import io.circe.generic.auto.*
import cats.implicits.*
import io.circe.Json
import org.orbeon.fr.rpc.SlothRouterBase
import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext


object SlothRouter extends SlothRouterBase {
  override val ResponseEventName: String = "fb-rpc-response"
  override protected val router: RouterCo[Json, Future] =
    Router[Json, Future]
      .route[FormBuilderRpcApi](FormBuilderRpcApiImpl)
}
