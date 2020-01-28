/**
  * Copyright (C) 2009 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.resources

import java.{util => ju}

import org.orbeon.oxf.common.OXFException

import scala.collection.JavaConverters._
import scala.collection.compat._

trait ResourceManagerFactory {
  def makeInstance: ResourceManager
}

/**
  * This is the main interface the to the Resource Manager system.
  */
object ResourceManagerWrapper {

  private val FactoryProperty = "oxf.resources.factory"

  private var _factory: ResourceManagerFactory = null

  def init(props: ju.Map[String, AnyRef]): Unit = synchronized {
    _factory =
      props.get(FactoryProperty) match {
        case factoryImpl: String =>
          try {
            val factoryClass = Class.forName(factoryImpl).asInstanceOf[Class[ResourceManagerFactory]]
            val constructor = factoryClass.getConstructor(classOf[ju.Map[_, _]])
            constructor.newInstance(props)
          } catch {
            case e: ClassNotFoundException =>
              throw new OXFException(s"Class `$factoryImpl` not found", e)
            case e: Exception =>
              throw new OXFException(s"Can't instantiate factory `$factoryImpl`", e)
          }
        case _ =>
          throw new OXFException("Declaration of resource factory missing: no value declared for property '" + FactoryProperty + "'")
      }
  }

  lazy val instance: ResourceManager = {
    assert(_factory ne null, "ResourceManagerWrapper not initialized")
    _factory.makeInstance
  }

  def propertiesAsJsonJava(props: ju.Map[String, AnyRef]): String =
    propertiesAsJson(props.asScala)

  def propertiesAsJson(props: collection.Map[String, AnyRef]): String = {

    val maxKeyLength =
      if (props.nonEmpty)
        props.keysIterator.maxBy(_.length).length
      else
        0

    props.to(List).sortBy(_._1).collect{
      case (key, value: String) => s""" "$key":${" " * (maxKeyLength + 1 - key.length)}"$value" """.trim
    }.mkString("{\n  ", ",\n  ", "\n}")
  }
}