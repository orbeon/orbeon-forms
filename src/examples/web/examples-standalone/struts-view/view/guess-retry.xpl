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
    <p:param type="input" name="errors"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:bean-generator">
        <p:input name="config">
            <config>
                <attribute>guess-hint</attribute>
                <source>request</source>
                <source>session</source>
            </config>
        </p:input>
        <p:input name="mapping">
            <mapping/>
        </p:input>
        <p:output name="data" id="hint"/>
    </p:processor>

    <p:processor name="oxf:xalan">
        <p:input name="data" href="aggregate('root',#guess, #errors, #hint)"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0" xmlns:struts="http://www.orbeon.com/oxf/struts">

                <xsl:import href="oxf:/oxf/struts/struts-support-xalan.xsl"/>

                <xsl:template match="/root/beans/guess">
                    <xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml">
                        <xhtml:head>
                            <xhtml:title>
                                <xsl:value-of select="struts:message('page.title')"/>
                            </xhtml:title>
                        </xhtml:head>
                        <xhtml:body>
                            <f:example-header title="struts/guess"/>
                            <xhtml:h1>
                                <xsl:value-of select="struts:message('guess.title')"/>
                            </xhtml:h1>

                            <xhtml:p>
                                Hint: <xsl:value-of select="/root/beans/guess-hint/@hint"/>
                            </xhtml:p>

                            <xhtml:p>
                                <xsl:value-of select="message"/>
                            </xhtml:p>
                            <form action="guess">
                                <xsl:if test="/root/errors/error">
                                    <xhtml:p>
                                        <xsl:value-of select="/root/errors/error[@property='errors.header']"/>
                                        <br/>
                                        <xsl:for-each select="/root/errors/error[not(starts-with(@property, 'errors.'))]">
                                            -
                                            <xsl:value-of select="."/>
                                            <br/>
                                        </xsl:for-each>
                                        <xsl:value-of select="/root/errors/error[@property='errors.footer']"/>
                                        <br/>
                                    </xhtml:p>
                                </xsl:if>
                                <xhtml:p>
                                    <xsl:value-of select="struts:message('guess.enter')"/>
                                    <xsl:text>&#160;</xsl:text>
                                    <input type="text" name="userGuess"/>
                                    <xsl:text>&#160;</xsl:text>
                                    <input type="submit" value="Go !"/>
                                </xhtml:p>
                            </form>
                            <xhtml:p>
                                <a href="../">Back</a>
                            </xhtml:p>
                        </xhtml:body>
                    </xhtml:html>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>