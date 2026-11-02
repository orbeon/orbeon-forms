/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.portlet

import java.util as ju


object RenderParameters {
  def apply(renderParameters: javax.portlet.RenderParameters): RenderParameters     = new JavaxRenderParameters(renderParameters)
  def apply(renderParameters: jakarta.portlet.RenderParameters): RenderParameters = new JakartaRenderParameters(renderParameters)
}

trait RenderParameters {
  // javax/jakarta.portlet.RenderParameters
  def getNativeRenderParameters: AnyRef

  def getNames: ju.Set[String]
  def getValue(name: String): String
  def getValues(name: String): Array[String]
  def isEmpty: Boolean
  def isPublic(name: String): Boolean
  def size(): Int
}

class JavaxRenderParameters(renderParameters: javax.portlet.RenderParameters) extends RenderParameters {
  override def getNativeRenderParameters: javax.portlet.RenderParameters = renderParameters

  override def getNames: ju.Set[String] = renderParameters.getNames
  override def getValue(name: String): String = renderParameters.getValue(name)
  override def getValues(name: String): Array[String] = renderParameters.getValues(name)
  override def isEmpty: Boolean = renderParameters.isEmpty
  override def isPublic(name: String): Boolean = renderParameters.isPublic(name)
  override def size(): Int = renderParameters.size()
}

class JakartaRenderParameters(renderParameters: jakarta.portlet.RenderParameters) extends RenderParameters {
  override def getNativeRenderParameters: jakarta.portlet.RenderParameters = renderParameters

  override def getNames: ju.Set[String] = renderParameters.getNames
  override def getValue(name: String): String = renderParameters.getValue(name)
  override def getValues(name: String): Array[String] = renderParameters.getValues(name)
  override def isEmpty: Boolean = renderParameters.isEmpty
  override def isPublic(name: String): Boolean = renderParameters.isPublic(name)
  override def size(): Int = renderParameters.size()
}
