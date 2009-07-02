#!/bin/sh

# This is a script which you can run under the checked out eXist source code directory to update eXist's references to
# XML parser and XSLT code. This worked as of 2009-07-02.
#
# Before running this script:
#
# 1) Set ORBEON_HOME to point to your Orbeon Forms source directory (which contains Orbeon's build.xml).
# 2) Make sure that you have a compiled version of Orbeon Forms (will look for $ORBEON_HOME/build/lib/orbeon.jar)
# 3) Run this script from the eXist root directory

ORBEON_HOME=../orbeon
VERSION=1_2_6
TODAY_DATE=`date +%Y%m%d`
FULL_VERSION=$VERSION\_orbeon_$TODAY_DATE

export ANT_OPTS=-Xmx256m
rm lib/endorsed/xercesImpl-2.9.1.jar
cp $ORBEON_HOME/lib/xerces-xercesImpl-2_9_orbeon_20070711.jar lib/endorsed/
cp $ORBEON_HOME/lib/saxon-8_8_orbeon_20090617.jar lib/endorsed/
cp $ORBEON_HOME/build/lib/orbeon.jar lib/endorsed/
for F in $(find src -name *.java)
do
    sed -i -e 's/org.apache.xerces/orbeon.apache.xerces/g' $F
    sed -i -e 's/net.sf.saxon/org.orbeon.saxon/g' $F
    sed -i -e 's/SAXParserFactory.newInstance()/new org.orbeon.oxf.xml.xerces.XercesSAXParserFactoryImpl()/g' $F
    #sed -i -e 's/= DocumentBuilderFactory/= orbeon.apache.xerces.jaxp.DocumentBuilderFactoryImpl/g' $F
done

# Build
ant clean
ant

# Remove existing JARs
rm $ORBEON_HOME/lib/*exist*

# Copy eXist JARs
cp exist.jar $ORBEON_HOME/lib/exist-$FULL_VERSION.jar
cp exist-optional.jar $ORBEON_HOME/lib/exist-optional-$FULL_VERSION.jar
cp lib/extensions/exist-modules.jar $ORBEON_HOME/lib/exist-modules-$FULL_VERSION.jar
cp lib/extensions/exist-ngram-module.jar $ORBEON_HOME/lib/exist-ngram-module-$FULL_VERSION.jar

# Copy eXist dependencies JARs
cp lib/core/antlr-2.7.6.jar $ORBEON_HOME/lib/exist-antlr-2_7_6.jar
cp lib/core/jgroups-all.jar $ORBEON_HOME/lib/exist-jgroups-all-exist.jar
cp lib/core/xmldb.jar $ORBEON_HOME/lib/exist-xmldb.jar
cp lib/core/xmlrpc-client-3.1.1.jar $ORBEON_HOME/lib/exist-xmlrpc-client-3_1_1.jar
cp lib/core/xmlrpc-common-3.1.1.jar $ORBEON_HOME/lib/exist-xmlrpc-common-3_1_1.jar
cp lib/core/xmlrpc-server-3.1.1.jar $ORBEON_HOME/lib/exist-xmlrpc-server-3_1_1.jar
cp lib/core/quartz-1.6.0.jar $ORBEON_HOME/lib/exist-quartz-1_6_0.jar 
cp lib/core/jta.jar $ORBEON_HOME/lib/exist-jta.jar
cp lib/core/stax-api-1.0.1.jar $ORBEON_HOME/lib/exist-stax-api-1_0_1.jar

# Create patch file
svn diff > $ORBEON_HOME/tools/eXist-$VERSION-$TODAY_DATE.patch
