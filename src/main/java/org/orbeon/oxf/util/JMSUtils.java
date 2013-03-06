/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.util;

import org.orbeon.oxf.common.OXFException;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class JMSUtils {

    public static final String JNDI_SERVICE_PREFIX = "ois.bus.service.";

    /**
     * Returns a new queue connection. The queue connection must be
     * closed by the caller.
     */
    public static QueueConnection getQueueConnection() throws JMSException {
        try {
            final QueueConnectionFactory queueConnectionFactory = (QueueConnectionFactory)
                    new InitialContext().lookup("weblogic.jms.ConnectionFactory");
            return queueConnectionFactory.createQueueConnection();
        } catch (NamingException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Creates a new queue in JMS server if a queue with the given
     * name does not already exist.
     */
    public static Queue createQueue(String jndiName) {
        try {
            InitialContext initialContext = new InitialContext();
            try {
                // See if the queue already exists
                return (Queue) initialContext.lookup(jndiName);
            } catch (NamingException e) {
                // If it does not exist, create it
                Class jmsHelper = Class.forName("weblogic.jms.extensions.JMSHelper");
                Method createPermanentQueueAsync = jmsHelper.getMethod("createPermanentQueueAsync",
                        new Class[] {Context.class, String.class,String.class, String.class});
                createPermanentQueueAsync.invoke(null, new Object[] {initialContext,
                        "WSStoreForwardInternalJMSServermyserver", jndiName, jndiName});
                return (Queue) initialContext.lookup(jndiName);
            }
        } catch (IllegalAccessException e) {
            throw new OXFException(e);
        } catch (InvocationTargetException e) {
            throw new OXFException(e);
        } catch (NoSuchMethodException e) {
            throw new OXFException(e);
        } catch (ClassNotFoundException e) {
            throw new OXFException(e);
        } catch (NamingException e) {
            throw new OXFException(e);
        }
    }
}
