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
<xhtml:html xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xsl:version="2.0">
    <xhtml:head><xhtml:title>XForms Selection Controls</xhtml:title></xhtml:head>
    <xhtml:body>
        <xforms:group ref="/form">
            <table border="0" cellpadding="10" cellspacing="0">
                <tr>
                    <td align="right">Radio buttons:</td>
                    <td>
                        <xforms:select1 ref="carrier" appearance="full">
                            <xforms:item>
                                <xforms:label>Fedex</xforms:label>
                                <xforms:value>fedex</xforms:value>
                            </xforms:item>
                            <xforms:item>
                                <xforms:label>UPS</xforms:label>
                                <xforms:value>ups</xforms:value>
                            </xforms:item>
                        </xforms:select1>
                    </td>
                </tr>
                <tr>
                    <td align="right">Combo box:</td>
                    <td>
                        <xforms:select1 ref="payment" appearance="minimal">
                            <xforms:item>
                                <xforms:label>Cash</xforms:label>
                                <xforms:value>cash</xforms:value>
                            </xforms:item>
                            <xforms:item>
                                <xforms:label>Credit</xforms:label>
                                <xforms:value>credit</xforms:value>
                            </xforms:item>
                        </xforms:select1>
                    </td>
                </tr>
                <tr>
                    <td align="right">Check boxes:</td>
                    <td>
                        <xforms:select ref="wrapping" appearance="full">
                            <xforms:choices>
                                <xforms:item>
                                    <xforms:label>Hard-box</xforms:label>
                                    <xforms:value>box</xforms:value>
                                </xforms:item>
                                <xforms:item>
                                    <xforms:label>Gift</xforms:label>
                                    <xforms:value>gift</xforms:value>
                                </xforms:item>
                            </xforms:choices>
                        </xforms:select>
                    </td>
                </tr>
                <tr>
                    <td align="right">List:</td>
                    <td>
                        <xforms:select ref="taste" appearance="compact">
                            <xforms:item>
                                <xforms:label>Vanilla</xforms:label>
                                <xforms:value>vanilla</xforms:value>
                            </xforms:item>
                            <xforms:item>
                                <xforms:label>Strawberry</xforms:label>
                                <xforms:value>strawberry</xforms:value>
                            </xforms:item>
                        </xforms:select>
                    </td>
                </tr>
                <tr>
                    <td align="right">Marital status:</td>
                    <td>
                        <xforms:select1 ref="marital-status" appearance="full">
                            <xforms:itemset nodeset="/form/marital-status-choices/marital-status">
                            	<xforms:label ref="@label"/>
                            	<xforms:copy ref="@value"/>
                            </xforms:itemset>
                        </xforms:select1>
                    </td>
                </tr>
                <tr>
                    <td/>
                    <td>
                        <xforms:submit>
                            <xforms:label>Submit</xforms:label>
                        </xforms:submit>
                    </td>
                </tr>
                <tr>
                    <td align="right">XForms instance:</td>
                    <td>
                        <f:xml-source>
                            <xsl:copy-of select="/*"/>
                        </f:xml-source>
                    </td>
                </tr>
            </table>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
