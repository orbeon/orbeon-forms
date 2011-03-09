/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package biz.taoconsulting.oxf.processor.converter;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.SimpleProcessor;
import org.orbeon.oxf.util.Base64XMLReceiver;
import org.orbeon.oxf.xml.XMLUtils;
import org.pdfbox.exceptions.OutlineNotLocalException;
import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.pdmodel.PDDocumentCatalog;
import org.pdfbox.pdmodel.PDDocumentInformation;
import org.pdfbox.pdmodel.PDPage;
import org.pdfbox.pdmodel.common.PDMetadata;
import org.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.pdfbox.util.PDFTextStripper;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Convert from a binary PDF (Acrobat) file to XML. Supports configuration
 * option: <action>pages </action> Extracts text page by page <action>bookmarks
 * </action> Extracts text in bookmarks <action>bookmarksonly </action>Extracts
 * bookmarks only <action>meta </action>Extracts only meta data
 *
 * @author Stephan H. Wissel
 * @version 0.4
 */
public class FromPdfConverter extends SimpleProcessor {

    //TODO: Move that somewhere less visible?
    private List allPages = null; // Array of all pages

    //Key Variables for static info - Parameter names
    private static final String INPUT_CONFIG = "config";

    private static final String INPUT_SCOPE = "string(//action)";

    private static final String INPUT_DATA = "data";

    private static final String OUTPUT_DATA = "data";

    //DEFINED SCOPES
    private static final String SCOPE_BOOKMARKS = "bookmarks";

    private static final String SCOPE_PAGES = "pages";

    private static final String SCOPE_BOOKMARKSONLY = "bookmarksonly";

    private static final String SCOPE_BOOKMARKPAGES = "bookmarkpages";

    private static final String SCOPE_METADATA = "meta";

    // ToDo: find a smarter way for scope list
    private static final String[] LIST_OF_SCOPES = {SCOPE_BOOKMARKS, SCOPE_PAGES, SCOPE_BOOKMARKSONLY, SCOPE_METADATA,
                                                    SCOPE_BOOKMARKPAGES};

    // Tag attributes
    private static final String ATT_CDATA = "CDATA";

    private static final String ATT_PAGES = "pages";

    private static final String ATT_PAGE = "page";

    private static final String ATT_LEVEL = "level";

    private static final String ATT_PAGENUM = "number";

    private static final String ATT_AUTHOR = "author";

    private static final String ATT_TITLE = "title";

    private static final String ATT_SUBJECT = "subject";

    // Tag names and attributes
    private static final String TAG_ROOT = "PDFDocument";

    private static final String TAG_META = "PDFMetadata";

    private static final String TAG_PAGE = "Page";

    private static final String TAG_BOOKMARK = "Bookmark";

    private static final String TAG_TITLE = "Title";

    private static final String TAG_TEXT = "Text";

    private static final String TAG_ERROR = "Error";

    // Logger
    private static final Logger logger = Logger.getLogger(FromPdfConverter.class);

    public FromPdfConverter() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public void generateData(PipelineContext context, XMLReceiver xmlReceiver) {
        // We will read the scope of conversion from the Config input
        // the PDF from Data and write the output to Data
        // Data Input is base 64, so sax won't do

        // Read the configuration options and get the scope
        String scope = getScopeFromInput(context, INPUT_CONFIG);

        // Read binary content of PDF into an Inputstream
        InputStream pdfStream = getPDFStreamFromInput(context, INPUT_DATA);

        // Process the PDF, we get the SAX Stream back directly
        logger.info("Ready to call extractFromPDF");
        extractFromPDF(pdfStream, xmlReceiver, scope);

    }

    /**
     * @param context
     * @param inputName
     * @return pdfStream
     */
    private InputStream getPDFStreamFromInput(PipelineContext context, String inputName) {

        // Our result
        InputStream pdfStream = null;

        // Get the encoded data from the context
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        // Now read the binary PDF File, so we can generate the XML
        logger.info("Creating the Base64 Content handler for the uploaded file");
        Base64XMLReceiver base64ContentHandler = new Base64XMLReceiver(os);

        try {
            readInputAsSAX(context, inputName, base64ContentHandler);
            final byte[] fileContent = os.toByteArray();
            pdfStream = new ByteArrayInputStream(fileContent);
        } catch (Exception e) {
            logger.error(e);
        }
        //Return what we got
        return pdfStream;

    }

