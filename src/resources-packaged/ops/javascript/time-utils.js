// *** the next field you want to auto forward to and the**
// *** length of the activefield when auto forward ********
function movebox(activefield,nextfield,maxlen)
{
  var activefieldlen = activefield.value.length;

   if (activefieldlen == maxlen) {
    nextfield.focus();
   }
}

function updateTimeField(timeobj, hobj, mobj, sobj) {
  timeobj.value = hobj.value + ':' + mobj.value + ':' + sobj.value;
}

function updateComponents(timeobj, hobj, mobj, sobj) {
   var tokenizer =  new StringTokenizer(timeobj.value, ":");
   if(tokenizer.hasMoreTokens()) 
   		hobj.value = tokenizer.nextToken();
   if(tokenizer.hasMoreTokens()) 
  		mobj.value = tokenizer.nextToken();
   if(tokenizer.hasMoreTokens()) 
   		sobj.value = tokenizer.nextToken();
   
}

function upRange(mynum,upr)
{
  var un = mynum.value;
  if (un > upr)
   return false;
  else
   return true;
}


function checkHour(hobj) {

  if (isNaN(hobj.value)) {
     alert("The Hour field can only contain numbers\n You entered: " + hobj.value);
     hobj.value = "00";
    }


     if (hobj.value.length == 2)    {
       if (!upRange(hobj,23))   {
         alert("The hour field can only contain numbers between 0 and 23");
         hobj.value = 23;
         hobj.focus();
        }

      } 
} 

function checkMinute(mobj) {

  if (isNaN(mobj.value))
    {
     alert("The Minute field can only contain numbers\n You entered: " + mobj.value);
     mobj.value = "00";
    }

    if (mobj.value.length == 2)   {
       if (!upRange(mobj,59))  {
         alert("The minute field can only contain numbers between 0 and 59");
         mobj.value = 59;
         mobj.focus();
        }

      } 
}


function checkSecond(sobj) {

  if (isNaN(sobj.value))
    {
     alert("The Second field can only contain numbers\n You entered: " + sobj.value);
     sobj.value = "00";
    }

     if (sobj.value.length == 2)   {
       if (!upRange(sobj,59))   {
         alert("The second field can only contain numbers between 0 and 59");
         sobj.value = 59;
         sobj.focus();
        }

      } 
}


/*
   Constructor.
   Split up a material string based upong the separator.

   Param    -  material, the String to be split up.
   Param    -  separator, the String to look for within material. Should be
               something like "," or ".", not a regular expression.

*/
function StringTokenizer (material, separator)
{
   // Attributes.
   this.material = material;
   this.separator = separator;

   // Operations.
   this.getTokens = getTokens;
   this.nextToken = nextToken;
   this.countTokens = countTokens;
   this.hasMoreTokens = hasMoreTokens;
   this.tokensReturned = tokensReturned;

   // Initialisation code.
   this.tokens = this.getTokens();
   this.tokensReturned = 0;

}  // end constructor




/*
   Go through material, putting each token into a new array.

   Return      - the array with all the tokens in it.
*/
function getTokens()
{
   // Create array of tokens.
   var tokens = new Array();
   var nextToken;

   // If no separators found, single token is the material string itself.
	if (this.material.indexOf (this.separator) < 0)
	{
		tokens [0] = this.material;
		return tokens;
	}  // end if

   // Establish initial start and end positions of the first token.
   start = 0;
   end = this.material.indexOf (this.separator, start);

   // Counter for how many tokens were found.
   var counter = 0;

   // Go through material, token at a time.
   var trimmed;
 	while (this.material.length - start >= 1)
	{
		nextToken = this.material.substring (start, end);
		start = end + 1;
		if (this.material.indexOf (this.separator, start + 1) < 0)
		{
			end = this.material.length;
		}  // end if
		else
		{
			end = this.material.indexOf (this.separator, start + 1);
		}  // end else

      trimmed = trim (nextToken);

      // Remove any extra separators at start.
      while (trimmed.substring(0, this.separator.length) == this.separator) {
         trimmed = trimmed.substring (this.separator.length);
      }
      trimmed = trim(trimmed);
      if (trimmed == "") {
         continue;
      }
      tokens [counter] = trimmed;
		counter ++;
	}   // end if

   // Return the initialised array.
   return tokens;


}  // end getTokens function


/*
   Return a count of the number of tokens in the material.

   Return      - int number of tokens in material.
*/
function countTokens()
{
  return this.tokens.length;
}  // end countTokens function



/*
   Get next token in material.

   Return      - next token in material.
*/
function nextToken()
{

   if (this.tokensReturned >= this.tokens.length)
   {
      return null;
   }  // end if
   else
   {
      var returnToken = this.tokens [this.tokensReturned];
      this.tokensReturned ++;
      return returnToken;
   }  // end else

}  // end nextToken function



/*
   Tests if there are more tokens available from this tokenizer's string. If
   this method returns true, then a subsequent call to nextToken
   will successfully return a token.

   Return      true if more tokens, false otherwise.
*/
function hasMoreTokens()
{
   if (this.tokensReturned < this.tokens.length)
   {
      return true;
   }  // end if
   else
   {
      return false;
   }  // end else
}  // end hasMoreTokens function

function tokensReturned()
{
   return this.tokensReturned;
}  // end tokensReturned function


function trim (strToTrim) {
   return(strToTrim.replace(/^\s+|\s+$/g, ''));
}  // end trim function



