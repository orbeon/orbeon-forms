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
<html xsl:version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xi="http://www.w3.org/2001/XInclude"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:claim="http://orbeon.org/oxf/examples/bizdoc/claim"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns="http://www.w3.org/1999/xhtml">

    <head>
        <title>Detail - Step 2</title>
    </head>
    <body>
        <div class="maincontent">
            <xforms:group ref="/form" xxforms:show-errors="{doc('input:instance')/form/show-errors = 'true'}">
                <xi:include href="../summary/view-logo.xml"/>
                <xi:include href="detail-view-header.xml"/>
                <xforms:group ref="document">
                    <xforms:group ref="claim:claim/claim:insured-info">
                        <h2 style="margin-top: 0">Additonal Claimant Information</h2>
                        <table>
                            <xforms:group ref="claim:person-info">
                                <tr>
                                    <th align="right" valign="middle">Gender</th>
                                    <td>
                                        <xforms:select1 ref="claim:gender-code" appearance="full">
                                            <xforms:hint>Gender</xforms:hint>
                                            <xforms:help>Please select a gender here</xforms:help>
                                            <div style="width: 200px">
                                                <xforms:item>
                                                    <xforms:label>Male</xforms:label>
                                                    <xforms:value>M</xforms:value>
                                                </xforms:item>
                                                <xforms:item>
                                                    <xforms:label>Female</xforms:label>
                                                    <xforms:value>F</xforms:value>
                                                </xforms:item>
                                                <xforms:item>
                                                    <xforms:label>Unknown</xforms:label>
                                                    <xforms:value>U</xforms:value>
                                                </xforms:item>
                                            </div>
                                        </xforms:select1>
                                    </td>
                                </tr>
                                <tr>
                                    <th align="right" valign="middle">Birth Date</th>
                                    <td>
                                        <xforms:input ref="claim:birth-date" xhtml:style="width: 200px">
                                            <xforms:hint>Birth Date</xforms:hint>
                                            <xforms:help>Please enter a birth date here (e.g. 1970-02-25)</xforms:help>
                                        </xforms:input>
                                    </td>
                                </tr>
                                <tr>
                                    <th align="right" valign="middle">Marital Status</th>
                                    <td>
                                        <xforms:select1 ref="claim:marital-status-code" appearance="compact" xhtml:style="width: 200px">
                                            <xforms:hint>Marital Status</xforms:hint>
                                            <xforms:help>Please select a marital status here</xforms:help>
                                            <xforms:item>
                                                <xforms:label>Domestic Partner</xforms:label>
                                                <xforms:value>C</xforms:value>
                                            </xforms:item>
                                            <xforms:item>
                                                <xforms:label>Divorced</xforms:label>
                                                <xforms:value>D</xforms:value>
                                            </xforms:item>
                                            <xforms:item>
                                                <xforms:label>Married</xforms:label>
                                                <xforms:value>M</xforms:value>
                                            </xforms:item>
                                            <xforms:item>
                                                <xforms:label>Separated</xforms:label>
                                                <xforms:value>P</xforms:value>
                                            </xforms:item>
                                            <xforms:item>
                                                <xforms:label>Single</xforms:label>
                                                <xforms:value>S</xforms:value>
                                            </xforms:item>
                                            <xforms:item>
                                                <xforms:label>Unknown</xforms:label>
                                                <xforms:value>U</xforms:value>
                                            </xforms:item>
                                            <xforms:item>
                                                <xforms:label>Widowed</xforms:label>
                                                <xforms:value>W</xforms:value>
                                            </xforms:item>
                                        </xforms:select1>
                                    </td>
                                </tr>
                                <tr>
                                    <th align="right" valign="middle">Occupation</th>
                                    <td>
                                        <xforms:input ref="claim:occupation" xhtml:style="width: 200px">
                                            <xforms:hint>Occupation</xforms:hint>
                                            <xforms:help>Please enter an occupation here</xforms:help>
                                        </xforms:input>
                                    </td>
                                </tr>
                            </xforms:group>
                            <xforms:group ref="claim:family-info">
                                <tr>
                                    <th>Comments</th>
                                    <td colspan="4">
                                        <xforms:textarea ref="claim:comments" xhtml:style="width: 200px; height: 10em; font-family: Verdana;">
                                            <xforms:hint>Comments</xforms:hint>
                                            <xforms:help>Please enter comments here</xforms:help>
                                        </xforms:textarea>
                                    </td>
                                </tr>
                            </xforms:group>
                        </table>
                        <h2 style="margin-top: 0">Children</h2>
                        <xforms:group ref="claim:family-info">
                            <table>
                                <tr>
                                    <th align="left">Birth Date</th>
                                    <th align="left">Name</th>
                                </tr>
                                <xforms:repeat nodeset="claim:children/claim:child" id="childSet">
                                    <tr>
                                        <td>
                                            <xforms:input ref="claim:birth-date">
                                                <xforms:hint>Birth Date</xforms:hint>
                                                <xforms:help>Please enter a birth date here (e.g. 1970-02-25)</xforms:help>
                                            </xforms:input>
                                        </td>
                                        <td>
                                            <xforms:input ref="claim:first-name">
                                                <xforms:hint>First Name</xforms:hint>
                                                <xforms:help>Please enter a first name here</xforms:help>
                                            </xforms:input>
                                        </td>
                                        <td>
                                            <xforms:submit>
                                                <xforms:label>X</xforms:label>
                                                <xforms:delete nodeset="/form/document/claim:claim/claim:insured-info/claim:family-info/claim:children/claim:child" at="index('childSet')"/>
                                            </xforms:submit>
                                        </td>
                                    </tr>
                                </xforms:repeat>
                                <tr>
                                    <td colspan="2">
                                        <xforms:submit>
                                            <xforms:label>Add Child</xforms:label>
                                            <xforms:insert nodeset="claim:children/claim:child" at="last()" position="after"/>
                                        </xforms:submit>
                                    </td>
                                </tr>
                            </table>
                        </xforms:group>

                        <h2 style="margin-top: 0">Claim Information</h2>
                        <table>
                            <tr>
                                <td>
                                    <xforms:group ref="claim:claim-info">
                                        <table>
                                            <tr>
                                                <th>Accident Type</th>
                                                <td colspan="4">
                                                    <xforms:select1 ref="claim:accident-type" appearance="compact" xhtml:style="width: 200px">
                                                        <xforms:hint>Accident Type</xforms:hint>
                                                        <xforms:help>Please select an accident type here</xforms:help>
                                                        <xforms:item>
                                                            <xforms:label>Hand Injury</xforms:label>
                                                            <xforms:value>HAND</xforms:value>
                                                        </xforms:item>
                                                        <xforms:item>
                                                            <xforms:label>Head Injury</xforms:label>
                                                            <xforms:value>HEAD</xforms:value>
                                                        </xforms:item>
                                                        <xforms:item>
                                                            <xforms:label>Foot Injury</xforms:label>
                                                            <xforms:value>FOOT</xforms:value>
                                                        </xforms:item>
                                                        <xforms:item>
                                                            <xforms:label>Other Injury</xforms:label>
                                                            <xforms:value>OTHER</xforms:value>
                                                        </xforms:item>
                                                    </xforms:select1>
                                                </td>
                                            </tr>
                                            <tr>
                                                <th>Accident Date</th>
                                                <td colspan="4">
                                                    <xforms:input ref="claim:accident-date" xhtml:style="width: 200px">
                                                        <xforms:hint>Accident Date</xforms:hint>
                                                        <xforms:help>Please enter the date of the accident here (e.g. 1970-02-25)</xforms:help>
                                                    </xforms:input>
                                                </td>
                                            </tr>
                                            <tr>
                                                <th>Calculated Rate</th>
                                                <td colspan="4">
                                                    <xforms:output ref="claim:rate">
                                                        <xforms:hint>Claim Rate</xforms:hint>
                                                        <xforms:help>Calculated claim rate category</xforms:help>
                                                    </xforms:output>
                                                </td>
                                            </tr>
                                        </table>
                                    </xforms:group>
                                </td>
                            </tr>
                        </table>
                    </xforms:group>

                    <hr/>

                    <table>
                        <tr>
                            <td align="left" valign="bottom">
                                <xforms:submit>
                                    <xforms:label>Save</xforms:label>
                                    <xforms:setvalue ref="/form/action">save</xforms:setvalue>
                                </xforms:submit>
                                <xforms:submit>
                                    <xforms:label>Back</xforms:label>
                                    <xforms:setvalue ref="/form/action">back</xforms:setvalue>
                                </xforms:submit>
                            </td>
                        </tr>
                    </table>

                    <hr/>
                </xforms:group>
            </xforms:group>
        </div>
    </body>
</html>

