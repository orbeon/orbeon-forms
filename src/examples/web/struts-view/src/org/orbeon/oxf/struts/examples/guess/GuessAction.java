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
package org.orbeon.oxf.struts.examples.guess;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.MessageResources;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Locale;

public class GuessAction extends Action {

    private static final Logger logger = Logger.getLogger(GuessAction.class.getName());

    public static final String HINT_KEY = "guess-hint";

    public ActionForward perform(ActionMapping actionMapping,
                                 ActionForm actionForm,
                                 HttpServletRequest request,
                                 HttpServletResponse response) throws IOException, ServletException {

        GuessForm guess = (GuessForm)actionForm;
        MessageResources messages = getResources(request);
        Locale locale = getLocale(request);

        Integer n = (Integer)request.getSession().getAttribute("number");
        if(n == null) {
            n = new Integer(new Long(Math.round(Math.random() * 100) + 1).intValue());
            request.getSession(true).setAttribute("number", n);
            guess.setMessage(messages.getMessage(locale, "guess.welcome"));

            HintBean hint = new HintBean(n.intValue());
            request.getSession(true).setAttribute(HINT_KEY, hint);
        } else {
            Integer g = new Integer(guess.getUserGuess());
            Object[] objs = {g};
            int comp = n.compareTo(g);
            if(comp == 0) {
                request.getSession().setAttribute("number", null);
                return actionMapping.findForward("success");
            }else if(comp < 0 )
                guess.setMessage(messages.getMessage(locale, "guess.too-big", objs));
            else
                guess.setMessage(messages.getMessage(locale, "guess.too-small", objs));
        }
        return actionMapping.findForward("retry");

    }


    public static class HintBean {
        private int hint;

        public HintBean(int hint) {
            this.hint = hint;
        }

        public int getHint() {
            return hint;
        }

        public void setHint(int hint) {
            this.hint = hint;
        }
    }


}
