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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="guess"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#guess"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0"  xmlns:struts="http://www.orbeon.com/oxf/struts">
                <xsl:import href="oxf:/oxf/struts/struts-support-saxon.xsl"/>

                <xsl:template match="/beans/guess">
                    <xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml">

                        <xhtml:head>
                            <xhtml:title>
                                <xsl:value-of select="struts:message('page.title')"/>
                            </xhtml:title>
                        </xhtml:head>
                        <xhtml:body>
                            <xhtml:h1>
                                <xsl:value-of select="struts:message('guess.title')"/>
                            </xhtml:h1>

                            <xhtml:p>
                                <b>
                                    <xsl:value-of select="struts:message('guess.win')"/>
                                </b>
                            </xhtml:p>
                            <xhtml:p>
                                <a href="guess">
                                    <xsl:value-of select="struts:message('guess.try-again')"/>
                                </a>
                            </xhtml:p>

                            <xhtml:p><a href="../">Back</a></xhtml:p>

                        </xhtml:body>
                    </xhtml:html>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>