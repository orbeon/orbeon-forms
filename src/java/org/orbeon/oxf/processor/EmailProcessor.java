/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor;

import org.apache.commons.fileupload.DefaultFileItemFactory;
import org.apache.commons.fileupload.FileItem;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.RequestGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SystemUtils;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.ProcessorOutputXMLReader;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.Iterator;
import java.util.Properties;

/**
 * This processor allows sending emails. It supports multipart messages and inline as well as
 * out-of-line attachments.
 *
 * For some useful JavaMail information: http://java.sun.com/products/javamail/FAQ.html
 *
 * TODO:
 * o revise support of text/html
 *   o built-in support for HTML could handle src="cid:*" with part/message ids
 * o support text/xml? or just XHTML?
 * o build message with SAX, not DOM, so streaming of input is possible
 */
public class EmailProcessor extends ProcessorImpl {

    // Properties for this processor
    public static final String EMAIL_SMTP_HOST = "smtp-host";
    public static final String EMAIL_TEST_TO = "test-to";
    public static final String EMAIL_TEST_SMTP_HOST = "test-smtp-host";

    public static final String EMAIL_FORCE_TO_DEPRECATED = "forceto"; // deprecated
    public static final String EMAIL_HOST_DEPRECATED = "host"; // deprecated

    public static final String EMAIL_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/email";

    private static final String DEFAULT_MULTIPART = "mixed";
    private static final String DEFAULT_TEXT_ENCODING = "iso-8859-1";

