<!--
  Copyright (C) 2011 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
          xmlns:xhtml="http://www.w3.org/1999/xhtml"
          xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <p:param type="input" name="data"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#data"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

                <xsl:template match="xhtml:body//fb:view | xhtml:body//fb:section | xhtml:body//fr:grid">
                    <xsl:element name="fr:{local-name()}">
                        <xsl:apply-templates select="@* except @edit-ref | node()"/>
                    </xsl:element>
                </xsl:template>

                <!-- Remove automatic td ids -->
                <xsl:template match="xhtml:body//fr:grid//*:td/@id[ends-with(., '-td')]"/>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>