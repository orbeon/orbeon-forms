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

    <p:processor name="oxf:xslt">
        <p:input name="config">
            <results xsl:version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:variable name="sun" select="'#594fbf'"/>
                <xsl:variable name="ibm" select="'#006699'"/>
                <xsl:variable name="bea" select="'#cc0000'"/>

                <machine name="Mobile PIII 1.13 GHz">
                    <vm name="sun" mark="1031"/>
                    <vm name="ibm" mark="1098"/>
                    <vm name="bea" mark="1191"/>
                </machine>
                <machine name="Mobile P4 1.7 GHz">
                    <vm name="sun" mark="1165"/>
                    <vm name="ibm" mark="1276"/>
                    <vm name="bea" mark="1505"/>
                </machine>
                <machine name="Desktop Dual Althlon 1.67 GHz (1 CPU)">
                    <vm name="sun" mark="1311"/>
                    <vm name="ibm" mark="1176"/>
                    <vm name="bea" mark="1694"/>
                </machine>
                <machine name="Desktop Dual Althlon 1.67 GHz (2 CPU)">
                    <vm name="sun" mark="1764"/>
                    <vm name="ibm" mark="2165"/>
                    <vm name="bea" mark="2681"/>
                </machine>
            </results>
        </p:input>
        <p:input name="data"><dummy/></p:input>
        <p:output name="data" id="data"/>
    </p:processor>

    <p:processor name="oxf:chart-serializer">
        <p:input name="config"><config/></p:input>
        <p:input name="data" href="#data"/>
        <p:input name="chart">
             <chart>
                 <map>fruits</map>
                 <background-color>#F2F2F2</background-color>
                 <title-color>#281154</title-color>
                 <type>horizontal-bar</type>
                 <title></title>
                 <serie-title>OXF Mark</serie-title>
                 <tick-unit>500.0</tick-unit>
                 <bar-margin>0.1</bar-margin>
                 <category-margin>0.2</category-margin>
                 <value title="Sun JRE 1.4.2_02" categories="/results/machine/@name" series="/results/machine/vm[@name = 'sun']/@mark" color="#594fbf"/>
                 <value title="IBM JRE 1.3.1" categories="/results/machine/@name" series="/results/machine/vm[@name = 'ibm']/@mark" color="#6699cc"/>
                 <value title="BEA JRockit 1.4.1_03" categories="/results/machine/@name" series="/results/machine/vm[@name = 'bea']/@mark" color="#cc0000"/>
                 <legend visible="true" position="south"/>
                 <x-size>500</x-size>
                 <y-size>400</y-size>
             </chart>
        </p:input>
    </p:processor>

</p:config>
