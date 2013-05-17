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
<xhtml:html xsl:version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:order="urn:oasis:names:tc:ubl:Order:1.0:0.70"
    xmlns:cat="urn:oasis:names:tc:ubl:CommonAggregateTypes:1.0:0.70">

    <xsl:variable name="instance" as="element()" select="/root/form"/>
    <xsl:variable name="errors" as="element()" select="/root/errors"/>
    
    
    <xhtml:head><xhtml:title>XForms UBL Order</xhtml:title></xhtml:head>
    <xhtml:body>
        <xforms:group ref="/form">
            <xsl:choose>
                <xsl:when test="$instance/view != 'instance'">
                    <!-- Global errors -->
                    <xsl:for-each select="$errors/error">
                        <xxforms:hidden ref="/form/global-validation">
                            <xforms:alert><xsl:value-of select="."/></xforms:alert>
                        </xxforms:hidden>
                    </xsl:for-each>
                    <table>
                        <tr><td colspan="2"><h2 style="margin-bottom: 0">Order Form</h2></td></tr>
                        <tr>
                            <td align="center" width="50%">
                                <table style="margin-top: 0;">
                                    <tr>
                                        <th align="right">Order Date</th>
                                        <td><xforms:input ref="order:Order/cat:IssueDate"/></td>
                                    </tr>
                                    <tr>
                                        <th align="right">Currency</th>
                                        <td>
                                            <xforms:select1 ref="order:Order/cat:LineExtensionTotalAmount/@currencyID" appearance="minimal">
                                                <xforms:choices>
                                                    <xforms:item>
                                                        <xforms:label>Euro</xforms:label>
                                                        <xforms:value>EUR</xforms:value>
                                                    </xforms:item>
                                                    <xforms:item>
                                                        <xforms:label>Pound</xforms:label>
                                                        <xforms:value>GBP</xforms:value>
                                                    </xforms:item>
                                                    <xforms:item>
                                                        <xforms:label>Dollar</xforms:label>
                                                        <xforms:value>USD</xforms:value>
                                                    </xforms:item>
                                                </xforms:choices>
                                            </xforms:select1>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="right">Order total</th>
                                        <td>
                                            <xforms:output ref="order:Order/cat:LineExtensionTotalAmount"/>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                            <td align="center" width="50%"/>
                        </tr>
                        <tr>
                            <xsl:if test="$instance/show-details = 'true'">
                                <td valign="top" width="50%">
                                    <h2 style="margin-top: 0; margin-bottom: 0">Seller Information</h2>
                                    <table>
                                        <tr>
                                            <th align="right">Name</th>
                                            <td><xforms:input ref="order:Order/cat:SellerParty/cat:PartyName/cat:Name"/></td>
                                        </tr>
                                        <tr>
                                            <th align="right">Street</th>
                                            <td><xforms:input ref="order:Order/cat:SellerParty/cat:Address/cat:Street"/></td>
                                        </tr>
                                        <tr>
                                            <th align="right">City</th>
                                            <td><xforms:input ref="order:Order/cat:SellerParty/cat:Address/cat:CityName"/></td>
                                        </tr>
                                        <tr>
                                            <th align="right">Postal Code</th>
                                            <td><xforms:input ref="order:Order/cat:SellerParty/cat:Address/cat:PostalZone"/></td>
                                        </tr>
                                        <tr>
                                            <th align="right">State or Providence</th>
                                            <td><xforms:input ref="order:Order/cat:SellerParty/cat:Address/cat:CountrySub-Entity"/></td>
                                        </tr>
                                    </table>
                                </td>
                                <td valign="top" width="50%">
                                    <h2 style="margin-top: 0">Buyer Information</h2>
                                    <table>
                                        <tr>
                                            <th align="right">Name</th>
                                            <td><xforms:input ref="order:Order/cat:BuyerParty/cat:PartyName/cat:Name"/></td>
                                        </tr>
                                        <tr>
                                            <th align="right">Street</th>
                                            <td><xforms:input ref="order:Order/cat:BuyerParty/cat:Address/cat:Street"/></td>
                                        </tr>
                                        <tr>
                                            <th align="right">City</th>
                                            <td><xforms:input ref="order:Order/cat:BuyerParty/cat:Address/cat:CityName"/></td>
                                        </tr>
                                        <tr>
                                            <th align="right">Postal Code</th>
                                            <td><xforms:input ref="order:Order/cat:BuyerParty/cat:Address/cat:PostalZone"/></td>
                                        </tr>
                                        <tr>
                                            <th align="right">State or Providence</th>
                                            <td><xforms:input ref="order:Order/cat:BuyerParty/cat:Address/cat:CountrySub-Entity"/></td>
                                        </tr>
                                    </table>
                                </td>
                            </xsl:if>
                        </tr>
                        <tr>
                            <td colspan="2" align="right" valign="bottom">
                                <xforms:submit xxforms:appearance="button">
                                    <xforms:label><xsl:value-of select="if ($instance/show-details = 'true') then 'Hide Details' else 'Show Details'"/></xforms:label>
                                    <xforms:setvalue ref="show-details">
                                        <xsl:value-of select="if ($instance/show-details = 'true') then 'false' else 'true'"/>
                                    </xforms:setvalue>
                                </xforms:submit>
                            </td>
                        </tr>
                        <tr><td colspan="2"><h2 style="margin-top: 1em">Order Lines</h2></td></tr>
                        <!-- Order lines table -->
                        <tr><td align="center" colspan="2">
                            <xhtml:table class="gridtable">
                                <xhtml:tr>
                                    <xhtml:th>Quantity</xhtml:th>
                                    <xhtml:th>Description</xhtml:th>
                                    <xhtml:th>Part Number</xhtml:th>
                                    <xhtml:th>Unit Price</xhtml:th>
                                    <xhtml:th>Line Price</xhtml:th>
                                    <xhtml:th>Remove</xhtml:th>
                                </xhtml:tr>
                                <xforms:repeat nodeset="order:Order/cat:OrderLine" id="lineSet">
                                    <xhtml:tr>
                                        <xhtml:td>
                                            <xforms:input ref="cat:Quantity" xhtml:class="tinyinput">
                                                <xforms:alert>Positive number expected</xforms:alert>
                                            </xforms:input>
                                        </xhtml:td>
                                        <xhtml:td><xforms:input ref="cat:Item/cat:Description"/></xhtml:td>
                                        <xhtml:td><xforms:input ref="cat:Item/cat:SellersItemIdentification/cat:ID" xhtml:class="smallinput"/></xhtml:td>
                                        <xhtml:td>
                                            <xforms:input ref="cat:Item/cat:BasePrice/cat:PriceAmount" xhtml:class="tinyinput">
                                                <xforms:alert>Not on 5 cents boundary</xforms:alert>
                                            </xforms:input>
                                        </xhtml:td>
                                        <xhtml:td>
                                            <xforms:output ref="cat:LineExtensionAmount"/>
                                        </xhtml:td>
                                        <xhtml:td align="center">
                                            <xforms:submit xxforms:appearance="image">
                                                <xxforms:img src="/images/remove.png"/>
                                                <xforms:label/>
                                                <xforms:delete nodeset="/form/order:Order/cat:OrderLine" at="index('lineSet')"/>
                                            </xforms:submit>
                                        </xhtml:td>
                                    </xhtml:tr>
                                </xforms:repeat>
                            </xhtml:table>
                        </td></tr>
                        <tr><td align="right" colspan="2">
                            <br/>
                            <xforms:submit xxforms:appearance="button">
                                <xforms:label>Insert new line</xforms:label>
                                <xforms:insert nodeset="/form/order:Order/cat:OrderLine" at="last()" position="after"/>
                            </xforms:submit>
                            <xforms:submit xxforms:appearance="button">
                                <xforms:label>Update total</xforms:label>
                            </xforms:submit>
                            <xforms:submit xxforms:appearance="button">
                                <xforms:label>View XForms Instance</xforms:label>
                                <xforms:setvalue ref="view">instance</xforms:setvalue>
                            </xforms:submit>
                        </td></tr>
                    </table>
                </xsl:when>
                <xsl:otherwise>
                    <xhtml:p>
                        <input type="submit" value="Back to Form" onclick="window.history.back(); return false;"/>
                    </xhtml:p>
                    <f:xml-source>
                        <xsl:copy-of select="$instance"/>
                    </f:xml-source>
                </xsl:otherwise>
            </xsl:choose>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
