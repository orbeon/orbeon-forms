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
             xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
             xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns="http://www.w3.org/1999/xhtml"
             xsl:version="2.0">
     <head>
         <title>Charts</title>
     </head>
     <body>
         <xforms:group ref="/form">
             <p>
                 <table class="gridtable">
                     <tr>
                         <th>Categories</th>
                         <td>
                             <xforms:input ref="data/categories/cat1" size="8"/>
                         </td>
                         <td>
                             <xforms:input ref="data/categories/cat2" size="8"/>
                         </td>
                         <td>
                             <xforms:input ref="data/categories/cat3" size="8"/>
                         </td>
                         <td>
                             <xforms:input ref="data/categories/cat4" size="8"/>
                         </td>
                         <td>
                             <xforms:input ref="data/categories/cat5" size="8"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Series 1 Title</th>
                         <td colspan="5">
                             <xforms:input ref="chart/value[1]/@title" size="8"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Series 1 Values</th>
                         <td>
                             <xforms:input ref="data/values1/val1" size="3"/>
                         </td>
                         <td>
                             <xforms:input ref="data/values1/val2" size="3"/>
                         </td>
                         <td>
                             <xforms:input ref="data/values1/val3" size="3"/>
                         </td>
                         <td>
                             <xforms:input ref="data/values1/val4" size="3"/>
                         </td>
                         <td>
                             <xforms:input ref="data/values1/val5" size="3"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Series 2 Title</th>
                         <td colspan="5">
                             <xforms:input ref="chart/value[2]/@title" size="8"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Series 2 Values</th>
                         <td>
                             <xforms:input ref="data/values2/val1" size="3"/>
                         </td>
                         <td>
                             <xforms:input ref="data/values2/val2" size="3"/>
                         </td>
                         <td>
                             <xforms:input ref="data/values2/val3" size="3"/>
                         </td>
                         <td>
                             <xforms:input ref="data/values2/val4" size="3"/>
                         </td>
                         <td>
                             <xforms:input ref="data/values2/val5" size="3"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Chart Type</th>
                         <td colspan="5">
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
                         </td>
                     </tr>
                     <tr>
                         <th>Title</th>
                         <td colspan="5">
                             <xforms:input ref="chart/title"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Title Color</th>
                         <td colspan="5">
                             <xforms:input ref="chart/title-color"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Background Color</th>
                         <td colspan="5">
                             <xforms:input ref="chart/background-color"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Category Title</th>
                         <td colspan="5">
                             <xforms:input ref="chart/category-title"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Category Margin</th>
                         <td colspan="5">
                             <xforms:input ref="chart/category-margin"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Serie Title</th>
                         <td colspan="5">
                             <xforms:input ref="chart/serie-title"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Tick Unit</th>
                         <td colspan="5">
                             <xforms:input ref="chart/tick-unit"/>
                         </td>
                     </tr>
                     <tr>
                         <th>Bar Margin</th>
                         <td colspan="5">
                             <xforms:input ref="chart/bar-margin"/>
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
                         </td>
                     </tr>
                     <tr>
                         <th>Legend Position</th>
                         <td colspan="5">
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
                         </td>
                     </tr>
                 </table>
                 <br/>
                 <xforms:submit xxforms:appearance="button">
                     <xforms:label>Update</xforms:label>
                 </xforms:submit>
             </p>
         </xforms:group>
         <table class="gridtable">
             <tr>
                 <th>
                     Chart Output
                 </th>
             </tr>
             <tr>
                 <td>
                     <center>
                         <img src="/chartDisplay?filename={/chart-info/file}" usemap="#fruits" border="0" width="400" height="300"/>
                         <xsl:copy-of select="/chart-info/map"/>
                     </center>
                 </td>
             </tr>
             <tr>
                 <th>
                     Chart Input
                 </th>
             </tr>
             <tr>
                 <td style="white-space: normal">
                     <f:xml-source>
                         <xsl:copy-of select="doc('input:instance')/form/chart"/>
                     </f:xml-source>
                 </td>
             </tr>
             <tr>
                 <th>
                     Data Input
                 </th>
             </tr>
             <tr>
                 <td>
                     <f:xml-source>
                         <xsl:copy-of select="doc('input:instance')/form/data"/>
                     </f:xml-source>
                 </td>
             </tr>
         </table>
     </body>
 </html>