    /**
     * Extracts the action to be taken from the input and validates the action
     * against the list of defined actions
     *
     * @param context
     * @param inputName

     * @return the validated scope
     */
    private String getScopeFromInput(PipelineContext context, String inputName) {
        String scope;
        Document scopeDocument = readInputAsDOM4J(context, inputName);
        scope = (String) scopeDocument.selectObject(INPUT_SCOPE);

        // If somebody forgot the input we use a default
        if (scope == null)
            scope = SCOPE_BOOKMARKS;

        // Now check the scope for being valid
        scope = scope.toLowerCase(); // we like it lowercase

        for (int i = 0; i < LIST_OF_SCOPES.length; i++) {
            if (LIST_OF_SCOPES[i].equals(scope)) {
                return scope; // We found a valid scope
            }
        }

        return SCOPE_BOOKMARKS; // If we got here the scope wasn't valid
    }

    private void extractFromPDF(InputStream inputStream, XMLReceiver xmlReceiver, String scope) {

        logger.info("Extract from PDF started");
        // Write the header information of the PDF
        PDDocument doc = null;

        try {
            xmlReceiver.startDocument();
            logger.info("Start document completed");

            // Some variables for our PDF processing
            doc = getPDFdocument(inputStream, xmlReceiver); // PDF
            // Document

            if (doc == null) {
                xmlReceiver.startElement("", TAG_ROOT, TAG_ROOT, null);
                addErrorTagToOutput(xmlReceiver, "No PDF Information could be extracted");
                xmlReceiver.endElement("", TAG_ROOT, TAG_ROOT);
                return; //No processing on empty documents

            }

        } catch (SAXException e) {
            logger.error(e);
        }

        // Get a handle on all pages in the PDF. Needed for page lookup
        try {
            logger.info("Try to get handle on all pages array");
            this.allPages = doc.getDocumentCatalog().getAllPages();
            logger.info("Got handle to allPages in PDF");
        } catch (Exception e) {
            logger.error(e);
            addErrorTagToOutput(xmlReceiver, e.toString());
            this.allPages = null;
        }

        try {

            // Get the document information
            logger.info("Ready to retrieve basic PDF information");
            PDDocumentInformation docInfo = getDocumentInformation(doc, xmlReceiver);
            AttributesImpl atts = new AttributesImpl();
            // Capture the number of pages
            addPageCountAttribute(atts, doc);

            // Now add some document Info
            addDocInfoAttributes(atts, docInfo);

            // Start the PDF Document
            logger.info("writing the root element PDFDocument");
            xmlReceiver.startElement("", TAG_ROOT, TAG_ROOT, atts);

            logger.info("PDFDocument tag succesful opened");
            //Pull the Meta data from the PDF Document
            atts = new AttributesImpl();

            logger.info("PDFMetadata Element start");
            xmlReceiver.startElement("", TAG_META, TAG_META, atts);
            extractMetaDataFromPDF(xmlReceiver, doc);
            xmlReceiver.endElement("", TAG_META, TAG_META);
            logger.info("PDFMetadata Element end");
            // Get the PDF Content based on the selection in config

            if (scope.equals(SCOPE_PAGES)) {
                //PDF page by page
                logger.info("Will extract pages");
                extractPagesFromPDF(xmlReceiver, doc);
            } else if (scope.equals(SCOPE_METADATA)) {
                logger.info("No action bejond meta data");
                // No further action required since it was meta data only!
            } else if (scope.equals(SCOPE_BOOKMARKPAGES)) {
                // Try bookmarks then pages
                logger.info("Will extract bookmarks first then pages");
                if (!extractOutlineFromPDF(xmlReceiver, doc, scope)) {
                    logger.info("No outline found, using pages");
                    extractPagesFromPDF(xmlReceiver, doc);
                }

            } else {
                // PDF in outlines - default
                logger.info("Will extract: " + scope);
                extractOutlineFromPDF(xmlReceiver, doc, scope);
            }

            //If we got here it worked
            logger.info("Writing end element " + TAG_ROOT);
            xmlReceiver.endElement("", TAG_ROOT, TAG_ROOT);
            logger.info("About to close PDFDocument and SaxDocument");
            xmlReceiver.endDocument();
            doc.close(); // We finish it once we are done
            logger.info("Closed PDF and XML");
        } catch (IOException e) {
            logger.error(e);
            addErrorTagToOutput(xmlReceiver, e.toString());
        } catch (SAXException e) {
            logger.error(e);
            addErrorTagToOutput(xmlReceiver, e.toString());
        }
    }

    /**
     * @param atts
     * @param docInfo
     */
    private void addDocInfoAttributes(AttributesImpl atts, PDDocumentInformation docInfo) {
        if (docInfo != null) {
            String author = docInfo.getAuthor();
            if (author != null)
                atts.addAttribute("", ATT_AUTHOR, ATT_AUTHOR, ATT_CDATA, author);
            String title = docInfo.getTitle();
            if (title != null)
                atts.addAttribute("", ATT_TITLE, ATT_TITLE, ATT_CDATA, title);
            String subject = docInfo.getSubject();
            if (subject != null)
                atts.addAttribute("", ATT_SUBJECT, ATT_SUBJECT, ATT_CDATA, subject);
        }

    }

