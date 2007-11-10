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
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:v="http://orbeon.org/oxf/xml/validation"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <!-- Parse input document -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance#xpointer(/*/*[1])"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:template match="/">
                    <xsl:copy-of select="saxon:parse(string(/))"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="input-parsed"/>
    </p:processor>

    <!-- Parse schema -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance#xpointer(/*/*[2])"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:template match="/">
                    <xsl:copy-of select="saxon:parse(string(/))"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="schema-parsed"/>
    </p:processor>

    <!-- Validate input document with schema -->
    <p:processor name="oxf:validation">
        <p:input name="data" href="#input-parsed"/>
        <p:input name="schema" href="#schema-parsed"/>
        <p:input name="config">
            <config>
                <decorate>true</decorate>
            </config>
        </p:input>
        <p:output name="data" id="validation"/>
    </p:processor>
    
    <!-- Analyze output from validation -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#validation"/>
        <p:input name="config">
            <div xsl:version="2.0">
                <xsl:choose>
                    <xsl:when test="//v:error">
                        The document is <span style="color: red">not valid</span> according to the schema:
                        <ol>
                           <xsl:for-each select="//v:error">
                               <li>
                                   <xsl:value-of select="substring-before(@message, '(schema: null)')"/>
                               </li>
                           </xsl:for-each>
                        </ol>
                    </xsl:when>
                    <xsl:otherwise>
                        The document is <span style="color: green">valid</span> according to the schema
                    </xsl:otherwise>
                </xsl:choose>
            </div>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
