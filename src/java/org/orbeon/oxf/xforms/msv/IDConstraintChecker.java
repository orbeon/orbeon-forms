/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
/*
 * @(#)$Id: IDConstraintChecker.java,v 1.2 2007/03/28 18:50:39 ebruchez Exp $
 *
 * Copyright 2001 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * This software is the proprietary information of Sun Microsystems, Inc.  
 * Use is subject to license terms.
 * 
 */
package org.orbeon.oxf.xforms.msv;

import com.sun.msv.grammar.xmlschema.ElementDeclExp;
import com.sun.msv.grammar.xmlschema.IdentityConstraint;
import com.sun.msv.grammar.xmlschema.KeyRefConstraint;
import com.sun.msv.util.LightStack;
import com.sun.msv.util.StartTagInfo;
import com.sun.msv.verifier.Acceptor;
import org.orbeon.oxf.xforms.ErrorInfo;
import org.relaxng.datatype.Datatype;
import org.relaxng.datatype.ValidationContext;

/**
 * Verifier with XML Schema-related enforcement.
 * 
 * <p>
 * This class can be used in the same way as {@link Verifier}.
 * This class also checks XML Schema's identity constraint.
 * 
 * @author <a href="mailto:kohsuke.kawaguchi@eng.sun.com">Kohsuke KAWAGUCHI</a>
 */
public class IDConstraintChecker implements ValidationContext {

    
    
    /** active mathcers. */
    protected final java.util.ArrayList matchers = new java.util.ArrayList();
    
    protected void add( Matcher matcher ) {
        matchers.add(matcher);
    }
    protected void remove( Matcher matcher ) {
        matchers.remove(matcher);
    }
    
    /**
     * a map from <code>SelectorMatcher</code> to set of <code>KeyValue</code>s.
     * 
     * One SelectorMatcher correponds to one scope of the identity constraint.
     */
    private final java.util.Map keyValues = new java.util.HashMap();
    
    /**
     * a map from keyref <code>SelectorMatcher</code> to key/unique
     * <code>SelectorMatcher</code>.
     * 
     * Given a keyref scope, this map stores which key scope should it refer to.
     */
    private final java.util.Map referenceScope = new java.util.HashMap();
    
    /**
     * a map from <code>IdentityConstraint</code> to a <code>LightStack</code> of
     * <code>SelectorMatcher</code>.
     * 
     * Each stack top keeps the currently active scope for the given IdentityConstraint.
     */
    private final java.util.Map activeScopes = new java.util.HashMap();
    protected SelectorMatcher getActiveScope( IdentityConstraint c ) {
        LightStack s = (LightStack)activeScopes.get(c);
        if(s==null)    return null;
        if(s.size()==0)    return null;
        return (SelectorMatcher)s.top();
    }
    protected void pushActiveScope( IdentityConstraint c, SelectorMatcher matcher ) {
        LightStack s = (LightStack)activeScopes.get(c);
        if(s==null)
            activeScopes.put(c,s=new LightStack());
        s.push(matcher);
    }
    protected void popActiveScope( IdentityConstraint c, SelectorMatcher matcher ) {
        LightStack s = (LightStack)activeScopes.get(c);
        if(s==null)
            // since it's trying to pop, there must be a non-empty stack.
            throw new Error();
        if(s.pop()!=matcher)
            // trying to pop a non-active scope.
            throw new Error();
    }
        
    
    /**
     * adds a new KeyValue to the value set.
     * @return true        if this is a new value.
     */
    protected boolean addKeyValue( SelectorMatcher scope, KeyValue value ) {
        java.util.Set keys = (java.util.Set)keyValues.get(scope);
        if(keys==null)
            keyValues.put(scope, keys = new java.util.HashSet());
        return keys.add(value);
    }
    /**
     * gets the all <code>KeyValue</code>s that were added within the specified scope.
     */
    protected KeyValue[] getKeyValues( SelectorMatcher scope ) {
        java.util.Set keys = (java.util.Set)keyValues.get(scope);
        if(keys==null)
            return new KeyValue[0];
        return (KeyValue[])keys.toArray(new KeyValue[keys.size()]);
    }
    
    
    
    public void startDocument()  {
        keyValues.clear();
    }
    
    public void endDocument()  {
        
        // keyref check
        java.util.Map.Entry[] scopes = (java.util.Map.Entry[])
            keyValues.entrySet().toArray(new java.util.Map.Entry[keyValues.size()]);
        if(com.sun.msv.driver.textui.Debug.debug)
            System.out.println("key/keyref check: there are "+keyValues.size()+" scope(s)");
        
        for( int i=0; i<scopes.length; i++ ) {
            final SelectorMatcher key = (SelectorMatcher)scopes[i].getKey();
            final java.util.Set value = (java.util.Set)scopes[i].getValue();
            
            if( key.idConst instanceof KeyRefConstraint ) {
                // get the set of corresponding keys.
                java.util.Set keys = (java.util.Set)keyValues.get( referenceScope.get(key) );
                KeyValue[] keyrefs = (KeyValue[])
                    value.toArray(new KeyValue[value.size()]);
                
                for( int j=0; j<keyrefs.length; j++ ) {
                    if( keys==null || !keys.contains(keyrefs[j]) )
                        // this keyref doesn't have a corresponding key.
                        reportError( keyrefs[j].element, ERR_UNDEFINED_KEY,
                            new Object[]{
                                key.idConst.namespaceURI,
                                key.idConst.localName} );
                }
            }
        }
    }
    
