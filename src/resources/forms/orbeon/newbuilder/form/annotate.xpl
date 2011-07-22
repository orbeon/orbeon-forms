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

                <xsl:template match="xhtml:body//fr:view">
                    <xsl:element name="fb:{local-name()}">
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:element>
                </xsl:template>

                <xsl:template match="xhtml:body//fr:section">
                    <xsl:element name="fb:{local-name()}">
                        <xsl:attribute name="edit-ref"/>
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:element>
                </xsl:template>

                <xsl:template match="xhtml:body//fr:grid">
                    <xsl:copy>
                        <xsl:attribute name="edit-ref"/>
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Saxon serialization adds an extra meta element, make sure to remove it -->
                <xsl:template match="xhtml:head/meta[@http-equiv = 'Content-Type']"/>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>