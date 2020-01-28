/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.xforms.rpc

import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

// As we use this on the client and the server
trait JsonSerializers extends autowire.Serializers[Json, Decoder, Encoder] {

  def read[Result : Decoder](json: Json): Result =
    json.as[Result] match {
      case Left(t)  => throw t// TODO: DecodingFailure
      case Right(r) => r
    }

  def write[Result : Encoder](r: Result): Json = r.asJson
}
