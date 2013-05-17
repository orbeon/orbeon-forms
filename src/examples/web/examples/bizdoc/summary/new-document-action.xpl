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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param name="data" type="output"/>

    <!-- Generate an instance that is empty except for new ids -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="../schema/empty-instance.xml"/>
        <p:input name="config">
            <document-info xsl:version="2.0" xmlns:uuid="java:org.orbeon.oxf.util.UUIDUtils">
                <document-id>
                    <xsl:value-of select="uuid:createPseudoUUID()"/>
                </document-id>
                <document>
                    <xsl:copy-of select="/*"/>
                </document>
            </document-info>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