    public EmailProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA, EMAIL_CONFIG_NAMESPACE_URI));
    }

    public void start(PipelineContext pipelineContext) {
        try {
            Document dataDocument = readInputAsDOM4J(pipelineContext, INPUT_DATA);
            Element messageElement = dataDocument.getRootElement();

            // Get system id (will likely be null if document is generated dynamically)
            LocationData locationData = (LocationData) messageElement.getData();
            String dataInputSystemId = locationData.getSystemID();

            // Set SMTP host
            Properties properties = new Properties();
            String testSmtpHostProperty = getPropertySet().getString(EMAIL_TEST_SMTP_HOST);

            if (testSmtpHostProperty != null) {
                // Test SMTP Host from properties overrides the local configuration
                properties.setProperty("mail.smtp.host", testSmtpHostProperty);
            } else {
                // Try regular config parameter and property
                String host = messageElement.element("smtp-host").getTextTrim();
                if (host != null && !host.equals("")) {
                    // Precedence goes to the local config parameter
                    properties.setProperty("mail.smtp.host", host);
                } else {
                    // Otherwise try to use a property
                    host = getPropertySet().getString(EMAIL_SMTP_HOST);
                    if (host == null)
                        host = getPropertySet().getString(EMAIL_HOST_DEPRECATED);
                    if (host == null)
                        throw new OXFException("Could not find SMTP host in configuration or in properties");
                    properties.setProperty("mail.smtp.host", host);

                }
            }

            // Create message
            Session session = Session.getInstance(properties);
            Message message = new MimeMessage(session);

            // Set From
            message.setFrom(createAddress(messageElement.element("from")));

            // Set To
            String testToProperty = getPropertySet().getString(EMAIL_TEST_TO);
            if (testToProperty == null)
                testToProperty = getPropertySet().getString(EMAIL_FORCE_TO_DEPRECATED);

            if (testToProperty != null) {
                // Test To from properties overrides local configuration
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(testToProperty));
            } else {
                // Regular list of To elements
                for (Iterator i = messageElement.elements("to").iterator(); i.hasNext();) {
                    Element toElement = (Element) i.next();
                    InternetAddress address = createAddress(toElement);
                    message.addRecipient(Message.RecipientType.TO, address);
                }
            }

            // Set subject
            message.setSubject(messageElement.element("subject").getStringValue());

            // Handle body
            Element textElement = messageElement.element("text");
            Element bodyElement = messageElement.element("body");

            if (textElement != null) {
                // Old deprecated mechanism (simple text body)
                message.setText(textElement.getStringValue());
            } else if (bodyElement != null) {
                // New mechanism with body and parts
                handleBody(pipelineContext, dataInputSystemId, message, bodyElement);
            } else {
                throw new OXFException("Main text or body element not found");// TODO: location info
            }

            // Send message
            Transport transport = session.getTransport("smtp");
            Transport.send(message);
            transport.close();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private void handleBody(PipelineContext pipelineContext, String dataInputSystemId, Part parentPart, Element bodyElement) throws Exception {

        // Find out if there are embedded parts
        Iterator parts = bodyElement.elementIterator("part");
        String multipart;
        if (bodyElement.getName().equals("body")) {
            multipart = bodyElement.attributeValue("mime-multipart");
            if (multipart != null && !parts.hasNext())
                throw new OXFException("mime-multipart attribute on body element requires part children elements");
            String contentTypeFromAttribute = NetUtils.getContentTypeContentType(bodyElement.attributeValue("content-type"));
            if (contentTypeFromAttribute != null && contentTypeFromAttribute.startsWith("multipart/"))
                contentTypeFromAttribute.substring("multipart/".length());
            if (parts.hasNext() && multipart == null)
                multipart = DEFAULT_MULTIPART;
        } else {
            String contentTypeAttribute = NetUtils.getContentTypeContentType(bodyElement.attributeValue("content-type"));
            multipart = (contentTypeAttribute != null && contentTypeAttribute.startsWith("multipart/")) ? contentTypeAttribute.substring("multipart/".length()) : null;
        }

        if (multipart != null) {
            // Multipart content is requested
            MimeMultipart mimeMultipart = new MimeMultipart(multipart);

            // Iterate through parts
            for (Iterator i = parts; i.hasNext();) {
                Element partElement = (Element) i.next();

                MimeBodyPart mimeBodyPart = new MimeBodyPart();
                handleBody(pipelineContext, dataInputSystemId, mimeBodyPart, partElement);
                mimeMultipart.addBodyPart(mimeBodyPart);
            }

            // Set content on parent part
            parentPart.setContent(mimeMultipart);
        } else {
            // No multipart, just use the content of the element and add to the current part (which can be the main message)
            handlePart(pipelineContext, dataInputSystemId, parentPart, bodyElement);
        }
    }

    private void handlePart(PipelineContext pipelineContext, String dataInputSystemId, Part parentPart, Element partOrBodyElement) throws Exception {
        final String name = partOrBodyElement.attributeValue("name");
        String contentTypeAttribute = partOrBodyElement.attributeValue("content-type");
        final String contentType = NetUtils.getContentTypeContentType(contentTypeAttribute);
        final String charset;
        {
            String c = NetUtils.getContentTypeCharset(contentTypeAttribute);
            charset = (c != null) ? c : DEFAULT_TEXT_ENCODING;
        }
        final String contentTypeWithCharset = contentType + "; charset=" + charset;
        final String src = partOrBodyElement.attributeValue("src");

        // Either a String or a FileItem
        final Object content;
        if (src != null) {
            // Content of the part is not inline

            // Generate a Document from the source
            SAXSource source = getSAXSource(EmailProcessor.this, pipelineContext, src, dataInputSystemId, contentType);
            content = handleStreamedPartContent(pipelineContext, source, contentType, charset);
        } else {
            // Content of the part is inline

            // In the cases of text/html and XML, there must be exactly one root element
            boolean needsRootElement = "text/html".equals(contentType);// || ProcessorUtils.isXMLContentType(contentType);
            if (needsRootElement && partOrBodyElement.elements().size() != 1)
                throw new ValidationException("The <body> or <part> element must contain exactly one element for text/html",
                        (LocationData) partOrBodyElement.getData());

            // Create Document and convert it into a String
            Element rootElement = (Element)(needsRootElement ? partOrBodyElement.elements().get(0) : partOrBodyElement);
            Document partDocument = DocumentHelper.createDocument();
            partDocument.setRootElement((Element) Dom4jUtils.cloneNode(rootElement));
            content = handleInlinePartContent(partDocument, contentType);
        }

        if (!(ProcessorUtils.isTextContentType(contentType) || ProcessorUtils.isXMLContentType(contentType))) {
            // This is binary content
            if (content instanceof FileItem) {
                final FileItem fileItem = (FileItem) content;
                parentPart.setDataHandler(new DataHandler(new DataSource() {
                    public String getContentType() {
                        return contentType;
                    }

                    public InputStream getInputStream() throws IOException {
                        return fileItem.getInputStream();
                    }

                    public String getName() {
                        return name;
                    }

                    public OutputStream getOutputStream() throws IOException {
                        throw new IOException("Write operation not supported");
                    }
                }));
            } else {
                byte[] data = XMLUtils.base64StringToByteArray((String) content);
                parentPart.setDataHandler(new DataHandler(new SimpleBinaryDataSource(name, contentType, data)));
            }
        } else {
            // This is text content
            if (content instanceof FileItem) {
                // The text content was encoded when written to the FileItem
                final FileItem fileItem = (FileItem) content;
                parentPart.setDataHandler(new DataHandler(new DataSource() {
                    public String getContentType() {
                        // This always contains a charset
                        return contentTypeWithCharset;
                    }

                    public InputStream getInputStream() throws IOException {
                        // This is encoded with the appropriate charset (user-defined, or the default)
                        return fileItem.getInputStream();
                    }

                    public String getName() {
                        return name;
                    }

                    public OutputStream getOutputStream() throws IOException {
                        throw new IOException("Write operation not supported");
                    }
                }));
            } else {
                parentPart.setDataHandler(new DataHandler(new SimpleTextDataSource(name, contentTypeWithCharset, (String) content)));
            }
        }

        // Set content-disposition header
        String contentDisposition = partOrBodyElement.attributeValue("content-disposition");
        if (contentDisposition != null)
            parentPart.setDisposition(contentDisposition);

        // Set content-id header
        String contentId = partOrBodyElement.attributeValue("content-id");
        if (contentId != null)
            parentPart.setHeader("content-id", "<" + contentId + ">");
            //part.setContentID(contentId);
    }

    private String handleInlinePartContent(Document document, String contentType) throws SAXException {
        if ("text/html".equals(contentType)) {
            // Convert XHTML into an HTML String
            StringWriter writer = new StringWriter();
            TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
            identity.getTransformer().setOutputProperty(OutputKeys.METHOD, "html");
            identity.setResult(new StreamResult(writer));
            LocationSAXWriter saxw = new LocationSAXWriter();
            saxw.setContentHandler(identity);
            saxw.write(document);

            return writer.toString();
        } else {
            // For other types, just return the text nodes
            return document.getStringValue();
        }
    }

    private FileItem handleStreamedPartContent(PipelineContext pipelineContext, SAXSource source, String contentType, String encoding)
            throws IOException, TransformerException {

        final FileItem fileItem = new DefaultFileItemFactory(RequestGenerator.getMaxMemorySizeProperty(), SystemUtils.getTemporaryDirectory())
                .createItem("dummy", "dummy", false, null);
        // Make sure the file is deleted when the context is destroyed
        pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter() {
            public void contextDestroyed(boolean success) {
                fileItem.delete();
            }
        });
        // Write character content to the FileItem instance
        Writer writer = null;
        OutputStream os = null;

        final boolean useWriter = ProcessorUtils.isTextContentType(contentType) || ProcessorUtils.isXMLContentType(contentType);

        try {
            os = fileItem.getOutputStream();
            writer = new BufferedWriter(new OutputStreamWriter(os, encoding));
            final OutputStream _os = os;
            final Writer _writer = writer;
            Transformer identity = TransformerUtils.getIdentityTransformer();
            identity.transform(source, new SAXResult(new ForwardingContentHandler() {
                public void characters(char[] chars, int start, int length) {
                    try {
                        if (useWriter)
                            _writer.write(chars, start, length);
                        else
                            _os.write(Base64.decode(new String(chars, start, length)));

                    } catch (IOException e) {
                        throw new OXFException(e);
                    }
                }
            }));
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        }

        return fileItem;
    }

    private InternetAddress createAddress(Element addressElement) throws AddressException, UnsupportedEncodingException {
        String email = addressElement.element("email").getStringValue();
        Element nameElement = addressElement.element("name");
        return nameElement == null ? new InternetAddress(email)
                : new InternetAddress(email, nameElement.getStringValue());
    }

    private class SimpleTextDataSource implements DataSource {
        String contentType;
        String text;
        String name;

        public SimpleTextDataSource(String name, String contentType, String text) {
            this.name = name;
            this.contentType = contentType;
            this.text = text;
        }

        public String getContentType() {
            return contentType;
        }

        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(text.getBytes());
        }

        public String getName() {
            return name;
        }

        public OutputStream getOutputStream() throws IOException {
            throw new IOException("Write operation not supported");
        }
    }

    private class SimpleBinaryDataSource implements DataSource {
        String contentType;
        byte[] data;
        String name;

        public SimpleBinaryDataSource(String name, String contentType, byte[] data) {
            this.name = name;
            this.contentType = contentType;
            this.data = data;
        }

        public String getContentType() {
            return contentType;
        }

        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(data);
        }

        public String getName() {
            return name;
        }

        public OutputStream getOutputStream() throws IOException {
            throw new IOException("Write operation not supported");
        }
    }

    private SAXSource getSAXSource(Processor processor, PipelineContext pipelineContext, String href, String base, String contentType) {
        try {
            // There are two cases:
            // 1. We read the source as SAX
            //    o This is required when reading from a processor input; in this case, we behave like
            //      the inline case
            //    o When reading from another type of URI, the resource could be in theory any type
            //      of file.
            // 2. We don't read the source as SAX
            //    o It is particularly useful to support this when resources are to be used as binary
            //      attachments such as images.
            //    o Here, we consider that the source can be XML, text/html, text/*,
            //      or binary. We do not handle reading Base64-encoded files. We leverage the URL
            //      generator to obtain the content in XML format.
            XMLReader xmlReader;
            {
                String inputName = ProcessorImpl.getProcessorInputSchemeInputName(href);
                if (inputName != null) {
                    // Resolve to input of current processor
                    xmlReader = new ProcessorOutputXMLReader(pipelineContext, processor.getInputByName(inputName).getOutput());
                } else {
                    // Resolve to regular URI
                    Processor urlGenerator = (contentType == null)
                            ? new URLGenerator(URLFactory.createURL(base, href))
                            : new URLGenerator(URLFactory.createURL(base, href), contentType, true);
                    xmlReader = new ProcessorOutputXMLReader(pipelineContext, urlGenerator.createOutput(ProcessorImpl.OUTPUT_DATA));
                }
            }

            // Return SAX Source based on XML Reader
            SAXSource saxSource = new SAXSource(xmlReader, new InputSource());
            saxSource.setSystemId(href);
            return saxSource;
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }
}

                    // Set content-transfer-encoding header
//                    final String contentTransferEncoding = partElement.attributeValue("content-transfer-encoding");
//
//                    MimeBodyPart part = new MimeBodyPart() {
//                        protected void updateHeaders() throws MessagingException {
//                            super.updateHeaders();
//                            if (contentTransferEncoding != null)
//                                setHeader("Content-Transfer-Encoding", contentTransferEncoding);
//                        }
//                    };
//                    // Set content-disposition header
//                    String contentDisposition = partElement.attributeValue("content-disposition");
//                    if (contentDisposition != null)
//                        part.setDisposition(contentDisposition);
//
//                    part.setDataHandler(new DataHandler(new SimpleTextDataSource(name, contentType, content)));
//                    mimeMultipart.addBodyPart(part);