    /**
     * @param inputStream
     * @param contentHandler
     * @return
     */
    private PDDocument getPDFdocument(InputStream inputStream, ContentHandler contentHandler) {
        PDDocument doc = null;
        // Create access to PDF Document
        try {
            // We get the document from the inputstream
            doc = PDDocument.load(inputStream);
        } catch (IOException e) {
            logger.error("PDFParser(InputStream)", e);
            doc = null; // We reset the object
            // We write our some stuff into output document, so we have a
            // chance to see what went wrong
            addErrorTagToOutput(contentHandler, e.toString());
        }

        return doc;

    }

    /**
     * Adds an error element to the output
     *
     * @param contentHandler
     * @param eMessage
     */
    private void addErrorTagToOutput(ContentHandler contentHandler, String eMessage) {
        try {
            contentHandler.startElement("", TAG_ERROR, TAG_ERROR, null);
            contentHandler.characters(eMessage.toCharArray(), 0, eMessage.length());
            contentHandler.endElement("", TAG_ERROR, TAG_ERROR);
        } catch (SAXException e) {
            logger.error(e);
        }

    }

    /**
     * @param doc
     * @return PDFDocumentInformation
     */
    private PDDocumentInformation getDocumentInformation(PDDocument doc, ContentHandler contentHandler) {
        PDDocumentInformation tmpInfo = null;
        try {
            tmpInfo = doc.getDocumentInformation();
        } catch (Exception e) {
            logger.error(e);
            addErrorTagToOutput(contentHandler, e.toString());
        }
        return tmpInfo;
    }

    /**
     * @param atts
     * @param doc
     */
    private void addPageCountAttribute(AttributesImpl atts, PDDocument doc) {
        int pageCount = 0; //The number of pages in this document
        try {
            pageCount = doc.getPageCount();
        } catch (IOException e) {
            logger.error(e);
            pageCount = 0;
        }
        if (pageCount > 0) {
            atts.addAttribute("", ATT_PAGES, ATT_PAGES, ATT_CDATA, String.valueOf(pageCount));
        }

    }

    /**
     * @param xmlReceiver
     * @param doc
     */
    private boolean extractMetaDataFromPDF(XMLReceiver xmlReceiver, PDDocument doc) {
        // Reads the meta data from the input stream and pushes them 1:1 to the
        // output
        // The Meta data is converted to sax using XMLUtils and start/end document
        // are simply
        // removed. The rest is moved through...
        boolean tmpReturn = true; // Benefit of the doubt
        logger.info("Processing META data");
        try {
            PDDocumentCatalog catalog = doc.getDocumentCatalog(); //Where meta
            // data lives
            PDMetadata metadata = catalog.getMetadata();
            // The meta data could be empty!
            if (metadata == null)
                return false;

            // The content handler for that input stream...
            final XMLReceiver pdfMetaContent = new PdfMetadataContentHandler(xmlReceiver);

            //read the XML metadata into an inputstream
            InputStream xmlInputStream = metadata.createInputStream();
            logger.info("Before creating sax stream for meta data");
            XMLUtils.inputStreamToSAX(xmlInputStream, "PDF", pdfMetaContent, XMLUtils.ParserConfiguration.PLAIN, false);
            //Now pull it in and write it out 1:1
            logger.info("Meta data stream created in SAX");
        } catch (IOException e) {
            // If it goes wrong
            logger.error(e);
            addErrorTagToOutput(xmlReceiver, e.toString());
            tmpReturn = false;
        } catch (Exception e) {
            logger.error(e);
            addErrorTagToOutput(xmlReceiver, e.toString());
            tmpReturn = false;
        }
        return tmpReturn;
    }

    /**
     * @param xmlReceiver
     * @param doc
     */
    private boolean extractPagesFromPDF(XMLReceiver xmlReceiver, PDDocument doc) {

        // This extracts all pages with the text per page
        boolean tmpReturn = true; // Benefit of the doubt
        PDFTextStripper stripper;
        try {
            stripper = new PDFTextStripper();

            AttributesImpl atts;

            // Capture the number of pages
            int pageCount = doc.getPageCount();

            //Loop through all the pages;
            for (int i = 1; i <= pageCount; i++) {
                atts = new AttributesImpl();
                atts.addAttribute("", ATT_PAGENUM, ATT_PAGENUM, ATT_CDATA, String.valueOf(i));
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String textBetweenBookmarks = stripper.getText(doc);
                xmlReceiver.startElement("", TAG_PAGE, TAG_PAGE, atts);
                textBetweenBookmarks = MassageTextResult(textBetweenBookmarks);
                xmlReceiver.characters(textBetweenBookmarks.toCharArray(), 0, textBetweenBookmarks.length());
                xmlReceiver.endElement("", TAG_PAGE, TAG_PAGE);
            }
        } catch (IOException e) {
            // If it goes wrong
            logger.error(e);
            addErrorTagToOutput(xmlReceiver, e.toString());
            tmpReturn = false;
        } catch (SAXException e) {
            // If it goes wrong
            logger.error(e);
            addErrorTagToOutput(xmlReceiver, e.toString());
            tmpReturn = false;
        }
        return tmpReturn;
    }

