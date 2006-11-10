YAHOO.widget.ResizePanel = function(el, userConfig) {
	if (arguments.length > 0) {
		YAHOO.widget.ResizePanel.superclass.constructor.call(this, el, userConfig);
	}
}

YAHOO.extend(YAHOO.widget.ResizePanel, YAHOO.widget.Panel);

YAHOO.widget.ResizePanel.prototype.init = function(el, userConfig) {
	YAHOO.widget.ResizePanel.superclass.init.call(this, el/*, userConfig*/);  // Note that we don't pass the user config in here yet because we only want it executed once, at the lowest subclass level
	
	this.beforeInitEvent.fire(YAHOO.widget.ResizePanel);

	this.resizeHandle = document.createElement("DIV");
	this.resizeHandle.id = this.id + "_r";
	
	this.resizeHandle.style.position = "absolute";
	this.resizeHandle.style.width = "25px";
	this.resizeHandle.style.height = "4px";
	this.resizeHandle.style.right = "0";
	this.resizeHandle.style.bottom = "0";
	this.resizeHandle.style.padding = "0";
	this.resizeHandle.style.margin = "0";
	this.resizeHandle.style.zIndex = "1";
	
	this.resizeHandle.style.backgroundColor = "#666";
	this.resizeHandle.style.cursor = "se-resize";
	this.resizeHandle.style.fontSize = "2px";

	this.beforeRenderEvent.subscribe(function() {
			if (! this.footer) {
				this.setFooter("");
			}
		}, 
		this, true
	);
	this.renderEvent.subscribe(function() {
					var me = this;
					
					me.innerElement.appendChild(me.resizeHandle);

					this.ddResize = new YAHOO.util.DragDrop(this.resizeHandle.id, this.id);
					this.ddResize.setHandleElId(this.resizeHandle.id);

					var headerHeight = me.header.offsetHeight;

					this.ddResize.onMouseDown = function(e) {

						this.startWidth = me.innerElement.offsetWidth;
						this.startHeight = me.innerElement.offsetHeight;
						
						me.cfg.setProperty("width", this.startWidth + "px");
						me.cfg.setProperty("height", this.startHeight + "px");

						this.startPos = [YAHOO.util.Event.getPageX(e),
										 YAHOO.util.Event.getPageY(e)];

						me.innerElement.style.overflow = "hidden";
						me.body.style.overflow = "auto";
					}
					
					this.ddResize.onDrag = function(e) {
						var newPos = [YAHOO.util.Event.getPageX(e),
									  YAHOO.util.Event.getPageY(e)];
						
						var offsetX = newPos[0] - this.startPos[0];
						var offsetY = newPos[1] - this.startPos[1];
				
						var newWidth = Math.max(this.startWidth + offsetX, 10);
						var newHeight = Math.max(this.startHeight + offsetY, 10);

						me.cfg.setProperty("width", newWidth + "px");
						me.cfg.setProperty("height", newHeight + "px");

						var bodyHeight = (newHeight - 5 - me.footer.offsetHeight - me.header.offsetHeight - 3);
						if (bodyHeight < 0) {
							bodyHeight = 0;
						}

						me.body.style.height =  bodyHeight + "px";

						var innerHeight = me.innerElement.offsetHeight;
						var innerWidth = me.innerElement.offsetWidth;

						if (innerHeight < headerHeight) {
							me.innerElement.style.height = headerHeight + "px";
						}

						if (innerWidth < 20) {
							me.innerElement.style.width = "20px";
						}
					}

				}, this, true);

	if (userConfig) {
		this.cfg.applyConfig(userConfig, true);
	}

	this.initEvent.fire(YAHOO.widget.ResizePanel);
}

YAHOO.widget.ResizePanel.prototype.toString = function() {
	return "ResizePanel " + this.id;
}