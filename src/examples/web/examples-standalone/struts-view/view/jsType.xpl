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
          xmlns:oxf="http://www.orbeon.com/oxf/processors">
    
    <p:param type="output" name="data"/>

    <p:processor name="oxf:xalan">
        <p:input name="data"><dummy/></p:input>
        <p:input name="config">
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:struts="http://www.orbeon.com/oxf/struts">

                <xsl:include href="oxf:/oxf/struts/struts-support-xalan.xsl"/>

                <xsl:template match="/">
                    <xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml">
                        <xhtml:head>
                            <xhtml:title>OXF - Struts Javascript Validator Example</xhtml:title>
                            <xhtml:script type="text/javascript">
                                <xsl:value-of select="struts:javascript('jsTypeForm', 'true', 'true')"/>
                            </xhtml:script>
                        </xhtml:head>
                        <xhtml:body>
                          <f:example-header title="struts/jsType"/>
                            <br/>
                            <form action="jsType" onsubmit="return validateJsTypeForm(this);">

                                <xhtml:table class="gridtable" border="0">
                                    <xhtml:tr>
                                        <xhtml:th align="left">
                                            <xsl:value-of select="struts:message('typeForm.byte.displayname','messages')"/>
                                        </xhtml:th>
                                        <xhtml:td align="left">
                                            <input type="text" name="byte" size="15" maxlength="15"/>
                                        </xhtml:td>
                                    </xhtml:tr>
                                    <xhtml:tr>
                                        <xhtml:th align="left">
                                            <xsl:value-of select="struts:message('typeForm.short.displayname','messages')"/>
                                        </xhtml:th>
                                        <xhtml:td align="left">
                                            <input type="text" name="short" size="15" maxlength="15"/>
                                        </xhtml:td>
                                    </xhtml:tr>
                                    <xhtml:tr>
                                        <xhtml:th align="left">
                                            <xsl:value-of select="struts:message('typeForm.integer.displayname','messages')"/>
                                        </xhtml:th>
                                        <xhtml:td align="left">
                                            <input type="text" name="integer" size="15" maxlength="15"/>
                                        </xhtml:td>
                                    </xhtml:tr>
                                    <xhtml:tr>
                                        <xhtml:th align="left">
                                            <xsl:copy-of select="struts:message('typeForm.float.displayname','messages')"/>
                                        </xhtml:th>
                                        <xhtml:td align="left">
                                            <input type="text" name="float" size="15" maxlength="15"/>
                                        </xhtml:td>
                                    </xhtml:tr>

                                    <xhtml:tr>
                                        <xhtml:th align="left">
                                            <xsl:copy-of select="struts:message('typeForm.floatRange.displayname','messages')"/>
                                        </xhtml:th>
                                        <xhtml:td align="left">
                                            <input type="text" name="floatRange" size="15" maxlength="15"/>
                                        </xhtml:td>
                                    </xhtml:tr>
                                    <xhtml:tr>
                                        <xhtml:th align="left">
                                            <xsl:copy-of select="struts:message('typeForm.date.displayname','messages')"/>
                                            <xsl:text> (example: 10/10/2003)</xsl:text>
                                        </xhtml:th>
                                        <xhtml:td align="left">
                                            <input type="text" name="date" size="15" maxlength="15"/>
                                        </xhtml:td>
                                    </xhtml:tr>
                                    <xhtml:tr>
                                        <xhtml:th align="left">
                                            <xsl:value-of select="struts:message('typeForm.creditCard.displayname','messages')"/>
                                        </xhtml:th>
                                        <xhtml:td align="left">
                                            <input type="text" name="creditCard" size="16" maxlength="16"/>
                                        </xhtml:td>
                                    </xhtml:tr>
                                    <xhtml:tr>
                                        <xhtml:td colspan="2">
                                            <input type="submit" name="submit" onclick="bCancel=false;"/>
                                        </xhtml:td>
                                    </xhtml:tr>
                                </xhtml:table>
                            </form>

                            <xhtml:p><a href="../">Back</a></xhtml:p>

                        </xhtml:body>
                    </xhtml:html>

                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>
</p:config>
