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

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.xml.sax.SAXException;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.Iterator;
import java.util.Properties;

public class EmailProcessor extends ProcessorImpl {

    public static final String EMAIL_FORCE_TO = "forceto";
    public static final String EMAIL_HOST = "host";

    public static final String EMAIL_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/email";
    public static final String DEFAULT_MULTIPART = "mixed";

    public EmailProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA, EMAIL_CONFIG_NAMESPACE_URI));
    }

    public void start(org.orbeon.oxf.pipeline.api.PipelineContext context) {
        try {
            Document dataDocument = readInputAsDOM4J(context, INPUT_DATA);
            Element messageElement = dataDocument.getRootElement();

            // Create message
            Properties properties = new Properties();
            String propHost = getPropertySet().getString(EMAIL_HOST);
            // SMTP Host from OXFProperties override the local configuration
            if (propHost != null)
                properties.setProperty("mail.smtp.host", propHost);
            else {
                String host = messageElement.element("smtp-host").getTextTrim();
                if (host != null)
                    properties.setProperty("mail.smtp.host", host);
            }
            Session session = Session.getInstance(properties);
            Message message = new MimeMessage(session);

            // Set from/to
            message.setFrom(createAddress(messageElement.element("from")));
            for (Iterator i = messageElement.elements("to").iterator(); i.hasNext();) {
                Element toElement = (Element) i.next();
                InternetAddress address = createAddress(toElement);
                String forceToEMail = getPropertySet().getString(EMAIL_FORCE_TO);
                if (forceToEMail != null)
                    address.setAddress(forceToEMail);
                message.addRecipient(Message.RecipientType.TO, address);
            }

            // Set subject
            message.setSubject(messageElement.element("subject").getStringValue());

            Element textElement = messageElement.element("text");
            Element bodyElement = messageElement.element("body");
            // simple body
            if (textElement != null)
                message.setText(textElement.getStringValue());
            else if (bodyElement != null) {
                // MIME parts
                String multipart = bodyElement.attributeValue("mime-multipart");
                if (multipart == null)
                    multipart = DEFAULT_MULTIPART;
                MimeMultipart mimeMultipart = new MimeMultipart(multipart);
                for (Iterator i = bodyElement.elementIterator("part"); i.hasNext();) {
                    Element partElement = (Element) i.next();
                    String name = partElement.attributeValue("name");
                    String contentType = partElement.attributeValue("content-type");

                    final String content;
                    if ("text/html".equals(contentType)) {
                        if (partElement.elements().size() != 1)
                            throw new ValidationException("<part> must contain one element",
                                    (LocationData) partElement.getData());
                        final StringWriter writer;
                        {
                            writer = new StringWriter();
                            TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
                            identity.getTransformer().setOutputProperty(OutputKeys.METHOD, "html");
                            identity.setResult(new StreamResult(writer));
                            LocationSAXWriter saxw = new LocationSAXWriter();
                            saxw.setContentHandler(identity);
                            Document partDocument = DocumentHelper.createDocument();
                            partDocument.setRootElement((Element) Dom4jUtils.cloneNode((Element) partElement.elements().get(0)));
                            saxw.write(partDocument);
                        }
                        content = writer.toString();
                    } else {
                        content = partElement.getStringValue();
                    }

                    MimeBodyPart part = new MimeBodyPart();
                    if (contentType != null && !contentType.startsWith("text/")) {
                        // This is not text content
                        byte[] data = XMLUtils.base64StringToByteArray(content);
                        part.setDataHandler(new DataHandler(new SimpleBinaryDataSource(name, contentType, data)));
                        mimeMultipart.addBodyPart(part);
                    } else {
                        // This is text content
                        part.setDataHandler(new DataHandler(new SimpleTextDataSource(name, contentType, content)));
                        mimeMultipart.addBodyPart(part);
                    }

                    // Set content-disposition header
                    String contentDisposition = partElement.attributeValue("content-disposition");
                    if (contentDisposition != null)
                        part.setDisposition(contentDisposition);
                }
                message.setContent(mimeMultipart);
            } else {
                throw new OXFException("text or body element not found");
            }

            // Send message
            Transport transport = session.getTransport("smtp");
            Transport.send(message);
            transport.close();
        } catch (MessagingException e) {
            throw new OXFException(e);
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
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
}
