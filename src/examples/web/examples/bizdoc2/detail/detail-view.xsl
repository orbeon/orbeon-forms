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
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xi="http://www.w3.org/2001/XInclude"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:claim="http://orbeon.org/oxf/examples/bizdoc/claim"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns="http://www.w3.org/1999/xhtml">

    <head>
        <title>BizDoc Detail</title>
        <xforms:model id="main-model" schema="oxf:/examples/bizdoc/detail/form-schema.xsd">
            <xforms:action ev:event="xforms-submit-done">
                <xforms:setvalue ref="instance('main-instance')/message">Submission successful!</xforms:setvalue>
                <xforms:toggle case="ok-message"/>
            </xforms:action>
            <xforms:action ev:event="xforms-submit-error">
                <xforms:setvalue ref="instance('main-instance')/message">Submission failed! Please correct errors first.</xforms:setvalue>
                <xforms:toggle case="error-message"/>
            </xforms:action>
            <xforms:action ev:event="xforms-insert">
<!--                <xforms:setvalue ref="instance('main-instance')/document/claim:claim/claim:insured-info/claim:family-info/claim:children/claim:child[index('children')]/claim:birth-date"/>-->
<!--                <xforms:setvalue ref="instance('main-instance')/document/claim:claim/claim:insured-info/claim:family-info/claim:children/claim:child[index('children')]/claim:first-name"/>-->
            </xforms:action>
            <!-- The XForms instance with application data -->
            <xforms:instance id="main-instance">
                <form xmlns="">
                    <message>No message</message>
                    <show-errors/>
                    <xsl:copy-of select="doc('input:instance')/*/(document-id | document)"/>
                </form>
            </xforms:instance>
            <xforms:instance id="triggers">
                <triggers xmlns="">
                    <remove-child/>
                </triggers>
            </xforms:instance>
            <xforms:bind nodeset="document/claim:claim">
                
                <!-- Add some required elements -->
                <xforms:bind nodeset="claim:insured-info">
                    <xforms:bind nodeset="claim:general-info">
                        <xforms:bind nodeset="claim:name-info/claim:last-name" required="true()"/>
                        <xforms:bind nodeset="claim:name-info/claim:first-name" required="true()"/>
                        <xforms:bind nodeset="claim:address/claim:address-detail/claim:street-name" required="true()"/>
                        <xforms:bind nodeset="claim:address/claim:city" required="true()"/>
                        <xforms:bind nodeset="claim:address/claim:state-province" required="true()"/>
                        <xforms:bind nodeset="claim:address/claim:country" required="true()"/>
                    </xforms:bind>

                    <xforms:bind nodeset="claim:person-info">
                        <xforms:bind nodeset="claim:birth-date" required="true()" type="xs:date"/>
                        <xforms:bind nodeset="claim:occupation" required="true()"/>
                    </xforms:bind>

                    <xforms:bind nodeset="claim:family-info/claim:children">
                        <!-- Handle the empty repeat entry necessary to add new entries with xforms:repeat -->
                        <xforms:bind nodeset="claim:child[position() lt last()]/claim:birth-date" required="true()"/>
                        <xforms:bind nodeset="claim:child[position() lt last()]/claim:first-name" required="true()"/>
                        <xforms:bind nodeset="claim:child[last()]/claim:birth-date" calculate="current-date()"/>
                        <!-- Set date type -->
                        <xforms:bind nodeset="claim:child/claim:birth-date" type="xs:date"/>
                    </xforms:bind>

                    <!-- Accident date -->
                    <xforms:bind nodeset="claim:claim-info/claim:accident-date" type="xs:date"/>

                    <!-- This bind element handles calculated values -->
                    <xforms:bind nodeset="claim:claim-info/claim:rate"
                                 calculate="if (../claim:insured-info/claim:person-info/claim:birth-date castable as xs:date)
                                            then if (current-date() - xs:date(/form/document/claim:claim/claim:insured-info/claim:person-info/claim:birth-date) > xdt:dayTimeDuration('P365D'))
                                                 then 10
                                                 else 5
                                            else 0"/>
                </xforms:bind>
            </xforms:bind>
            <xforms:bind nodeset="instance('triggers')/remove-child"
                relevant="count(instance('main-instance')/document/claim:claim/claim:insured-info/claim:family-info/claim:children/claim:child) > 1"/>
            <xforms:submission id="main" method="post" action="/bizdoc2/detail"/>
            <xforms:submission id="save" method="post" replace="none" action="/bizdoc2/save"/>
        </xforms:model>
    </head>
    <body>
        <div class="maincontent">
            <xforms:group ref="instance('main-instance')">
                <xi:include href="../view-logo.xml"/>

                <h2 style="margin-top: 0">Document Information</h2>
                <table>
                    <tr>
                        <th>Identifier</th>
                        <td>
                            <xforms:output ref="document-id"/>
                        </td>
                    </tr>
                    <xforms:switch>
                        <xforms:case id="no-message">
                            <tr/>
                        </xforms:case>
                        <xforms:case id="ok-message">
                            <tr>
                                <th align="right">Message</th>
                                <td>
                                    <span style="color: green">
                                        <xforms:output ref="message"/>
                                    </span>
                                </td>
                            </tr>
                        </xforms:case>
                        <xforms:case id="error-message">
                            <tr>
                                <th align="right">Message</th>
                                <td>
                                    <span style="color: red">
                                        <xforms:output ref="message"/>
                                    </span>
                                </td>
                            </tr>
                        </xforms:case>
                    </xforms:switch>
                </table>

                <xforms:group ref="document">

                    <xforms:switch>
                        <xforms:case id="page-1">

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
                                                        <xforms:alert>Incorrect street number</xforms:alert>
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
                                        <xforms:trigger>
                                            <xforms:label>Save</xforms:label>
                                            <xforms:action ev:event="DOMActivate">
                                                <xforms:recalculate model="main-model"/>
                                                <xforms:send submission="save"/>
                                            </xforms:action>
                                        </xforms:trigger>
                                        <xforms:trigger>
                                            <xforms:label>Back</xforms:label>
                                            <xforms:load ev:event="DOMActivate" resource="/bizdoc2"/>
                                        </xforms:trigger>
                                        <xforms:trigger>
                                            <xforms:label>Next</xforms:label>
                                            <xforms:action ev:event="DOMActivate">
                                                <xforms:toggle case="page-2"/>
                                                <xforms:toggle case="no-message"/>
                                            </xforms:action>
                                        </xforms:trigger>
                                    </td>
                                </tr>
                            </table>
                        </xforms:case>
                        <xforms:case id="page-2">

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
<!--                                                    <div style="width: 200px">-->
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
<!--                                                    </div>-->
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
                                                <xforms:select1 ref="claim:marital-status-code" appearance="minimal" xhtml:style="width: 200px">
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
                                        <xforms:repeat nodeset="claim:children/claim:child[position() lt last()]" id="children">
                                            <tr>
                                                <td align="right">
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
                                            </tr>
                                        </xforms:repeat>
                                        <tr>
                                            <td colspan="2">
                                                Currently selected row: <xforms:output value="index('children')"/>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td colspan="2">
                                                <xforms:trigger>
                                                    <xforms:label>Add Child</xforms:label>
                                                    <xforms:action ev:event="DOMActivate" >
                                                        <xforms:insert nodeset="claim:children/claim:child" at="index('children')" position="after"/>
                                                        <xforms:setvalue ref="claim:children/claim:child[index('children')]/claim:birth-date" value="''"/>
                                                        <xforms:setvalue ref="claim:children/claim:child[index('children')]/claim:first-name" value="''"/>
                                                    </xforms:action>
                                                </xforms:trigger>
                                                <xforms:trigger ref="instance('triggers')/remove-child">
                                                    <xforms:label>Remove Child</xforms:label>
                                                    <xforms:delete ev:event="DOMActivate"
                                                        nodeset="instance('main-instance')/document/claim:claim/claim:insured-info/claim:family-info/claim:children/claim:child"
                                                        at="index('children')"/>
                                                </xforms:trigger>
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
                                                            <xforms:select1 ref="claim:accident-type" appearance="minimal" xhtml:style="width: 200px">
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
                                        <xforms:trigger>
                                            <xforms:label>Save</xforms:label>
                                            <xforms:action ev:event="DOMActivate">
                                                <xforms:recalculate model="main-model"/>
                                                <xforms:send submission="save"/>
                                            </xforms:action>
                                        </xforms:trigger>
                                        <xforms:trigger>
                                            <xforms:label>Back</xforms:label>
                                            <xforms:action ev:event="DOMActivate">
                                                <xforms:toggle case="page-1"/>
                                                <xforms:toggle case="no-message"/>
                                            </xforms:action>
                                        </xforms:trigger>
                                    </td>
                                </tr>
                            </table>

                        </xforms:case>
                    </xforms:switch>

                </xforms:group><!-- ref="document" -->
            </xforms:group><!-- ref="instance('main-instance')" -->

        </div>
    </body>
</html>

