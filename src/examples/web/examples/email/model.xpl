<!--
    Copyright (C) 2004 Orbeon, Inc.
  
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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <!-- Strip instance of XForms annotations -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xsl:transform version="2.0" xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="@xxforms:*"/>
            </xsl:transform>
        </p:input>
        <p:output name="data" id="stripped-instance"/>
    </p:processor>

    <p:choose href="#instance">
        <p:when test="not(//@xxforms:valid = 'false')">

            <!-- Get PDF data -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="get-pdf-data.xpl"/>
                <p:output name="data" id="pdf-document"/>
            </p:processor>

            <!-- Get PNG Chart data -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="get-chart-data.xpl"/>
                <p:output name="data" id="png-document"/>
            </p:processor>

            <!-- Send email -->
            <p:processor name="oxf:email">
                <!-- The instance contains the email message -->
                <p:input name="data" href="#stripped-instance"/>
                <!-- Custom input with PDF document -->
                <p:input name="pdf-document" href="#pdf-document"/>
                <!-- Custom input with PNG document -->
                <p:input name="png-document" href="#png-document"/>
            </p:processor>

            <!-- Return status document -->
            <p:processor name="oxf:identity">
                <p:input name="data">
                    <status>success</status>
                </p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Return status document -->
            <p:processor name="oxf:identity">
                <p:input name="data">
                    <status>failure</status>
                </p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
