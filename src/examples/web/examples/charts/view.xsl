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
 <xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
             xmlns:xforms="http://www.w3.org/2002/xforms"
             xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
             xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
             xsl:version="2.0">
     <xhtml:head>
         <xhtml:title>Charts</xhtml:title>
     </xhtml:head>
     <xhtml:body>
         <xforms:group ref="/form">
             <xhtml:p>
                 <xhtml:table class="gridtable">
                     <xhtml:tr>
                         <xhtml:th>Categories</xhtml:th>
                         <xhtml:td>
                             <xforms:input ref="data/categories/cat1" xhtml:size="8"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/categories/cat2" xhtml:size="8"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/categories/cat3" xhtml:size="8"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/categories/cat4" xhtml:size="8"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/categories/cat5" xhtml:size="8"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Series 1 Title</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:input ref="chart/value[1]/@title" xhtml:size="8"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Series 1 Values</xhtml:th>
                         <xhtml:td>
                             <xforms:input ref="data/values1/val1" xhtml:size="3"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/values1/val2" xhtml:size="3"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/values1/val3" xhtml:size="3"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/values1/val4" xhtml:size="3"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/values1/val5" xhtml:size="3"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Series 2 Title</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:input ref="chart/value[2]/@title" xhtml:size="8"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Series 2 Values</xhtml:th>
                         <xhtml:td>
                             <xforms:input ref="data/values2/val1" xhtml:size="3"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/values2/val2" xhtml:size="3"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/values2/val3" xhtml:size="3"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/values2/val4" xhtml:size="3"/>
                         </xhtml:td>
                         <xhtml:td>
                             <xforms:input ref="data/values2/val5" xhtml:size="3"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Chart Type</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:select1 ref="chart/type" appearance="minimal">
                                 <xforms:choices>
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
                                 </xforms:choices>
                             </xforms:select1>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Title</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:input ref="chart/title"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Title Color</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:input ref="chart/title-color"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Background Color</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:input ref="chart/background-color"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Category Title</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:input ref="chart/category-title"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Category Margin</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:input ref="chart/category-margin"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Serie Title</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:input ref="chart/serie-title"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Tick Unit</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:input ref="chart/tick-unit"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Bar Margin</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:input ref="chart/bar-margin"/>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Category Label Angle</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:input ref="chart/category-label-angle"/>  (Positive Integer)
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Legend</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:select1 ref="chart/legend/@visible" appearance="minimal">
                                 <xforms:choices>
                                     <xforms:item>
                                         <xforms:label>Enabled</xforms:label>
                                         <xforms:value>true</xforms:value>
                                     </xforms:item>
                                     <xforms:item>
                                         <xforms:label>Disabled</xforms:label>
                                         <xforms:value>false</xforms:value>
                                     </xforms:item>
                                 </xforms:choices>
                             </xforms:select1>
                         </xhtml:td>
                     </xhtml:tr>
                     <xhtml:tr>
                         <xhtml:th>Legend Position</xhtml:th>
                         <xhtml:td colspan="5">
                             <xforms:select1 ref="chart/legend/@position" appearance="minimal">
                                 <xforms:choices>
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
                                 </xforms:choices>
                             </xforms:select1>
                         </xhtml:td>
                     </xhtml:tr>
                 </xhtml:table>
                 <br/>
                 <xforms:submit xxforms:appearance="button">
                     <xforms:label>Update</xforms:label>
                 </xforms:submit>
             </xhtml:p>
         </xforms:group>
         <xhtml:table class="gridtable">
             <xhtml:tr>
                 <xhtml:th>
                     Chart Output
                 </xhtml:th>
             </xhtml:tr>
             <xhtml:tr>
                 <xhtml:td>
                     <center>
                         <img src="/chartDisplay?filename={/chart-info/file}" usemap="#fruits" border="0" width="400" height="300"/>
                         <xsl:copy-of select="/chart-info/map"/>
                     </center>
                 </xhtml:td>
             </xhtml:tr>
             <xhtml:tr>
                 <xhtml:th>
                     Chart Input
                 </xhtml:th>
             </xhtml:tr>
             <xhtml:tr>
                 <xhtml:td style="white-space: normal">
                     <f:xml-source>
                         <xsl:copy-of select="document('input:instance')/form/chart"/>
                     </f:xml-source>
                 </xhtml:td>
             </xhtml:tr>
             <xhtml:tr>
                 <xhtml:th>
                     Data Input
                 </xhtml:th>
             </xhtml:tr>
             <xhtml:tr>
                 <xhtml:td>
                     <f:xml-source>
                         <xsl:copy-of select="document('input:instance')/form/data"/>
                     </f:xml-source>
                 </xhtml:td>
             </xhtml:tr>
         </xhtml:table>
     </xhtml:body>
 </xhtml:html>
