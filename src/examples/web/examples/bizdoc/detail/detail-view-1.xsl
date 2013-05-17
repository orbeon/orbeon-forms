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
        <title>Detail - Step 1</title>
    </head>
    <body>
        <div class="maincontent">
            <xforms:group ref="/form" xxforms:show-errors="{doc('input:instance')/form/show-errors = 'true'}">
                <xi:include href="../summary/view-logo.xml"/>
                <xi:include href="detail-view-header.xml"/>
                <xforms:group ref="document">
                    <xforms:group ref="claim:claim">
                        <xforms:group ref="claim:insured-info/claim:general-info">
                            <h2 style="margin-top: 0">Claimant Name</h2>
                            <xforms:group ref="claim:name-info">
                                <table>
                                    <tr>
                                        <th align="right" valign="middle">Title</th>
                                        <td>
                                            <xforms:input ref="claim:title-prefix">
                                                <xforms:hint>Salutation</xforms:hint>
                                                <xforms:help>Please enter an optional prefix (e.g.  Mr. or Ms.)</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="right" valign="middle">Last Name</th>
                                        <td>
                                            <xforms:input ref="claim:last-name">
                                                <xforms:hint>Last Name</xforms:hint>
                                                <xforms:help>Please enter a mandatory last name here</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="right" valign="middle">First Name</th>
                                        <td>
                                            <xforms:input ref="claim:first-name">
                                                <xforms:hint>First Name</xforms:hint>
                                                <xforms:help>Please enter a mandatory first name here</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="right" valign="middle">Suffix</th>
                                        <td>
                                            <xforms:input ref="claim:title-suffix">
                                                <xforms:hint>Title Suffix</xforms:hint>
                                                <xforms:help>Please enter an optional title suffix here</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                </table>
                            </xforms:group>
                            <h2 style="margin-top: 0">Claimant Address</h2>
                            <xforms:group ref="claim:address">
                                <table>
                                    <tr>
                                        <th align="right" valign="middle">Street Name</th>
                                        <td>
                                            <xforms:input ref="claim:address-detail/claim:street-name">
                                                <xforms:hint>Street Name</xforms:hint>
                                                <xforms:help>Please enter a street name here</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="right" valign="middle">Street Number</th>
                                        <td>
                                            <xforms:input ref="claim:address-detail/claim:street-number">
                                                <xforms:hint>Street Number</xforms:hint>
                                                <xforms:help>Please enter a street number here</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="right" valign="middle">Unit Number</th>
                                        <td>
                                            <xforms:input ref="claim:address-detail/claim:unit-number">
                                                <xforms:hint>Unit Number</xforms:hint>
                                                <xforms:help>Please enter a unit number here</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="right" valign="middle">City</th>
                                        <td>
                                            <xforms:input ref="claim:city">
                                                <xforms:hint>City</xforms:hint>
                                                <xforms:help>Please enter a city here</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="right" valign="middle">State</th>
                                        <td>
                                            <xforms:input ref="claim:state-province">
                                                <xforms:hint>State</xforms:hint>
                                                <xforms:help>Please enter a state here</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="right" valign="middle">Zip Code</th>
                                        <td>
                                            <xforms:input ref="claim:postal-code">
                                                <xforms:hint>Zip Code</xforms:hint>
                                                <xforms:help>Please enter a zip code here</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="right" valign="middle">Country</th>
                                        <td>
                                            <xforms:input ref="claim:country">
                                                <xforms:hint>Country</xforms:hint>
                                                <xforms:help>Please enter a country here</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="right" valign="middle">Email</th>
                                        <td>
                                            <xforms:input ref="claim:email">
                                                <xforms:hint>Email</xforms:hint>
                                                <xforms:help>Please enter an email address here</xforms:help>
                                            </xforms:input>
                                        </td>
                                    </tr>
                                </table>
                            </xforms:group>
                        </xforms:group>
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
                                <xforms:submit>
                                    <xforms:label>Next</xforms:label>
                                    <xforms:setvalue ref="/form/action">next</xforms:setvalue>
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