    public void onNextAcceptorReady( StartTagInfo sti, Acceptor next, final org.dom4j.Element elt )  {
        
        // call matchers
        int len = matchers.size();
        for( int i=0; i<len; i++ ) {
            Matcher m = (Matcher)matchers.get(i);
            m.startElement( elt );
        }
        
        // introduce newly found identity constraints.
        Object e = next.getOwnerType();
        if( e instanceof ElementDeclExp.XSElementExp ) {
            ElementDeclExp.XSElementExp exp = (ElementDeclExp.XSElementExp)e;
            if( exp.identityConstraints!=null ) {
                int m = exp.identityConstraints.size();
                for( int i=0; i<m; i++ )
                    add( new SelectorMatcher( this,
                            (IdentityConstraint)exp.identityConstraints.get(i),
                            elt ) );
                
                // SelectorMathcers will register themselves as active scopes 
                // in their constructor.
                
                // augment the referenceScope field by adding newly introduced keyrefs.
                for( int i=0; i<m; i++ ) {
                    IdentityConstraint c = (IdentityConstraint)
                        exp.identityConstraints.get(i);
                    if(c instanceof KeyRefConstraint) {
                        SelectorMatcher keyScope =
                            getActiveScope( ((KeyRefConstraint)c).key );
                        if(keyScope==null)
                            ;    // there is no active scope of the key scope now.
                        
                        referenceScope.put(
                            getActiveScope(c),
                            keyScope );
                    }
                }
            }
        }
    }

    public void feedAttribute
    ( Acceptor child, final org.dom4j.Attribute att, final Datatype[] result )  {
        
        final int len = matchers.size();
        // call matchers for attributes.
        for( int i=0; i<len; i++ ) {
            Matcher m = (Matcher)matchers.get(i);
            m.onAttribute( att, 
                (result==null || result.length==0)?null:result[0] );
        }
        
    }

    
    
    public void endElement( final org.dom4j.Element elt, final Datatype[] lastType )
                                 {
        
        // getLastCharacterType may sometimes return null. For example,
        // 1) this element should be empty and there was only whitespace characters.
        Datatype dt;
        if( lastType==null || lastType.length==0 )    dt = null;
        else                                        dt = lastType[0];
            
        // call matchers
        int len = matchers.size();
        for( int i=len-1; i>=0; i-- ) {
            // Matcher may remove itself from the vector.
            // Therefore, to make it work correctly, we have to
            // enumerate Matcher in reverse direction.
            ((Matcher)matchers.get(i)).endElement( dt );
        }
    }
    
    private java.util.LinkedList errorInfo;
    
    public ErrorInfo clearErrorInfo() {
        final ErrorInfo ret = errorInfo == null || errorInfo.size() == 0
                            ? null : ( ErrorInfo )errorInfo.removeLast();
        return ret;
    }
    
    protected void reportError( final org.dom4j.Element elt, String propKey, Object[] args )  {
        final String suffix = elt == null ? "" : " at " + elt.getUniquePath(); 
        final String msg = localizeMessage(propKey,args) + suffix;
        if ( errorInfo == null ) errorInfo = new java.util.LinkedList();
        final ErrorInfo errInf = new ErrorInfo( elt, msg );
        errorInfo.addLast( errInf );
    }
    
    public static String localizeMessage( String propertyName, Object arg ) {
        return localizeMessage( propertyName, new Object[]{arg} );
    }

    public static String localizeMessage( String propertyName, Object[] args ) {
        String format = java.util.PropertyResourceBundle.getBundle(
            "org.orbeon.oxf.xforms.msv.Messages").getString(propertyName);
        
        return java.text.MessageFormat.format(format, args );
    }
    
    public static final String ERR_UNMATCHED_KEY_FIELD =
        "IdentityConstraint.UnmatchedKeyField";    // arg :3
    public static final String ERR_NOT_UNIQUE =
        "IdentityConstraint.NotUnique"; // arg:2
    public static final String ERR_NOT_UNIQUE_DIAG =
        "IdentityConstraint.NotUnique.Diag";    // arg:2
    public static final String ERR_DOUBLE_MATCH =
        "IdentityConstraint.DoubleMatch"; // arg:3
    public static final String ERR_UNDEFINED_KEY =
        "IdentityConstraint.UndefinedKey"; // arg:2 

    public String resolveNamespacePrefix(String arg0) {
        // TODO Auto-generated method stub
        return null;
    }
    public String getBaseUri() {
        // TODO Auto-generated method stub
        return null;
    }
    public boolean isUnparsedEntity(String arg0) {
        // TODO Auto-generated method stub
        return false;
    }
    public boolean isNotation(String arg0) {
        // TODO Auto-generated method stub
        return false;
    }
    
}
