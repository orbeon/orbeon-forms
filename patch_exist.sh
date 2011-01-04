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
VERSION=1_4_1_dev
TODAY_DATE=`date +%Y%m%d`
FULL_VERSION=$VERSION\_orbeon_$TODAY_DATE

export ANT_OPTS=-Xmx256m
cp "$ORBEON_HOME"/lib/xerces-xercesImpl-2_9_orbeon_20070711.jar lib/endorsed/
cp "$ORBEON_HOME"/lib/saxon-9-1-0-8_orbeon_20101223.jar lib/endorsed/
cp "$ORBEON_HOME"/build/lib/orbeon.jar lib/endorsed/
for F in $(find src -name *.java)
do
    sed -i -e 's/org.apache.xerces/orbeon.apache.xerces/g' $F
    sed -i -e 's/net.sf.saxon/org.orbeon.saxon/g' $F
    sed -i -e 's/SAXParserFactory.newInstance()/new org.orbeon.oxf.xml.xerces.XercesSAXParserFactoryImpl()/g' $F
    #sed -i -e 's/= DocumentBuilderFactory/= orbeon.apache.xerces.jaxp.DocumentBuilderFactoryImpl/g' $F
done

# Build
./build.sh clean
./build.sh

# Remove existing JARs
rm "$ORBEON_HOME"/lib/*exist*

# Copy eXist JARs
cp exist.jar "$ORBEON_HOME"/lib/exist-$FULL_VERSION.jar
cp exist-optional.jar "$ORBEON_HOME"/lib/exist-optional-$FULL_VERSION.jar
cp lib/extensions/exist-modules.jar "$ORBEON_HOME"/lib/exist-modules-$FULL_VERSION.jar
cp lib/extensions/exist-ngram-module.jar "$ORBEON_HOME"/lib/exist-ngram-module-$FULL_VERSION.jar

# Copy eXist dependencies JARs
cp lib/core/antlr-2.7.7.jar           "$ORBEON_HOME"/lib/exist-dependency-antlr-2.7.7.jar
cp lib/core/jgroups-all-2.2.6.jar     "$ORBEON_HOME"/lib/exist-dependency-jgroups-all-2.2.6.jar
cp lib/core/jta-1.1.jar               "$ORBEON_HOME"/lib/exist-dependency-jta-1.1.jar
cp lib/core/quartz-1.6.6.jar          "$ORBEON_HOME"/lib/exist-dependency-quartz-1.6.6.jar
cp lib/core/stax-api-1.0.1.jar        "$ORBEON_HOME"/lib/exist-dependency-stax-api-1.0.1.jar
cp lib/core/ws-commons-util-1.0.2.jar "$ORBEON_HOME"/lib/exist-dependency-ws-commons-util-1.0.2.jar
cp lib/core/xmldb.jar                 "$ORBEON_HOME"/lib/exist-xmldb.jar
cp lib/core/xmlrpc-client-3.1.2.jar   "$ORBEON_HOME"/lib/exist-dependency-xmlrpc-client-3.1.2.jar
cp lib/core/xmlrpc-common-3.1.2.jar   "$ORBEON_HOME"/lib/exist-dependency-xmlrpc-common-3.1.2.jar
cp lib/core/xmlrpc-server-3.1.2.jar   "$ORBEON_HOME"/lib/exist-dependency-xmlrpc-server-3.1.2.jar
cp lib/endorsed/resolver-1.2.jar      "$ORBEON_HOME"/lib/exist-dependency-resolver-1.2.jar


# Create patch file
svn diff > "$ORBEON_HOME"/tools/eXist-$VERSION-$TODAY_DATE.patch