    private boolean extractOutlineFromPDF(ContentHandler contentHandler, PDDocument doc, String scope) {

        //    Get the document catalog
        boolean tmpReturn = true; // Benefit of the doubt
        PDDocumentOutline root = null;
        PDOutlineItem item = null;

        // Get the outline if there is one
        try {
            root = doc.getDocumentCatalog().getDocumentOutline();
        } catch (Exception e) {
            logger.error(e);
            addErrorTagToOutput(contentHandler, e.toString());
            tmpReturn = false;
        }
        // No further processing if the outline is null
        if (root == null) {
            tmpReturn = false;
        } else {
            // We try to get our hands on the content

            try {

                item = root.getFirstChild();
                // Check if there is anything
                if (item == null) {
                    tmpReturn = false; // No outline without a first element!
                } else {
                    while (item != null) {
                        // Memorize the next object
                        // Recursive call into bookmark processing;
                        processBookmark(contentHandler, doc, item, scope, 1);
                        logger.info(item.getTitle());
                        item = item.getNextSibling();
                    }
                }
            } catch (Exception e) {
                logger.error(e);
                tmpReturn = false;
            }
        }
        return tmpReturn;
    }

    /**
     * processBookmark gets called recursively for all nested bookmarks extracts
     * the bookmark and the text

     */
    private void processBookmark(ContentHandler hd, PDDocument doc, PDOutlineItem curItem, String scope, int level) {
        // First we check on what page the bookmark is. If we can't retrieve the
        // page the bookmark can't be the outline we are looking for, however we
        // would process children (you never know)
        try {

            int curPageNo = getPageNumber(doc, curItem);

            if (curPageNo > -1) {

                AttributesImpl atts = new AttributesImpl();
                atts.addAttribute("", ATT_LEVEL, ATT_LEVEL, ATT_CDATA, Integer.toString(level));
                atts.addAttribute("", ATT_PAGE, ATT_PAGE, ATT_CDATA, Integer.toString(curPageNo));

                hd.startElement("", TAG_BOOKMARK, TAG_BOOKMARK, atts);

                // Write the properties of interest
                atts.clear();
                hd.startElement("", TAG_TITLE, TAG_TITLE, atts);
                String curTitle = curItem.getTitle();
                hd.characters(curTitle.toCharArray(), 0, curTitle.length());
                hd.endElement("", TAG_TITLE, TAG_TITLE);

                //write out the text associated with this bookmark
                // if the scope allows for that

                if (!scope.toLowerCase().equals(SCOPE_BOOKMARKSONLY)) {

                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setStartBookmark(curItem);
                    stripper.setEndBookmark(curItem);
                    String textBetweenBookmarks = stripper.getText(doc);
                    hd.startElement("", TAG_TEXT, TAG_TEXT, atts);
                    textBetweenBookmarks = MassageTextResult(textBetweenBookmarks);
                    hd.characters(textBetweenBookmarks.toCharArray(), 0, textBetweenBookmarks.length());
                    hd.endElement("", TAG_TEXT, TAG_TEXT);

                }

            }
            // Now check the children
            PDOutlineItem child = curItem.getFirstChild();
            while (child != null) {
                processBookmark(hd, doc, child, scope, level + 1);
                logger.info("Child:" + child.getTitle());
                child = child.getNextSibling();
            }
            // Close the mark
            hd.endElement("", TAG_BOOKMARK, TAG_BOOKMARK);
        } catch (SAXException e) {
            logger.error(e);
            addErrorTagToOutput(hd, e.toString());
        } catch (IOException e) {
            logger.error(e);
            addErrorTagToOutput(hd, e.toString());
        } finally {
            // Nothing concluding to do
        }
    }

    private String MassageTextResult(String rawString) {
        // Removes unwanted characters
        // Currently we need to get rid of chr(13);
        String oldChar = new Character((char) 13).toString();
        return StringUtils.replace(rawString, oldChar, "");
        //return rawString.replace(oldChar, ""); // this only works with Java 5
    }

    private int getPageNumber(PDDocument doc, PDOutlineItem outline) {

        int pageNumber = -1;
        PDPage page = null;
        try {
            page = outline.findDestinationPage(doc);
            if (page != null && this.allPages != null) {
                pageNumber = this.allPages.indexOf(page);
            }

        } catch (OutlineNotLocalException e) {
            logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        } catch (Exception e) {
            logger.error(e);
        }

        return pageNumber;
    }
}
