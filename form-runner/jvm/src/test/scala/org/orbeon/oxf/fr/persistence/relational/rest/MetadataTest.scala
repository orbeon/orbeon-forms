/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.rest

import org.junit.Test
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.test.{ResourceManagerTestBase, XMLSupport}
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.scalatestplus.junit.AssertionsForJUnit

class MetadataTest extends ResourceManagerTestBase with AssertionsForJUnit with XMLSupport {

  @Test def extractMetadata(): Unit = {

    val is = ResourceManagerWrapper.instance.getContentAsStream("/org/orbeon/oxf/fr/form-with-metadata.xhtml")

    val (_, metadataOpt) = RequestReader.dataAndMetadataAsString(is, metadata = true)

    val expected =
      <metadata>
        <title xml:lang="en">ACME Order Form</title>
        <title xml:lang="fr">Formulaire de commande ACME</title>
        <permissions>
          <permission operations="read update delete">
            <group-member/>
          </permission>
          <permission operations="read update delete">
            <owner/>
          </permission>
          <permission operations="create"/>
        </permissions>
        <available>false</available>
      </metadata>.toDocument

    assertXMLDocumentsIgnoreNamespacesInScope(expected, metadataOpt map Dom4jUtils.readDom4j get)
  }
}
