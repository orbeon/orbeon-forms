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
    
    <p:param type="output" name="data"/>

    <p:processor name="oxf:identity">
         <p:input name="data">
            <list>
                <value> 127 </value>
                <value> 126 </value>
                <value> 130 </value>
                <value> 124 </value>
                <value> 128 </value>
                <value> 131 </value>
                <value> 134 </value>
                <value> 132 </value>
                <value> 133 </value>
                <value> 123 </value>
                <value> 135 </value>
                <value> 136 </value>
                <value> 125 </value>
                <value> 129 </value>
            </list>
        </p:input>
        <p:output name="data" id="source"/>
    </p:processor>

    <p:processor name="oxf:stx">
        <!-- This example comes straight from the Joost distribution -->
        <p:input name="data" href="#source"/>
        <p:input name="config">
            <stx:transform xmlns:stx="http://stx.sourceforge.net/2002/ns" version="1.0"
                pass-through="all" strip-space="yes">

                <!-- global variables -->
                <stx:variable name="value"/>
                <stx:variable name="changed" select="true()"/>

                <!-- the sort buffer -->
                <stx:buffer name="sorted"/>

                <!-- store first value -->
                <stx:template match="value[1]">
                    <stx:assign name="value" select="."/>
                </stx:template>

                <!-- compare current value with the stored one -->
                <stx:template match="value">
                    <stx:copy>
                        <stx:if test=". &lt; $value">
                            <stx:value-of select="."/>
                            <stx:assign name="changed" select="true()"/>
                        </stx:if>
                        <stx:else>
                            <stx:value-of select="$value"/>
                            <stx:assign name="value" select="."/>
                        </stx:else>
                    </stx:copy>
                </stx:template>

                <stx:template match="list[$changed]">
                    <stx:assign name="changed" select="false()"/> <!-- reset -->
                    <stx:result-buffer name="sorted" clear="yes">
                        <stx:copy>
                            <stx:process-children/>
                            <!-- output last stored value -->
                            <value>
                                <stx:value-of select="$value"/>
                            </value>
                        </stx:copy>
                    </stx:result-buffer>
                    <stx:process-buffer name="sorted"/>
                </stx:template>

                <!-- a group for outputting the sorted result -->
                <stx:group>
                    <!-- instantiated if $changed is false() -->
                    <stx:template match="list" public="yes">
                        <stx:copy>
                            <stx:process-children/> <!-- copy children per default -->
                        </stx:copy>
                    </stx:template>
                </stx:group>

            </stx:transform>
        </p:input>
        <p:output name="data" id="result"/>
    </p:processor>

    <p:processor name="oxf:identity">
         <p:input name="data" href="aggregate('root', #source, #result)"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
