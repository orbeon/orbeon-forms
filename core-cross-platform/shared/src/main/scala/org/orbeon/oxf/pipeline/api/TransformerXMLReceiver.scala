package org.orbeon.oxf.pipeline.api

import org.orbeon.oxf.xml.XMLReceiver

import javax.xml.transform.sax.TransformerHandler


trait TransformerXMLReceiver extends XMLReceiver with TransformerHandler