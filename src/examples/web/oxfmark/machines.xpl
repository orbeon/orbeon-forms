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
                <xsl:variable name="pc-desktop" select="'#666699'"/>
                <xsl:variable name="pc-laptop" select="'#8888BB'"/>
                <xsl:variable name="mac-desktop" select="'#DD9900'"/>
                <xsl:variable name="mac-laptop" select="'#FFBB00'"/>
                <result machine="Desktop Athlon 500 MHz" mark="348" color="{$pc-desktop}"/>
                <result machine="PowerBook G4 800 MHz" mark="615" color="{$mac-laptop}" email="sam@bea.com"/>
                <result machine="Power Mac Dual G4 450 GHz" mark="625" color="{$mac-desktop}" email="steven@kan.org"/>
                <result machine="Mobile PIII 1 GHz" mark="703" color="{$pc-laptop}" comment="Dell Inspiron 4100 Notebook" email="steven@kan.org"/>
                <result machine="PowerBook G4 1.25 GHz" mark="780" color="{$mac-laptop}" email="sam@bea.com"/>
                <result machine="PowerBook G4 1.33 GHz" mark="900" color="{$mac-laptop}" email="michael.descher@gmx.de"/>
                <result machine="Mobile PIII 1.13 GHz" mark="953" color="{$pc-laptop}"/>
                <result machine="PowerBook G4 1.5 GHz" mark="966" color="{$mac-laptop}"/>
                <result machine="Mobile P4 1.7 GHz" mark="1043" color="{$pc-laptop}"/>
                <result machine="Desktop Athlon XP 1.53 GHz" mark="1264" color="{$pc-desktop}"/>
                <result machine="Desktop P4 2.6 GHz" mark="1549" color="{$pc-desktop}" comment="dsmall6@yahoo.com"/>
                <result machine="Mobile Pentium-M 1.4 GHz" mark="1558" color="{$pc-laptop}" comment="Received: 1454 and 1662"/>
                <result machine="Power Mac Dual G4 1.25 GHz" mark="1634" color="{$mac-desktop}" email="p.magdelenat@bs-factory.com"/>
                <result machine="Desktop Dual Althlon 1.67 GHz" mark="1640" color="{$pc-desktop}"/>
                <result machine="Desktop Althlon 2600+" mark="1671" color="{$pc-desktop}" email="jon.strabala@quark2.quantumsi.com"/>
                <result machine="Power Mac Dual G4 1.4 GHz" mark="1693" color="{$mac-desktop}" email="sam@bea.com"/>
                <result machine="Desktop P4 3.06 Extreme Linux" mark="1760" color="{$pc-desktop}" email="avernet@orbeon.com"/>
                <result machine="Desktop P4 3.2 GHz Linux" mark="1928" color="{$pc-laptop}" comment="jmercay@orbeon.com"/>
                <result machine="Mobile Pentium-M 1.7 GHz" mark="1936" color="{$pc-laptop}" comment="matt.allen@riverdynamics.com"/>
                <result machine="Desktop P4 3.4 GHz" mark="2085" color="{$pc-laptop}" comment="erik@bruchez.org"/>
                <result machine="Desktop Dual Xeon 2.79 GHz" mark="2154" color="{$pc-desktop}"/>
                <result machine="Power Mac Dual G5 2 GHz" mark="2294" color="{$mac-desktop}"/>
                <result machine="Desktop Dual P4 Xeon 3.06" mark="2595" color="{$pc-desktop}"/>
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
                 <bar-margin>0.2</bar-margin>
                 <category-margin>0.4</category-margin>
                 <category-label-angle>25</category-label-angle>
                 <value title="" categories="/results/result/@machine" series="/results/result/@mark" colors="/results/result/@color"></value>
                 <legend visible="true" position="south">
                     <item label="PC Laptop" color="#8888BB"/>
                     <item label="PC Desktop" color="#666699"/>
                     <item label="Mac Laptop" color="#FFBB00"/>
                     <item label="Mac Desktop" color="#DD9900"/>
                 </legend>
                 <x-size>500</x-size>
                 <y-size>500</y-size>
             </chart>
        </p:input>
    </p:processor>

</p:config>
