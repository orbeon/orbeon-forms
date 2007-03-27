function actb(obj,ca,no_filter) {

    var actb_self = obj;
    actb_self.actb_keywords = ca;
    actb_self.actb_no_filter = no_filter;
	/* ---- Public Variables ---- */
	actb_self.actb_timeOut = -1; // Autocomplete Timeout in ms (-1: autocomplete never time out)
	actb_self.actb_lim = -1;    // Number of elements autocomplete can show (-1: no limit)
	actb_self.actb_firstText = true; // should the auto complete be limited to the beginning of keyword?
	actb_self.actb_mouse = true; // Enable Mouse Support
	actb_self.actb_delimiter = new Array();  // Delimiter for multiple autocomplete. Set it to empty array for single autocomplete
	actb_self.actb_startcheck = 0; // Show widget only after this number of characters is typed in.
	/* ---- Public Variables ---- */

	/* --- Styles --- */
	actb_self.actb_bgColor = '#fff';    // Background when line is NOT selected
    actb_self.actb_hColor = '#36c';     // Background when line IS selected
    actb_self.actb_textColor = '#000';  // Text color when line is NOT selected
    actb_self.actb_hTextColor = '#fff'; // Text color when line IS selected
	actb_self.actb_fFamily = 'Arial,Helvetica,Geneva,sans-serif';
	actb_self.actb_fSize = '9pt';
	actb_self.actb_hStyle = '';//'text-decoration:underline;font-weight="bold"';
	/* --- Styles --- */

	/* ---- Private Variables ---- */
    var actb_table_id = obj.parentNode.id + "-tat_table";
    var actb_tr_id = obj.parentNode.id + "-tat_tr";
    var actb_td_id = obj.parentNode.id + "-tat_td";
    var actb_delimwords = new Array();
	var actb_cdelimword = 0;
	var actb_delimchar = new Array();
	var actb_display = false;
	var actb_pos = 0;
	var actb_total = 0;
	var actb_curr = obj;
	var actb_rangeu = 0;
	var actb_ranged = 0;
	var actb_bool = new Array();
	var actb_pre = 0;
	var actb_toid;
	var actb_tomake = false;
	var actb_getpre = "";
	var actb_mouse_on_list = 1;
	var actb_kwcount = 0;
	var actb_caretmove = false;
	/* ---- Private Variables---- */

	addEvent(actb_curr,"focus",actb_setup);
	function actb_setup(){
		addEvent(document,"keydown",actb_checkkey);
		addEvent(actb_curr,"blur",actb_clear);
		addEvent(document,"keypress",actb_keypress);
	}

	function actb_clear(evt){
        if (!evt) evt = event;
        removeEvent(document,"keydown",actb_checkkey);
		removeEvent(actb_curr,"blur",actb_clear);
		removeEvent(document,"keypress",actb_keypress);
        // avernet: Do not remove the table right away, otherwise we might miss a click event on the table
        setTimeout(actb_removedisp, 100);
	}
    function actb_parse(n){
		if (actb_self.actb_delimiter.length > 0){
			var t = actb_delimwords[actb_cdelimword].trim().addslashes();
			var plen = actb_delimwords[actb_cdelimword].trim().length;
		}else{
			var t = actb_curr.value.addslashes();
			var plen = actb_curr.value.length;
		}
		var tobuild = '';
		var i;

        if (actb_self.actb_no_filter) {
            var re = new RegExp("");
        } else if (actb_self.actb_firstText) {
			var re = new RegExp("^" + t, "i");
		}else{
			var re = new RegExp(t, "i");
		}
		var p = n.search(re);

		for (i=0;i<p;i++){
			tobuild += n.substr(i,1);
		}
		//tobuild += "<font style='"+(actb_self.actb_hStyle)+"'>"
		for (i=p;i<plen+p;i++){
			tobuild += n.substr(i,1);
		}
		//tobuild += "</font>";
			for (i=plen+p;i<n.length;i++){
			tobuild += n.substr(i,1);
		}
		return tobuild;
	}
	function actb_generate(){
        if (document.getElementById(actb_table_id)){ actb_display = false;document.body.removeChild(document.getElementById(actb_table_id)); }
		if (actb_kwcount == 0){
			actb_display = false;
			return;
		}
        // Generate table
        a = document.createElement('table');
		a.cellSpacing='0px';
		a.cellPadding='0px';
		a.style.position= 'absolute';
        a.style.zIndex= 999;
        a.style.backgroundColor=actb_self.actb_bgColor;
		a.style.color = actb_self.actb_textColor;
        a.style.border = "1px solid black";
        a.id = actb_table_id;
        document.body.appendChild(a);
//        a.style.left = (inputPosition[0] - 2) + "px";
//        a.style.top = (inputPosition[1] + actb_curr.offsetHeight - 12) + "px";
		var i;
		var first = true;
		var j = 1;
		if (actb_self.actb_mouse){
			a.onmouseout = actb_table_unfocus;
			a.onmouseover = actb_table_focus;
		}
		var counter = 0;
		for (i=0;i<actb_self.actb_keywords.length;i++){
			if (actb_bool[i]){
				counter++;
				r = a.insertRow(-1);
				if (first && !actb_tomake){
					r.style.backgroundColor = actb_self.actb_hColor;
					r.style.color = actb_self.actb_hTextColor;
					first = false;
					actb_pos = counter;
				}else if(actb_pre == i){
					r.style.backgroundColor = actb_self.actb_hColor;
                    r.style.color = actb_self.actb_hTextColor;
					first = false;
					actb_pos = counter;
				}else{
					r.style.backgroundColor = actb_self.actb_bgColor;
					r.style.color = actb_self.actb_textColor;
				}
				r.id = actb_tr_id+(j);
                // Generate cell
                c = r.insertCell(-1);
				c.style.fontFamily = actb_self.actb_fFamily;
				c.style.fontSize = actb_self.actb_fSize;
                c.style.padding = '2px 2px 2px 2px';
				c.innerHTML = actb_parse(actb_self.actb_keywords[i]);
				c.id = actb_td_id+(j);
				c.setAttribute('pos',j);
				if (actb_self.actb_mouse){
					c.style.cursor = 'pointer';
					c.onclick=actb_mouseclick;
					c.onmouseover = actb_table_highlight;
				}
				j++;
			}
			if (j - 1 == actb_self.actb_lim && j < actb_total){
				r = a.insertRow(-1);
				r.style.backgroundColor = actb_self.actb_bgColor;
				r.style.color = actb_self.actb_textColor;
				c = r.insertCell(-1);
				c.style.fontFamily = 'arial narrow';
				c.style.fontSize = actb_self.actb_fSize;
				c.align='center';
				replaceHTML(c,'\\/');
				if (actb_self.actb_mouse){
					c.style.cursor = 'pointer';
					c.onclick = actb_mouse_down;
				}
				break;
			}
		}

        // avernet: Make it so the table is at least of the same width as the text field
        if (a.offsetWidth < actb_self.offsetWidth)
            a.width = actb_self.offsetWidth;

        // avernet: Perform position relative to input field
        var listPosition = YAHOO.util.Dom.getXY(actb_curr);
        listPosition[1] += actb_curr.offsetHeight;
        YAHOO.util.Dom.setXY(a, listPosition);

        actb_rangeu = 1;
		actb_ranged = j-1;
		actb_display = true;
		if (actb_pos <= 0) actb_pos = 1;
	}
	function actb_remake(){
		document.body.removeChild(document.getElementById(actb_table_id));
        a = document.createElement('table');
		a.cellSpacing='1px';
		a.cellPadding='2px';
		a.style.position='absolute';
		a.style.top = eval(curTop(actb_curr) + actb_curr.offsetHeight) + "px";
		a.style.left = curLeft(actb_curr) + "px";
		a.style.backgroundColor=actb_self.actb_bgColor;
		a.style.color = actb_self.actb_textColor;
		a.id = actb_table_id;
		if (actb_self.actb_mouse){
			a.onmouseout= actb_table_unfocus;
			a.onmouseover=actb_table_focus;
		}
		document.body.appendChild(a);
		var i;
		var first = true;
		var j = 1;
		if (actb_rangeu > 1){
			r = a.insertRow(-1);
			r.style.backgroundColor = actb_self.actb_bgColor;
			r.style.color = actb_self.actb_textColor;
			c = r.insertCell(-1);
			c.style.fontFamily = 'arial narrow';
			c.style.fontSize = actb_self.actb_fSize;
			c.align='center';
			replaceHTML(c,'/\\');
			if (actb_self.actb_mouse){
				c.style.cursor = 'pointer';
				c.onclick = actb_mouse_up;
			}
		}
		for (i=0;i<actb_self.actb_keywords.length;i++){
			if (actb_bool[i]){
				if (j >= actb_rangeu && j <= actb_ranged){
					r = a.insertRow(-1);
					r.style.backgroundColor = actb_self.actb_bgColor;
					r.style.color = actb_self.actb_textColor;
					r.id = actb_tr_id+(j);
					c = r.insertCell(-1);
					c.style.fontFamily = actb_self.actb_fFamily;
					c.style.fontSize = actb_self.actb_fSize;
					c.innerHTML = actb_parse(actb_self.actb_keywords[i]);
					c.id = actb_td_id+(j);
					c.setAttribute('pos',j);
					if (actb_self.actb_mouse){
						c.style.cursor = 'pointer';
                        c.onclick=actb_mouseclick;
						c.onmouseover = actb_table_highlight;
					}
					j++;
				}else{
					j++;
				}
			}
			if (j > actb_ranged) break;
		}
		if (j-1 < actb_total){
			r = a.insertRow(-1);
			r.style.backgroundColor = actb_self.actb_bgColor;
			r.style.color = actb_self.actb_textColor;
			c = r.insertCell(-1);
			c.style.fontFamily = 'arial narrow';
			c.style.fontSize = actb_self.actb_fSize;
			c.align='center';
			replaceHTML(c,'\\/');
			if (actb_self.actb_mouse){
				c.style.cursor = 'pointer';
				c.onclick = actb_mouse_down;
			}
		}
	}
	function actb_goup(){
		if (!actb_display) return;
		if (actb_pos == 1) return;
		document.getElementById(actb_tr_id+actb_pos).style.backgroundColor = actb_self.actb_bgColor;
		document.getElementById(actb_tr_id+actb_pos).style.color = actb_self.actb_textColor;
		actb_pos--;
		if (actb_pos < actb_rangeu) actb_moveup();
		document.getElementById(actb_tr_id+actb_pos).style.backgroundColor = actb_self.actb_hColor;
		document.getElementById(actb_tr_id+actb_pos).style.color = actb_self.actb_hTextColor;
		if (actb_toid) clearTimeout(actb_toid);
		if (actb_self.actb_timeOut > 0) actb_toid = setTimeout(function(){actb_mouse_on_list=0;actb_removedisp();},actb_self.actb_timeOut);
	}
	function actb_godown(){
		if (!actb_display) {
            actb_tocomplete(40);
            return;
        }
        if (actb_pos == actb_total) return;
		document.getElementById(actb_tr_id+actb_pos).style.backgroundColor = actb_self.actb_bgColor;
		document.getElementById(actb_tr_id+actb_pos).style.color = actb_self.actb_textColor;
		actb_pos++;
		if (actb_pos > actb_ranged) actb_movedown();
		document.getElementById(actb_tr_id+actb_pos).style.backgroundColor = actb_self.actb_hColor;
		document.getElementById(actb_tr_id+actb_pos).style.color = actb_self.actb_hTextColor;
		if (actb_toid) clearTimeout(actb_toid);
		if (actb_self.actb_timeOut > 0) actb_toid = setTimeout(function(){actb_mouse_on_list=0;actb_removedisp();},actb_self.actb_timeOut);
	}
	function actb_movedown(){
		actb_rangeu++;
		actb_ranged++;
		actb_remake();
	}
	function actb_moveup(){
		actb_rangeu--;
		actb_ranged--;
		actb_remake();
	}

	/* Mouse */
	function actb_mouse_down(){
        document.getElementById(actb_tr_id+actb_pos).style.backgroundColor = actb_self.actb_bgColor;
		document.getElementById(actb_tr_id+actb_pos).style.color = actb_self.actb_textColor;
		actb_pos++;
		actb_movedown();
		document.getElementById(actb_tr_id+actb_pos).style.backgroundColor = actb_self.actb_hColor;
		document.getElementById(actb_tr_id+actb_pos).style.color = actb_self.actb_hTextColor;
		actb_curr.focus();
		actb_mouse_on_list = 0;
		if (actb_toid) clearTimeout(actb_toid);
		if (actb_self.actb_timeOut > 0) actb_toid = setTimeout(function(){actb_mouse_on_list=0;actb_removedisp();},actb_self.actb_timeOut);
	}
	function actb_mouse_up(evt){
        if (!evt) evt = event;
		if (evt.stopPropagation){
			evt.stopPropagation();
		}else{
			evt.cancelBubble = true;
		}
		document.getElementById(actb_tr_id+actb_pos).style.backgroundColor = actb_self.actb_bgColor;
		document.getElementById(actb_tr_id+actb_pos).style.color = actb_self.actb_textColor;
		actb_pos--;
		actb_moveup();
		document.getElementById(actb_tr_id+actb_pos).style.backgroundColor = actb_self.actb_hColor;
		document.getElementById(actb_tr_id+actb_pos).style.color = actb_self.actb_hTextColor;
		actb_curr.focus();
		actb_mouse_on_list = 0;
		if (actb_toid) clearTimeout(actb_toid);
		if (actb_self.actb_timeOut > 0) actb_toid = setTimeout(function(){actb_mouse_on_list=0;actb_removedisp();},actb_self.actb_timeOut);
	}
	function actb_mouseclick(evt){
		if (!evt) evt = event;
		if (!actb_display) return;
		actb_mouse_on_list = 0;
		actb_pos = this.getAttribute('pos');
		actb_penter();
        // avernet: Notify the XForms code that the value has changed
        xformsHandleAutoCompleteMouseChange(actb_self);
    }
	function actb_table_focus(){
		actb_mouse_on_list = 1;
	}
	function actb_table_unfocus(){
		actb_mouse_on_list = 0;
		if (actb_toid) clearTimeout(actb_toid);
		if (actb_self.actb_timeOut > 0) actb_toid = setTimeout(function(){actb_mouse_on_list = 0;actb_removedisp();},actb_self.actb_timeOut);
	}
	function actb_table_highlight(){
        actb_mouse_on_list = 1;
		document.getElementById(actb_tr_id+actb_pos).style.backgroundColor = actb_self.actb_bgColor;
		document.getElementById(actb_tr_id+actb_pos).style.color = actb_self.actb_textColor;
		actb_pos = this.getAttribute('pos');
		while (actb_pos < actb_rangeu) actb_moveup();
		while (actb_pos > actb_ranged) actb_movedown();
		document.getElementById(actb_tr_id+actb_pos).style.backgroundColor = actb_self.actb_hColor;
		document.getElementById(actb_tr_id+actb_pos).style.color = actb_self.actb_hTextColor;
		if (actb_toid) clearTimeout(actb_toid);
		if (actb_self.actb_timeOut > 0) actb_toid = setTimeout(function(){actb_mouse_on_list = 0;actb_removedisp();},actb_self.actb_timeOut);
	}
	/* ---- */

	function actb_insertword(a){
        if (actb_self.actb_delimiter.length > 0){
			str = '';
			l=0;
			for (i=0;i<actb_delimwords.length;i++){
				if (actb_cdelimword == i){
					prespace = postspace = '';
					gotbreak = false;
					for (j=0;j<actb_delimwords[i].length;++j){
						if (actb_delimwords[i].charAt(j) != ' '){
							gotbreak = true;
							break;
						}
						prespace += ' ';
					}
					for (j=actb_delimwords[i].length-1;j>=0;--j){
						if (actb_delimwords[i].charAt(j) != ' ') break;
						postspace += ' ';
					}
					str += prespace;
					str += a;
					l = str.length;
					if (gotbreak) str += postspace;
				}else{
					str += actb_delimwords[i];
				}
				if (i != actb_delimwords.length - 1){
					str += actb_delimchar[i];
				}
			}
			actb_curr.value = str;
			setCaret(actb_curr,l);
		}else{
			actb_curr.value = a;
		}
		actb_mouse_on_list = 0;
		actb_removedisp();
	}
	function actb_penter(){
        if (!actb_display) return;
		actb_display = false;
		var word = '';
		var c = 0;
		for (var i=0;i<=actb_self.actb_keywords.length;i++){
			if (actb_bool[i]) c++;
			if (c == actb_pos){
				word = actb_self.actb_keywords[i];
				break;
			}
		}
		actb_insertword(word);
		l = getCaretStart(actb_curr);
	}
	function actb_removedisp(){
        // avernet: Ignore the actb_mouse_on_list which does not seem to be accurate
        if (true || actb_mouse_on_list==0){
			actb_display = 0;
            if (document.getElementById(actb_table_id)){ document.body.removeChild(document.getElementById(actb_table_id)); }
			if (actb_toid) clearTimeout(actb_toid);
		}
	}
    function actb_keypress(e){
        if (actb_caretmove) stopEvent(e);
		return !actb_caretmove;
	}
	function actb_checkkey(evt){
        if (!evt) evt = event;
		a = evt.keyCode;
		caret_pos_start = getCaretStart(actb_curr);
		actb_caretmove = 0;
		switch (a){
			case 38:
				actb_goup();
				actb_caretmove = 1;
				return false;
				break;
			case 40:
                actb_godown();
				actb_caretmove = 1;
				return false;
				break;
			case 13: case 9:
				if (actb_display){
					actb_caretmove = 1;
					actb_penter();
					return false;
				}else{
					return true;
				}
				break;
            default:
				setTimeout(function(){actb_tocomplete(a)},50);
				break;
		}
	}

	function actb_tocomplete(kc){
        if (kc == 27 || kc == 37 || kc == 39 || kc == 9) {
            actb_removedisp();
            return;
        }
        if (kc == 38 || kc == 13 || kc == 18 || kc == 16 || kc == 17 || kc == 20) return;
        var i;
		if (actb_display){
			var word = 0;
			var c = 0;
			for (var i=0;i<=actb_self.actb_keywords.length;i++){
				if (actb_bool[i]) c++;
				if (c == actb_pos){
					word = i;
					break;
				}
			}
			actb_pre = word;
		}else{ actb_pre = -1};
		
		if (actb_curr.value == '' && kc != 40){
			actb_mouse_on_list = 0;
			actb_removedisp();
			return;
		}
		if (actb_self.actb_delimiter.length > 0){
			caret_pos_start = getCaretStart(actb_curr);
			caret_pos_end = getCaretEnd(actb_curr);
			
			delim_split = '';
			for (i=0;i<actb_self.actb_delimiter.length;i++){
				delim_split += actb_self.actb_delimiter[i];
			}
			delim_split = delim_split.addslashes();
			delim_split_rx = new RegExp("(["+delim_split+"])");
			c = 0;
			actb_delimwords = new Array();
			actb_delimwords[0] = '';
			for (i=0,j=actb_curr.value.length;i<actb_curr.value.length;i++,j--){
				if (actb_curr.value.substr(i,j).search(delim_split_rx) == 0){
					ma = actb_curr.value.substr(i,j).match(delim_split_rx);
					actb_delimchar[c] = ma[1];
					c++;
					actb_delimwords[c] = '';
				}else{
					actb_delimwords[c] += actb_curr.value.charAt(i);
				}
			}

			var l = 0;
			actb_cdelimword = -1;
			for (i=0;i<actb_delimwords.length;i++){
				if (caret_pos_end >= l && caret_pos_end <= l + actb_delimwords[i].length){
					actb_cdelimword = i;
				}
				l+=actb_delimwords[i].length + 1;
			}
			var ot = actb_delimwords[actb_cdelimword].trim(); 
			var t = actb_delimwords[actb_cdelimword].addslashes().trim();
		}else{
			var ot = actb_curr.value;
			var t = actb_curr.value.addslashes();
		}
		if (ot.length == 0 && kc != 40){
            actb_mouse_on_list = 0;
			actb_removedisp();
		}
		if (ot.length < actb_self.actb_startcheck) return this;
        if (actb_self.actb_no_filter) {
            var re = new RegExp("");
		} else if (actb_self.actb_firstText){
			var re = new RegExp("^" + t, "i");
		}else{
			var re = new RegExp(t, "i");
		}

		actb_total = 0;
		actb_tomake = false;
		actb_kwcount = 0;
		for (i=0;i<actb_self.actb_keywords.length;i++){
			actb_bool[i] = false;
			if (re.test(actb_self.actb_keywords[i])){
				actb_total++;
				actb_bool[i] = true;
				actb_kwcount++;
				if (actb_pre == i) actb_tomake = true;
			}
		}

		if (actb_toid) clearTimeout(actb_toid);
		if (actb_self.actb_timeOut > 0) actb_toid = setTimeout(function(){actb_mouse_on_list = 0;actb_removedisp();},actb_self.actb_timeOut);
		actb_generate();
	}
    return actb_tocomplete;
}