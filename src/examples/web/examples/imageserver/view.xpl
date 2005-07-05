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

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <!-- We use this private processor to generate a URL from an instance -->
    <p:processor name="oxf:instance-to-parameters">
        <p:input name="instance" href="#instance"/>
        <p:input name="filter"><dummy/></p:input>
        <p:output name="data" id="parameters"/>
    </p:processor>

    <p:processor name="oxf:xalan">
        <p:input name="data" href="#parameters"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0"
                            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:net="http://orbeon.com/oxf/xslt/net">

                <xsl:import href="oxf:/oxf/xslt/utils/utils.xsl"/>

                <xsl:template match="/">
                    <xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
                        xmlns:xforms="http://www.w3.org/2002/xforms"
                        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

                        <xhtml:head>
                            <xhtml:title>Image Transformations</xhtml:title>
                        </xhtml:head>
                        <xhtml:body>
                            <xforms:group ref="/form">
                                <xhtml:p>
                                    <xhtml:table class="gridtable">
                                        <xhtml:tr>
                                            <xhtml:th colspan="2">Global Settings</xhtml:th>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Quality</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/quality"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th colspan="2">
                                                <xforms:select ref="image/scale/enable" appearance="full">
                                                    <xforms:choices>
                                                        <xforms:item>
                                                            <xforms:label>Scale</xforms:label>
                                                            <xforms:value>true</xforms:value>
                                                        </xforms:item>
                                                    </xforms:choices>
                                                </xforms:select>
                                            </xhtml:th>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Quality</xhtml:th>
                                            <xhtml:td>
                                                <xforms:select1 ref="image/scale/quality" appearance="minimal">
                                                    <xforms:choices>
                                                        <xforms:item>
                                                            <xforms:label>High</xforms:label>
                                                            <xforms:value>high</xforms:value>
                                                        </xforms:item>
                                                        <xforms:item>
                                                            <xforms:label>Low</xforms:label>
                                                            <xforms:value>low</xforms:value>
                                                        </xforms:item>
                                                    </xforms:choices>
                                                </xforms:select1>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Scale Up</xhtml:th>
                                            <xhtml:td>
                                                <xforms:select1 ref="image/scale/scale-up" appearance="minimal">
                                                    <xforms:choices>
                                                        <xforms:item>
                                                            <xforms:label>True</xforms:label>
                                                            <xforms:value>true</xforms:value>
                                                        </xforms:item>
                                                        <xforms:item>
                                                            <xforms:label>False</xforms:label>
                                                            <xforms:value>false</xforms:value>
                                                        </xforms:item>
                                                    </xforms:choices>
                                                </xforms:select1>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Width</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/scale/width"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Height</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/scale/height"/>
                                            </xhtml:td>
                                        </xhtml:tr>


                                        <xhtml:tr>
                                            <xhtml:th colspan="2">
                                                <xforms:select ref="image/crop/enable" appearance="full">
                                                    <xforms:choices>
                                                        <xforms:item>
                                                            <xforms:label>Crop</xforms:label>
                                                            <xforms:value>true</xforms:value>
                                                        </xforms:item>
                                                    </xforms:choices>
                                                </xforms:select>
                                            </xhtml:th>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>X</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/crop/x"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Y</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/crop/y"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Width</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/crop/width"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Height</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/crop/height"/>
                                            </xhtml:td>
                                        </xhtml:tr>

                                        <xhtml:tr>
                                            <xhtml:th colspan="2">
                                                <xforms:select ref="image/rect/enable" appearance="full">
                                                    <xforms:choices>
                                                        <xforms:item>
                                                            <xforms:label>Empty Rectangle</xforms:label>
                                                            <xforms:value>true</xforms:value>
                                                        </xforms:item>
                                                    </xforms:choices>
                                                </xforms:select>
                                            </xhtml:th>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>X</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/rect/@x"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Y</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/rect/@y"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Width</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/rect/@width"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Height</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/rect/@height"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Color/Alpha</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/rect/color/@rgb"/>
                                                <xforms:input ref="image/rect/color/@alpha" />
                                            </xhtml:td>
                                        </xhtml:tr>

                                        <xhtml:tr>
                                            <xhtml:th colspan="2">
                                                <xforms:select ref="image/fill/enable" appearance="full">
                                                    <xforms:choices>
                                                        <xforms:item>
                                                            <xforms:label>Filled Rectangle</xforms:label>
                                                            <xforms:value>true</xforms:value>
                                                        </xforms:item>
                                                    </xforms:choices>
                                                </xforms:select>
                                            </xhtml:th>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>X</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/fill/@x"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Y</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/fill/@y"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Width</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/fill/@width"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Height</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/fill/@height"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Color/Alpha</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/fill/color/@rgb"/>
                                                <xforms:input ref="image/fill/color/@alpha" />
                                            </xhtml:td>
                                        </xhtml:tr>


                                        <xhtml:tr>
                                            <xhtml:th colspan="2">
                                                <xforms:select ref="image/line/enable" appearance="full">
                                                    <xforms:choices>
                                                        <xforms:item>
                                                            <xforms:label>Line</xforms:label>
                                                            <xforms:value>true</xforms:value>
                                                        </xforms:item>
                                                    </xforms:choices>
                                                </xforms:select>
                                            </xhtml:th>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>X1</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/line/@x1"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Y1</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/line/@y1"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Width</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/line/@x2"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Height</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/line/@y2"/>
                                            </xhtml:td>
                                        </xhtml:tr>
                                        <xhtml:tr>
                                            <xhtml:th>Color/Alpha</xhtml:th>
                                            <xhtml:td>
                                                <xforms:input ref="image/line/color/@rgb"/>
                                                <xforms:input ref="image/line/color/@alpha" />
                                            </xhtml:td>
                                        </xhtml:tr>
                                    </xhtml:table>
                                </xhtml:p>
                                <br/>
                                <xforms:submit xxforms:appearance="button">
                                    <xforms:label>Update</xforms:label>
                                </xforms:submit>
                            </xforms:group>

                            <br/>
                            <center><img src="/direct/imageserver/image{net:url(/*)}"/></center>
                            <br/>
                        </xhtml:body>
                    </xhtml:html>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
