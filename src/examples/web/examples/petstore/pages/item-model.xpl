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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="data"/>
    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>



    <p:choose href="../database/type.xml">
        <p:when test="/type = 'file'">
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="../database/database.xpl"/>
                <p:output name="data" id="database"/>
            </p:processor>

            <p:processor name="oxf:xslt">
                <p:input name="data" href="aggregate('root', #instance, #database)"/>
                <p:input name="config">
                    <xsl:stylesheet version="1.0">
                        <xsl:template match="/">
                            <xsl:variable name="item" select="/root/Populate/Catalog/Items/Item[@id = /root/form/item-id]"/>
                            <detail>
                                <id><xsl:value-of select="$item/@id"/></id>
                                <description>
                                    <xsl:value-of select="$item/ItemDetails/Description"/>
                                </description>
                                <price>
                                    <xsl:value-of select="$item/ItemDetails/ListPrice"/>
                                </price>
                                <image>
                                    <xsl:value-of select="$item/ItemDetails/Image"/>
                                </image>
                            </detail>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>

        <p:when test="/type='db'">
            <p:processor name="oxf:pipeline">
                 <p:input name="config" href="../database/locale.xpl"/>
                 <p:output name="locale" id="locale"/>
             </p:processor>

             <p:processor name="oxf:sql">
                 <p:input name="data" href="aggregate('root', #instance, #locale)"/>
                 <p:input name="config">
                   <sql:config  xmlns:sql="http://orbeon.org/oxf/xml/sql">
                       <sql:connection>
                           <sql:datasource>db</sql:datasource>
                               <sql:execute>
                                   <sql:query>
                                       select * from item, item_details
                                         where item.itemid = item_details.itemid
                                         and item.itemid = <sql:parameter type="xs:string" select="/root/form/item-id"/>
                                         and item_details.locale = <sql:parameter type="xs:string" select="/root/locale"/>
                                   </sql:query>
                                   <sql:results>
                                       <sql:row-results>
                                           <detail>
                                               <id><sql:get-column type="xs:string" column="itemid"/></id>
                                               <description><sql:get-column type="xs:string" column="descn"/></description>
                                               <price><sql:get-column type="xs:string" column="listprice"/></price>
                                               <image><sql:get-column type="xs:string" column="image"/></image>
                                           </detail>
                                       </sql:row-results>
                                   </sql:results>
                               </sql:execute>
                       </sql:connection>
                   </sql:config>
                 </p:input>
                 <p:output name="data" ref="data"/>
             </p:processor>

        </p:when>

    </p:choose>
</p:config>
