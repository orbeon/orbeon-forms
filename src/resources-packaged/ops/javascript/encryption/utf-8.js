
    /*	Encoding and decoding of Unicode character strings as
    	UTF-8 byte streams.  */
	
    //	UNICODE_TO_UTF8  --  Encode Unicode argument string as UTF-8 return value

    function unicode_to_utf8(s) {
	var utf8 = "";
	
	for (var n = 0; n < s.length; n++) {
            var c = s.charCodeAt(n);

            if (c <= 0x7F) {
	    	//  0x00 - 0x7F:  Emit as single byte, unchanged
        	utf8 += String.fromCharCode(c);
            } else if ((c >= 0x80) && (c <= 0x7FF)) {
	    	//  0x80 - 0x7FF:  Output as two byte code, 0xC0 in first byte
		//  	    	    	    	    	    0x80 in second byte
        	utf8 += String.fromCharCode((c >> 6) | 0xC0);
        	utf8 += String.fromCharCode((c & 0x3F) | 0x80);
            } else {
	    	// 0x800 - 0xFFFF:  Output as three bytes, 0xE0 in first byte
		//  	    	    	    	    	   0x80 in second byte
		//  	    	    	    	    	   0x80 in third byte
        	utf8 += String.fromCharCode((c >> 12) | 0xE0);
        	utf8 += String.fromCharCode(((c >> 6) & 0x3F) | 0x80);
        	utf8 += String.fromCharCode((c & 0x3F) | 0x80);
            }
	}
	return utf8;
    }

    //	UTF8_TO_UNICODE  --  Decode UTF-8 argument into Unicode string return value

    function utf8_to_unicode(utf8) {
	var s = "", i = 0, b1, b2, b2;

	while (i < utf8.length) {
            b1 = utf8.charCodeAt(i);
            if (b1 < 0x80) {	    // One byte code: 0x00 0x7F
        	s += String.fromCharCode(b1);
        	i++;
            } else if((b1 >= 0xC0) && (b1 < 0xE0)) {	// Two byte code: 0x80 - 0x7FF
        	b2 = utf8.charCodeAt(i + 1);
        	s += String.fromCharCode(((b1 & 0x1F) << 6) | (b2 & 0x3F));
        	i += 2;
            } else {	    	    // Three byte code: 0x800 - 0xFFFF
        	b2 = utf8.charCodeAt(i + 1);
		b3 = utf8.charCodeAt(i + 2);
        	s += String.fromCharCode(((b1 & 0xF) << 12) |
		    	    	    	 ((b2 & 0x3F) << 6) |
					 (b3 & 0x3F));
        	i += 3;
            }
	}
	return s;
    }

    /*	ENCODE_UTF8  --  Encode string as UTF8 only if it contains
			 a character of 0x9D (Unicode OPERATING
			 SYSTEM COMMAND) or a character greater
			 than 0xFF.  This permits all strings
			 consisting exclusively of 8 bit
			 graphic characters to be encoded as
			 themselves.  We choose 0x9D as the sentinel
			 character as opposed to one of the more
			 logical PRIVATE USE characters because 0x9D
			 is not overloaded by the regrettable
			 "Windows-1252" character set.  Now such characters
			 don't belong in JavaScript strings, but you never
			 know what somebody is going to paste into a
			 text box, so this choice keeps Windows-encoded
			 strings from bloating to UTF-8 encoding.  */
			 
    function encode_utf8(s) {
    	var i, necessary = false;
	
	for (i = 0; i < s.length; i++) {
	    if ((s.charCodeAt(i) == 0x9D) ||
	    	(s.charCodeAt(i) > 0xFF)) {
	    	necessary = true;
		break;
	    }
	}
	if (!necessary) {
	    return s;
	}
	return String.fromCharCode(0x9D) + unicode_to_utf8(s);
    }
    
    /*  DECODE_UTF8  --  Decode a string encoded with encode_utf8
			 above.  If the string begins with the
			 sentinel character 0x9D (OPERATING
			 SYSTEM COMMAND), then we decode the
			 balance as a UTF-8 stream.  Otherwise,
			 the string is output unchanged, as
			 it's guaranteed to contain only 8 bit
			 characters excluding 0x9D.  */
			 
    function decode_utf8(s) {
    	if ((s.length > 0) && (s.charCodeAt(0) == 0x9D)) {
	    return utf8_to_unicode(s.substring(1));
	}
	return s;
    }
