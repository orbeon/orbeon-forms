<!--
    Copyright (C) 2007 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<xsl:transform version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
               xmlns:xforms="http://www.w3.org/2002/xforms"
               xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
               xmlns:f="http://orbeon.org/oxf/xml/formatting"
               xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <xsl:include href="oxf:/ops/utils/formatting/xml-formatting.xsl"/>

    <xsl:template match="/">

        <xhtml:html>
            <xhtml:head>
                <xhtml:title>Java API</xhtml:title>
            </xhtml:head>
            <xhtml:body>
                <xhtml:p>
                    This example shows how a custom processor written in Java can natively execute other processors or
                    pipelines. The custom processor runs an XSLT transformation, which reads a custom input passed to
                    the Java processor. The transformation applies the Orbeon Forms XML formatter to the input and
                    returns a custom output on the Java Processor.
                </xhtml:p>
                <xhtml:div class="ops-source">
                    <xsl:apply-templates mode="xml-formatting"/>
                </xhtml:div>
            </xhtml:body>
        </xhtml:html>

    </xsl:template>

</xsl:transform>

