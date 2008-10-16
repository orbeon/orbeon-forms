var AccordionMenu =(function()
{
	var YUD = YAHOO.util.Dom;
	var YUE = YAHOO.util.Event;
	var oMenuSetting = {};
	var oMenuCache = {};
	var dLastHoverTitle ;
	YUD.addClass(document.documentElement,'accordion-menu-js');
	
	function getDT(e)
	{
		var dEl = YUE.getTarget(e);
			
		if(	(e.tagName + '').toUpperCase()=='DD' )
		{	
			var dt = e.previousSibling ;
			while(dt)
			{
				if(dt.tagName &&  dt.tagName.toUpperCase() == 'DT'){break;};
				dt = dt.previousSibling
			};
			
			if(!dt || dt.tagName.toUpperCase() != 'DT'){return;}
			else{return (dt.tagName==='DT')?dt:null};
		}
		else if(e.clientX)
		{
			var found = false;
			while( dEl.parentNode)
			{
				if(YUD.hasClass(dEl,'a-m-t')){ found  = true ; break;};
				dEl = dEl.parentNode;
			};
			if(!found){return null}
			else{return (dEl.tagName==='DT')?dEl:null };	
		};		
	};
	
	
	
	function getDD(dt)
	{
		if(!dt){return null;};
		var dd = dt.nextSibling ;
	
		while(dd)
		{	
			if(dd.tagName && dd.tagName.toUpperCase() == 'DD'){break;};
			dd = dd.nextSibling;
			
		};
		if(!dd || dd.tagName.toUpperCase() != 'DD'){return;}
		else{return dd};
	};
	
	function expand(dl,dt,dd)
	{
		dl.hasAnimation +=1;
		YUD.addClass(dd,'a-m-d-before-expand');		
		var oAttr = {height:{from:0,to:dd.offsetHeight }};
		
		YUD.removeClass(dd,'a-m-d-before-expand');
		
		var onComplete = function()
		{	
			oAnim.onComplete.unsubscribe(onComplete);
			oAnim.stop();
			YUD.removeClass(dd,'a-m-d-anim');
			YUD.addClass(dd,'a-m-d-expand');
			onComplete = null;	
			dl.hasAnimation -=1;
			var dt = getDT(dd);	
			YUD.addClass(dt,'a-m-t-expand');
			if( oMenuCache[ dl.id ] &&  oMenuCache[ dl.id ].onOpen && dd.style.height!='' )
			{	
				oMenuCache[ dl.id ].onOpen(	 {dl:dl,dt:dt,dd:dd} );								
			};	
			dd.style.height = '';
		
		};
		
		var onTween = function()
		{
			if(dd.style.height)
			{	
				YUD.addClass(dd,'a-m-d-anim');				
				oAnim.onTween.unsubscribe(onTween);
				onTween = null;
				dd.oAnim = null;
			};
			
		};
		
		if(dd.oAnim)
		{
			dd.oAnim.stop();
			dd.oAnim = null;
			dl.hasAnimation -=1;	
		};
		var oEaseType = YAHOO.util.Easing.easeOut;
		var seconds = 0.5;
		if(oMenuCache[ dl.id ] )
		{
			oEaseType = oMenuCache[ dl.id ]['easeOut']?oEaseType:YAHOO.util.Easing.easeIn;
			seconds =  oMenuCache[ dl.id ]['seconds'];
			
			if( !oMenuCache[ dl.id ]['animation'] )
			{
				var oAnim = {onComplete:{unsubscribe:function(){}},stop:function(){}};
				onComplete();
				return;
			};
		};
		
		
		var oAnim = new YAHOO.util.Anim(dd,oAttr,seconds ,oEaseType);
		oAnim.onComplete.subscribe(onComplete);	
		oAnim.onTween.subscribe(onTween);
		oAnim.animate();
		dd.oAnim = oAnim ;
	
	};
	
	function collapse(dl,dt,dd)
	{
		dl.hasAnimation +=1;
		YUD.addClass(dd,'a-m-d-anim');
		var oAttr = {height:{from:dd.offsetHeight,to:0}};
		
		
		var onComplete = function()
		{
			oAnim.onComplete.unsubscribe(onComplete);
			YUD.removeClass(dd,'a-m-d-anim');
			YUD.removeClass(dd,'a-m-d-expand');
			dd.style.height = '';
			dd.oAnim = null;
			onComplete = null;	
			dl.hasAnimation -=1;	
			var dt = getDT(dd);	
			YUD.removeClass(dt,'a-m-t-expand');	
			if( oMenuCache[ dl.id ] &&  oMenuCache[ dl.id ].onOpen )
			{				
				oMenuCache[ dl.id ].onClose(	 {dl:dl,dt:dt,dd:dd} );
			};			
			
		};
		
		if(dd.oAnim)
		{
			dd.oAnim.stop();
			dd.oAnim = null;
			dl.hasAnimation -=1;	
		};
		
		var oEaseType = YAHOO.util.Easing.easeOut;
		var seconds = 0.5;
		if(oMenuCache[ dl.id ] )
		{
			oEaseType = oMenuCache[ dl.id ]['easeOut']?oEaseType:YAHOO.util.Easing.easeIn;
			seconds =  oMenuCache[ dl.id ]['seconds'];
			if( !oMenuCache[ dl.id ]['animation'] )
			{
				var oAnim = {onComplete:{unsubscribe:function(){}},stop:function(){}};
				onComplete();
				return;
			};	
		};
		
		var oAnim = new YAHOO.util.Anim(dd,oAttr,seconds ,oEaseType);	
		oAnim.onComplete.subscribe(onComplete);	
		oAnim.animate();
		dd.oAnim = oAnim ;
	};
	
	function collapseAll(dl,dt,dd)
	{
		var aOtherDD = YUD.getElementsByClassName('a-m-d-expand','dd',dl);
		for(var i=0;i<aOtherDD.length;i++)
		{
			var otherDD = aOtherDD[i] ;
			if( otherDD !=dd )
			{
				collapse(dl,null,otherDD);
			};				
		};
	}
	
	
	var onMenuMouseover = function(e)
	{
		var dMenuTitle = getDT(e);
		if(!dMenuTitle){return;};
		if(dLastHoverTitle)
		{
			YUD.removeClass(dLastHoverTitle,'a-m-t-hover');
		};		
		YUD.addClass(dMenuTitle,'a-m-t-hover');
		dLastHoverTitle = dMenuTitle ;
		YUE.preventDefault(e);
		return false;		
	};
	
	var onMenuMouseout = function(e)
	{
		var dMenuTitle = getDT(e);
		if(!dMenuTitle){return;};
		if(dLastHoverTitle && dLastHoverTitle!=dMenuTitle)
		{
			YUD.removeClass(dLastHoverTitle,'a-m-t-hover');
			YUD.removeClass(dLastHoverTitle,'a-m-t-down');
		};	
		YUD.removeClass(dMenuTitle,'a-m-t-down');	
		YUD.removeClass(dMenuTitle,'a-m-t-hover');
		dLastHoverTitle = null ;
		YUE.preventDefault(e);
		return false;		
	};
	
	var onMenuMousedown = function(e)
	{
		var dMenuTitle = getDT(e);
		if(!dMenuTitle){return;};	
		YUD.addClass(dMenuTitle,'a-m-t-down');
		YUE.preventDefault(e);
		return false;	
	};
	
	var onMenuClick = function(e)
	{
		var dt = getDT(e);
		if(!dt){return;};
		var dd = getDD(dt);
		
	
		
		if(!dd){return;};
		var dl = dt.parentNode;
		
		if(dl.hasAnimation==null)
		{
			dl.hasAnimation = 0;
		}	
		if(dl.hasAnimation > 0 ){return;};
		YUD.removeClass(dt,'a-m-t-down');
		
		if(YUD.hasClass(dd,'a-m-d-expand'))
		{	
			collapse(dl,dt,dd);
		}
		else
		{			
			if( oMenuCache[ dl.id ] &&  oMenuCache[ dl.id ].dependent == false ){}
			else{collapseAll(dl,dt,dd);}
			expand(dl,dt,dd);
		};		
		YUE.preventDefault(e);
		return false;
	};
	
	
	YUE.on( document,'mouseover',onMenuMouseover);
	YUE.on( document,'mouseout',onMenuMouseout);
	YUE.on( document,'mousedown',onMenuMousedown);
	YUE.on( document,'click',onMenuClick);
	
  var oApi = {
  
	openDtById : function(sId)
	{
		var dt = document.getElementById(sId);
		if(!dt){return;};
		if(!YUD.hasClass(dt,'a-m-t')){return;};
		var dl = dt.parentNode;
		var dd = getDD(dt);
		if(dl.hasAnimation==null){dl.hasAnimation = 0;};
		
		if(dl.hasAnimation > 0 ){return;};
		if(YUD.hasClass(dd,'a-m-d-expand')){return;};
		if( oMenuCache[ dl.id ] &&  oMenuCache[ dl.id ].dependent == false ){}
		else{collapseAll(dl,dt,dd);}
		expand(dl,dt,dd);
	},
	
	closeDtById : function(sId)
	{
		var dt = document.getElementById(sId);
		if(!dt){return;};
		if(!YUD.hasClass(dt,'a-m-t')){return;};
		var dl = dt.parentNode;
		var dd = getDD(dt);
		if(dl.hasAnimation==null){dl.hasAnimation = 0;};
		if(dl.hasAnimation > 0 ){return;};
		if(!YUD.hasClass(dd,'a-m-d-expand')){return;};
		collapse(dl,dt,dd);
	},
	
	
	setting : function(id,oOptions)
	{	
		if( !oOptions ){return;};
	
		if( typeof(id)!='string' ){return;};
	
		var setMunu = function(dl)
		{	
			dl = dl || this;
			dl.hasAnimation = 0;
			oMenuCache[ dl.id ] = 
			{
				element:dl,
				dependent:true,
				onOpen:function(){},
				onClose:function(){},
				seconds:0.5,
				easeOut:true,
				openedIds:[],
				animation:true
			};
			oMenu =  oMenuCache[ dl.id ] ;
			
			if(typeof(oOptions['animation'])=='boolean')
			{
				oMenu['animation'] = !!oOptions['animation']; 
				
			};
			
			
			if(typeof(oOptions['dependent'])=='boolean')
			{
				oMenu['dependent'] = !!oOptions['dependent']; 
			};
			
			if(typeof(oOptions['easeOut'])=='boolean')
			{
				oMenu['easeOut'] = !!oOptions['easeOut']; 
			};
			
			if(typeof(oOptions['seconds'])=='number')
			{
				oMenu['seconds'] = Math.max(0 , oOptions['seconds'] ); 
			};
			
			if(typeof(oOptions['onOpen'])=='function')
			{
				oMenu['onOpen'] = oOptions['onOpen'];
			};
			
			if(typeof(oOptions['onClose'])=='function')
			{
				oMenu['onClose'] = oOptions['onClose'];
			};
		
			if(oOptions['openedIds'].shift)
			{
				oMenu['openedIds'] = oOptions['openedIds'];
			};
			
			
			for(var i=0;i<oMenu['openedIds'].length;i++)
			{
				var sId = oMenu['openedIds'][i];
				var dt = document.getElementById( sId  );
				
				if(dt && dt.tagName.toUpperCase() == 'DT')
				{
					var dl = dt.parentNode;
					var dd = getDD(dt);
					expand(dl,dt,dd);
				}
				else if(!dt)
				{
					function onDtAvailable()
					{
						var dt = this;
						if(dt.tagName.toUpperCase() == 'DT')
						{
							var dl = dt.parentNode;
							var dd = getDD(dt);
							expand(dl,dt,dd);
						};	
					};
					
					YUE.onAvailable(sId,onDtAvailable);
				}			
			};
			
			
		};
		
		if(document.getElementById(id))
		{
			setMunu(document.getElementById(id))
		}
		else
		{	
			YUE.onAvailable(id,setMunu);	
		};	
	}
 };//endof api
 
 return oApi;
 
})();

