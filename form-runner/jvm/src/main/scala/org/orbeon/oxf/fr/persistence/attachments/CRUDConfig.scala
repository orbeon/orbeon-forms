/**
 * Copyright (C) 2025 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.attachments

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.fr.{AppForm, FormOrData, FormRunnerPersistence}
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.xml.NamespaceMapping


trait CRUDConfig {
  type C

  def config(appForm: AppForm, formOrData: FormOrData)(implicit propertySet: PropertySet): C

  def provider(
    appForm    : AppForm,
    formOrData : FormOrData
  )(implicit
    propertySet: PropertySet
  ): String =
    FormRunnerPersistence.findAttachmentsProvider(
      appForm,
      formOrData
    ).getOrElse {
      val propertySegment = Seq(
        appForm.app,
        appForm.form,
        formOrData.entryName
      ).mkString(".")

      throw new OXFException(s"Could not find attachments provider for `$propertySegment`")
    }

  def providerPropertyWithNs(
    provider   : String,
    property   : String,
    defaultOpt : Option[String]
  )(implicit
    propertySet: PropertySet
  ): (String, NamespaceMapping) =
    FormRunnerPersistence.providerPropertyWithNs(provider, property).getOrElse {
      defaultOpt.map(default => (default, NamespaceMapping.EmptyMapping)).getOrElse {
        throw new OXFException(s"Could not find $property property for provider `$provider`")
      }
    }

  def providerProperty(
    provider   : String,
    property   : String,
    defaultOpt : Option[String]
  )(implicit
    propertySet: PropertySet
  ): String =
    providerPropertyWithNs(provider, property, defaultOpt)._1
}
