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
package org.orbeon.oxf.test.old;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Properties;
import java.util.Date;

public class SendMail {

    public static void main(String[] args) {
        try {
            Properties props = new Properties();
            props.setProperty("mail.smtp.host", "localhost");

            Session session = Session.getInstance(props);

            MimeMultipart multi = new MimeMultipart("alternative");

            DataSource textDS = new FileDataSource("C:\\orbeon\\OIS\\orbeon\\27.txt");
            MimeBodyPart text = new MimeBodyPart();
            text.setDataHandler(new DataHandler(textDS));
            multi.addBodyPart(text);



            DataSource htmlDS = new FileDataSource("C:\\orbeon\\OIS\\orbeon\\27.html");
            MimeBodyPart html = new MimeBodyPart();
            html.setDataHandler(new DataHandler(htmlDS));

            multi.addBodyPart(html);

            Message message = new MimeMessage(session);
            message.setContent(multi);

            Address[] from = new Address[1];
            from[0] = new InternetAddress("oxf-info@orbeon.com", "PresentationServer Team");
            message.addFrom(from);


            Address to = new InternetAddress("orbeon-team@orbeon.com");
//            Address to = new InternetAddress("oxf-announce@orbeon.com");
            message.addRecipient(Message.RecipientType.TO, to);

            message.setSubject("PLEASE REVIEW : Orbeon Announces PresentationServer Server 2.7 and PresentationServer Studio 1.2");
            message.setSentDate(new Date());

            Transport transport = session.getTransport("smtp");
            Transport.send(message);
            transport.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
