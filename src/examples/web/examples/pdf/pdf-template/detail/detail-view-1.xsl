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
    xmlns:xi="http://www.w3.org/2003/XInclude"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:claim="http://orbeon.org/oxf/examples/bizdoc/claim"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns="http://www.w3.org/1999/xhtml">

    <head>
        <title>Detail - Step 1</title>
    </head>
    <body>
        <div id="maincontent">
            <xforms:group ref="/form" xxforms:show-errors="{/form/show-errors = 'true'}">
                <xi:include href="../summary/view-logo.xml"/>
                <xi:include href="detail-view-header.xml"/>
                <xforms:group ref="document">
                    <xforms:group ref="claim:form">
                        <h2 style="margin-top: 0">Personal Information</h2>
                        <table>
                            <tr>
                                <th align="right" valign="middle">Last Name</th>
                                <td>
                                    <xforms:input ref="claim:name/claim:last-name">
                                        <xforms:hint>Last Name</xforms:hint>
                                        <xforms:help>Please enter a mandatory last name here</xforms:help>
                                    </xforms:input>
                                </td>
                                <th align="right" valign="middle">Driver License / Id Card No.</th>
                                <td>
                                    <xforms:input ref="claim:driver-license-no" xhtml:size="2">
                                        <xforms:hint>Driver License / Id Card No.</xforms:hint>
                                        <xforms:help>Please enter a mandatory licence number of id card number here</xforms:help>
                                    </xforms:input>
                                </td>
                            </tr>
                            <tr>
                                <th align="right" valign="middle">First</th>
                                <td>
                                    <xforms:input ref="claim:name/claim:first-name">
                                        <xforms:hint>First Name</xforms:hint>
                                        <xforms:help>Please enter a mandatory first name here</xforms:help>
                                    </xforms:input>
                                </td>
                                <th align="right" valign="middle">Initial</th>
                                <td>
                                    <xforms:input ref="claim:name/claim:initial" xhtml:size="2">
                                        <xforms:hint>Initial</xforms:hint>
                                        <xforms:help>Please enter an optional initial here</xforms:help>
                                    </xforms:input>
                                </td>
                            </tr>
                            <tr>
                                <td colspan="4" align="right">
                                    <xforms:group ref="claim:birth-date">
                                        <table border="0" cellspacing="0" cellpadding="0">
                                            <tr>
                                                <th align="right" valign="middle">Birth Date (MM-DD-YYYY)</th>
                                                <td style="padding-left: 5px">
                                                    <xforms:input ref="claim:month" xhtml:size="1">
                                                        <xforms:hint>Month</xforms:hint>
                                                    </xforms:input>
                                                </td>
                                                <td style="padding-left: 5px">
                                                    <xforms:input ref="claim:day" xhtml:size="1">
                                                        <xforms:hint>Day</xforms:hint>
                                                    </xforms:input>
                                                </td>
                                                <td style="padding-left: 5px">
                                                    <xforms:input ref="claim:year" xhtml:size="2">
                                                        <xforms:hint>Year</xforms:hint>
                                                        <xforms:help>Please enter a mandatory date here</xforms:help>
                                                    </xforms:input>
                                                </td>
                                            </tr>
                                        </table>
                                    </xforms:group>
                                </td>
                            </tr>
                        </table>
                        <h2 style="margin-top: 0">New or Correct Residence Address</h2>
                        <xforms:group ref="claim:residence-address">
                            <table>
                                <tr>
                                    <th align="right" valign="middle">Street Number</th>
                                    <td>
                                        <xforms:input ref="claim:street/claim:number">
                                            <xforms:hint>Street Number</xforms:hint>
                                            <xforms:help>Please enter a street number here</xforms:help>
                                        </xforms:input>
                                    </td>
                                    <th align="right" valign="middle">Street Name</th>
                                    <td>
                                        <xforms:input ref="claim:street/claim:name-1">
                                            <xforms:hint>Street Name</xforms:hint>
                                            <xforms:help>Please enter a street name here</xforms:help>
                                        </xforms:input>
                                    </td>
                                </tr>
                                <tr>
                                    <th align="right" valign="middle">Apt No.</th>
                                    <td>
                                        <xforms:input ref="claim:apt">
                                            <xforms:hint>Street Number</xforms:hint>
                                            <xforms:help>Please enter a street number here</xforms:help>
                                        </xforms:input>
                                    </td>
                                    <th align="right" valign="middle">Street Name</th>
                                    <td>
                                        <xforms:input ref="claim:street/claim:name-2">
                                            <xforms:hint>Street Name</xforms:hint>
                                            <xforms:help>Please enter a street name here</xforms:help>
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
                                    <th align="right" valign="middle">State and Zip Code</th>
                                    <td>
                                        <table border="0" cellspacing="0" cellpadding="0">
                                            <tr>
                                                <td>
                                                    <xforms:input ref="claim:state" xhtml:size="1">
                                                        <xforms:hint>State</xforms:hint>
                                                    </xforms:input>
                                                </td>
                                                <td style="padding-left: 5px">
                                                    <xforms:input ref="claim:zip" xhtml:size="3">
                                                        <xforms:hint>Zip</xforms:hint>
                                                        <xforms:help>Please enter a state and zip code here</xforms:help>
                                                    </xforms:input>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </xforms:group>
                         <h2 style="margin-top: 0">New or Correct Mailing Address</h2>
                         <xforms:group ref="claim:mailing-address">
                            <table>
                                <tr>
                                    <th align="right" valign="middle">Street Number</th>
                                    <td>
                                        <xforms:input ref="claim:street/claim:number">
                                            <xforms:hint>Street Number</xforms:hint>
                                            <xforms:help>Please enter a street number here</xforms:help>
                                        </xforms:input>
                                    </td>
                                    <th align="right" valign="middle">Street Name</th>
                                    <td>
                                        <xforms:input ref="claim:street/claim:name-1">
                                            <xforms:hint>Street Name</xforms:hint>
                                            <xforms:help>Please enter a street name here</xforms:help>
                                        </xforms:input>
                                    </td>
                                </tr>
                                <tr>
                                    <th align="right" valign="middle">Apt No.</th>
                                    <td>
                                        <xforms:input ref="claim:apt">
                                            <xforms:hint>Street Number</xforms:hint>
                                            <xforms:help>Please enter a street number here</xforms:help>
                                        </xforms:input>
                                    </td>
                                    <th align="right" valign="middle">Street Name</th>
                                    <td>
                                        <xforms:input ref="claim:street/claim:name-2">
                                            <xforms:hint>Street Name</xforms:hint>
                                            <xforms:help>Please enter a street name here</xforms:help>
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
                                    <th align="right" valign="middle">State and Zip Code</th>
                                    <td>
                                        <table border="0" cellspacing="0" cellpadding="0">
                                            <tr>
                                                <td>
                                                    <xforms:input ref="claim:state" xhtml:size="1">
                                                        <xforms:hint>State</xforms:hint>
                                                    </xforms:input>
                                                </td>
                                                <td style="padding-left: 5px">
                                                    <xforms:input ref="claim:zip" xhtml:size="3">
                                                        <xforms:hint>Zip</xforms:hint>
                                                        <xforms:help>Please enter a state and zip code here</xforms:help>
                                                    </xforms:input>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
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

