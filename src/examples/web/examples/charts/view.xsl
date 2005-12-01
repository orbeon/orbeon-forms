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
 <html xmlns:f="http://orbeon.org/oxf/xml/formatting"
       xmlns:xhtml="http://www.w3.org/1999/xhtml"
       xmlns:xforms="http://www.w3.org/2002/xforms"
       xmlns:ev="http://www.w3.org/2001/xml-events"
       xmlns:xs="http://www.w3.org/2001/XMLSchema"
       xmlns:widget="http://orbeon.org/oxf/xml/widget"
       xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
       xmlns="http://www.w3.org/1999/xhtml"
       xsl:version="2.0">
     <head>
         <title>Charts</title>
         <xforms:model xmlns:xforms="http://www.w3.org/2002/xforms">
             <!-- Display error message in case of submission error -->
            <xforms:setvalue ev:event="xforms-submit-error" ref="instance('status-instance')/message">Submission Error</xforms:setvalue>
            <!-- Clear error message in case of submission success -->
            <xforms:setvalue ev:event="xforms-submit-done" ref="instance('status-instance')/message"/>

            <xforms:instance id="main">
                <xsl:copy-of select="doc('input:instance')"/>
            </xforms:instance>
             <xforms:instance id="status-instance">
                <status xmlns="">
                    <message/>
                </status>
            </xforms:instance>
            <xforms:bind nodeset="instance('main')/chart/category-label-angle" type="xs:nonNegativeInteger"/>
            <xforms:submission id="main-submission" ref="/form" method="post" action="/direct/charts" replace="all"/>
        </xforms:model>
     </head>
     <body>
         <p style="color: red">
            <xforms:output ref="instance('status-instance')/message"/>
        </p>
         <xforms:group ref="/form">
             <widget:tabs>
                <widget:tab id="configuration">
                    <widget:label>Configure Chart</widget:label>
                    <table class="gridtable">
                        <tr>
                            <th>Categories and Values</th>
                            <td>
                                <table class="gridtable">
                                    <tr>
                                        <th style="white-space: nowrap">Label</th>
                                        <th style="white-space: nowrap">Value 1</th>
                                        <th style="white-space: nowrap">Value 2</th>
                                    </tr>
                                    <xforms:repeat nodeset="data/entries/entry" id="categoryRepeat">
                                        <tr>
                                            <td style="white-space: nowrap">
                                                <xforms:input ref="category"/>
                                            </td>
                                            <td style="white-space: nowrap">
                                                <xforms:input ref="value1"/>
                                            </td>
                                            <td style="white-space: nowrap">
                                                <xforms:input ref="value2"/>
                                            </td>
                                        </tr>
                                        <tr/>
                                    </xforms:repeat>
                                </table>
                                <xforms:trigger>
                                    <xforms:label>Add</xforms:label>
                                    <xforms:action ev:event="DOMActivate">
                                        <xforms:insert nodeset="data/entries/entry" at="index('categoryRepeat')" position="after"/>
                                        <xforms:setvalue ref="data/entries/entry[index('categoryRepeat')]/category" value="''"/>
                                        <xforms:setvalue ref="data/entries/entry[index('categoryRepeat')]/value1" value="''"/>
                                        <xforms:setvalue ref="data/entries/entry[index('categoryRepeat')]/value2" value="''"/>
                                    </xforms:action>
                                </xforms:trigger>
                                <xforms:trigger>
                                    <xforms:label>Remove</xforms:label>
                                    <xforms:action ev:event="DOMActivate">
                                        <xforms:delete nodeset="data/entries/entry" at="index('categoryRepeat')"/>
                                    </xforms:action>
                                </xforms:trigger>
                             </td>
                         </tr>
                         <tr>
                             <th>Titles</th>
                             <td colspan="5">
                                 <xforms:input ref="chart/title">
                                     <xforms:label>Chart: </xforms:label>
                                 </xforms:input>
                                 <xforms:input ref="chart/category-title">
                                     <xforms:label>Category: </xforms:label>
                                 </xforms:input>
                                 <br/>
                                 <xforms:input ref="chart/value[1]/@title" size="8">
                                     <xforms:label>Series 1: </xforms:label>
                                 </xforms:input>
                                 <xforms:input ref="chart/value[2]/@title" size="8">
                                     <xforms:label>Series 2: </xforms:label>
                                 </xforms:input>
                                 <br/>
                                 <xforms:input ref="chart/serie-title">
                                     <xforms:label>Serie: </xforms:label>
                                 </xforms:input>
                             </td>
                         </tr>
                         <tr>
                             <th>Chart Type</th>
                             <td colspan="5">
                                 <xforms:select1 ref="chart/type" appearance="minimal">
                                     <xforms:item>
                                         <xforms:label>Vertical Bar</xforms:label>
                                         <xforms:value>vertical-bar</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Horizontal Bar</xforms:label>
                                         <xforms:value>horizontal-bar</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Vertical Bar 3D</xforms:label>
                                         <xforms:value>vertical-bar-3d</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Horizontal Bar 3D</xforms:label>
                                         <xforms:value>horizontal-bar-3d</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Stacked Vertical Bar</xforms:label>
                                         <xforms:value>stacked-vertical-bar</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Stacked Horizontal Bar</xforms:label>
                                         <xforms:value>stacked-horizontal-bar</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Stacked Vertical Bar 3D</xforms:label>
                                         <xforms:value>stacked-vertical-bar-3d</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Stacked Horizontal Bar 3D</xforms:label>
                                         <xforms:value>stacked-horizontal-bar-3d</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Lines</xforms:label>
                                         <xforms:value>line</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Area</xforms:label>
                                         <xforms:value>area</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Pie</xforms:label>
                                         <xforms:value>pie</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Pie 3D</xforms:label>
                                         <xforms:value>pie-3d</xforms:value>
                                     </xforms:item>
                                 </xforms:select1>
                             </td>
                         </tr>
                         <tr>
                             <th>Colors</th>
                             <td colspan="5">
                                 <xforms:input ref="chart/title-color">
                                     <xforms:label>Title: </xforms:label>
                                 </xforms:input>
                                 <xforms:input ref="chart/background-color">
                                     <xforms:label>Background: </xforms:label>
                                 </xforms:input>
                             </td>
                         </tr>
                         <tr>
                             <th>Margins</th>
                             <td colspan="5">
                                 <xforms:input ref="chart/category-margin">
                                     <xforms:label>Category: </xforms:label>
                                 </xforms:input>
                                 <xforms:input ref="chart/bar-margin">
                                     <xforms:label>Bar: </xforms:label>
                                 </xforms:input>
                             </td>
                         </tr>
                         <tr>
                             <th>Tick Unit</th>
                             <td colspan="5">
                                 <xforms:input ref="chart/tick-unit"/>
                             </td>
                         </tr>
                         <tr>
                             <th>Category Label Angle</th>
                             <td colspan="5">
                                 <xforms:input ref="chart/category-label-angle"/>  (Positive Integer)
                             </td>
                         </tr>
                         <tr>
                             <th>Legend</th>
                             <td colspan="5">
                                 <xforms:select1 ref="chart/legend/@visible" appearance="minimal">
                                     <xforms:label>Enabled: </xforms:label>
                                     <xforms:item>
                                         <xforms:label>Yes</xforms:label>
                                         <xforms:value>true</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>No</xforms:label>
                                         <xforms:value>false</xforms:value>
                                     </xforms:item>
                                 </xforms:select1>
                                 <xforms:select1 ref="chart/legend/@position" appearance="minimal">
                                     <xforms:label>Position: </xforms:label>
                                     <xforms:item>
                                         <xforms:label>North</xforms:label>
                                         <xforms:value>north</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>East</xforms:label>
                                         <xforms:value>east</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>South</xforms:label>
                                         <xforms:value>south</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>West</xforms:label>
                                         <xforms:value>west</xforms:value>
                                     </xforms:item>
                                 </xforms:select1>
                             </td>
                         </tr>
                     </table>
                     <br/>
                     <xforms:submit submission="main-submission">
                         <xforms:label>Update Chart</xforms:label>
                     </xforms:submit>
                </widget:tab>
                <widget:tab id="result" selected="true">
                    <widget:label>View Chart</widget:label>
                    <center>
                        <img src="/chartDisplay?filename={/chart-info/file}" usemap="#fruits" border="0" width="500" height="375"/>
                    </center>
                    <xsl:copy-of select="/chart-info/map"/>
                </widget:tab>
                <widget:tab id="chart-input">
                    <widget:label>XML Configuration</widget:label>
                    <f:xml-source>
                         <xsl:copy-of select="doc('input:instance')/form/chart"/>
                     </f:xml-source>
                </widget:tab>
                <widget:tab id="data-input">
                    <widget:label>XML Data</widget:label>
                    <f:xml-source>
                         <xsl:copy-of select="doc('input:instance')/form/data"/>
                     </f:xml-source>
                </widget:tab>
            </widget:tabs>
         </xforms:group>
     </body>
 </html>
