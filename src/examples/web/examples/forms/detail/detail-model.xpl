<!--
    Copyright (C) 2006 Orbeon, Inc.

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
          xmlns:xi="http://www.w3.org/2001/XInclude">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:choose href="#instance">
        <p:when test="/*/document-id != ''">
            <!-- Call persistence layer -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="../services/data-access/read-document.xpl"/>
                <p:input name="document-id" href="#instance#xpointer(/*/document-id)"/>
                <p:output name="document-info" ref="data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Just produce empty document template -->
            <p:processor name="oxf:unsafe-xslt">
                <p:input name="config">
                    <document-info xsl:version="2.0" xmlns:uuid="java:org.orbeon.oxf.util.UUIDUtils">
                        <document-id><xsl:value-of select="uuid:createPseudoUUID()"/></document-id>
                        <document-date/>
                        <document>
                            <xsl:copy-of select="doc('../schema/template-form.xml')"/>
                        </document>
                    </document-info>
                </p:input>
                <p:input name="data"><dummy/></p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
