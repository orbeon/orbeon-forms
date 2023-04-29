const ctrlBindings = !!navigator.userAgent.match(/Macintosh/);
class Combobox {
    constructor(input, list) {
        this.input = input;
        this.list = list;
        this.isComposing = false;
        if (!list.id) {
            list.id = `combobox-${Math.random()
                .toString()
                .slice(2, 6)}`;
        }
        this.keyboardEventHandler = event => keyboardBindings(event, this);
        this.compositionEventHandler = event => trackComposition(event, this);
        this.inputHandler = this.clearSelection.bind(this);
        input.setAttribute('role', 'combobox');
        input.setAttribute('aria-controls', list.id);
        input.setAttribute('aria-expanded', 'false');
        input.setAttribute('aria-autocomplete', 'list');
        input.setAttribute('aria-haspopup', 'listbox');
    }
    destroy() {
        this.clearSelection();
        this.stop();
        this.input.removeAttribute('role');
        this.input.removeAttribute('aria-controls');
        this.input.removeAttribute('aria-expanded');
        this.input.removeAttribute('aria-autocomplete');
        this.input.removeAttribute('aria-haspopup');
    }
    start() {
        this.input.setAttribute('aria-expanded', 'true');
        this.input.addEventListener('compositionstart', this.compositionEventHandler);
        this.input.addEventListener('compositionend', this.compositionEventHandler);
        this.input.addEventListener('input', this.inputHandler);
        this.input.addEventListener('keydown', this.keyboardEventHandler);
        this.list.addEventListener('click', commitWithElement);
    }
    stop() {
        this.clearSelection();
        this.input.setAttribute('aria-expanded', 'false');
        this.input.removeEventListener('compositionstart', this.compositionEventHandler);
        this.input.removeEventListener('compositionend', this.compositionEventHandler);
        this.input.removeEventListener('input', this.inputHandler);
        this.input.removeEventListener('keydown', this.keyboardEventHandler);
        this.list.removeEventListener('click', commitWithElement);
    }
    navigate(indexDiff = 1) {
        const focusEl = Array.from(this.list.querySelectorAll('[aria-selected="true"]')).filter(visible)[0];
        const els = Array.from(this.list.querySelectorAll('[role="option"]')).filter(visible);
        const focusIndex = els.indexOf(focusEl);
        if ((focusIndex === els.length - 1 && indexDiff === 1) || (focusIndex === 0 && indexDiff === -1)) {
            this.clearSelection();
            this.input.focus();
            return;
        }
        let indexOfItem = indexDiff === 1 ? 0 : els.length - 1;
        if (focusEl && focusIndex >= 0) {
            const newIndex = focusIndex + indexDiff;
            if (newIndex >= 0 && newIndex < els.length)
                indexOfItem = newIndex;
        }
        const target = els[indexOfItem];
        if (!target)
            return;
        for (const el of els) {
            if (target === el) {
                this.input.setAttribute('aria-activedescendant', target.id);
                target.setAttribute('aria-selected', 'true');
                scrollTo(this.list, target);
            }
            else {
                el.setAttribute('aria-selected', 'false');
            }
        }
    }
    clearSelection() {
        this.input.removeAttribute('aria-activedescendant');
        for (const el of this.list.querySelectorAll('[aria-selected="true"]')) {
            el.setAttribute('aria-selected', 'false');
        }
    }
}
function keyboardBindings(event, combobox) {
    if (event.shiftKey || event.metaKey || event.altKey)
        return;
    if (!ctrlBindings && event.ctrlKey)
        return;
    if (combobox.isComposing)
        return;
    switch (event.key) {
        case 'Enter':
        case 'Tab':
            if (commit(combobox.input, combobox.list)) {
                event.preventDefault();
            }
            break;
        case 'Escape':
            combobox.clearSelection();
            break;
        case 'ArrowDown':
            combobox.navigate(1);
            event.preventDefault();
            break;
        case 'ArrowUp':
            combobox.navigate(-1);
            event.preventDefault();
            break;
        case 'n':
            if (ctrlBindings && event.ctrlKey) {
                combobox.navigate(1);
                event.preventDefault();
            }
            break;
        case 'p':
            if (ctrlBindings && event.ctrlKey) {
                combobox.navigate(-1);
                event.preventDefault();
            }
            break;
        default:
            if (event.ctrlKey)
                break;
            combobox.clearSelection();
    }
}
function commitWithElement(event) {
    if (!(event.target instanceof Element))
        return;
    const target = event.target.closest('[role="option"]');
    if (!target)
        return;
    if (target.getAttribute('aria-disabled') === 'true')
        return;
    fireCommitEvent(target);
}
function commit(input, list) {
    const target = list.querySelector('[aria-selected="true"]');
    if (!target)
        return false;
    if (target.getAttribute('aria-disabled') === 'true')
        return true;
    target.click();
    return true;
}
function fireCommitEvent(target) {
    target.dispatchEvent(new CustomEvent('combobox-commit', { bubbles: true }));
}
function visible(el) {
    return (!el.hidden &&
        !(el instanceof HTMLInputElement && el.type === 'hidden') &&
        (el.offsetWidth > 0 || el.offsetHeight > 0));
}
function trackComposition(event, combobox) {
    combobox.isComposing = event.type === 'compositionstart';
    const list = document.getElementById(combobox.input.getAttribute('aria-controls') || '');
    if (!list)
        return;
    combobox.clearSelection();
}
function scrollTo(container, target) {
    if (!inViewport(container, target)) {
        container.scrollTop = target.offsetTop;
    }
}
function inViewport(container, element) {
    const scrollTop = container.scrollTop;
    const containerBottom = scrollTop + container.clientHeight;
    const top = element.offsetTop;
    const bottom = top + element.clientHeight;
    return top >= scrollTop && bottom <= containerBottom;
}

const boundary = /\s|\(|\[/;
function query(text, key, cursor, { multiWord, lookBackIndex, lastMatchPosition } = {
    multiWord: false,
    lookBackIndex: 0,
    lastMatchPosition: null
}) {
    let keyIndex = text.lastIndexOf(key, cursor - 1);
    if (keyIndex === -1)
        return;
    if (keyIndex < lookBackIndex)
        return;
    if (multiWord) {
        if (lastMatchPosition != null) {
            if (lastMatchPosition === keyIndex)
                return;
            keyIndex = lastMatchPosition - key.length;
        }
        const charAfterKey = text[keyIndex + 1];
        if (charAfterKey === ' ' && cursor >= keyIndex + key.length + 1)
            return;
        const newLineIndex = text.lastIndexOf('\n', cursor - 1);
        if (newLineIndex > keyIndex)
            return;
        const dotIndex = text.lastIndexOf('.', cursor - 1);
        if (dotIndex > keyIndex)
            return;
    }
    else {
        const spaceIndex = text.lastIndexOf(' ', cursor - 1);
        if (spaceIndex > keyIndex)
            return;
    }
    const pre = text[keyIndex - 1];
    if (pre && !boundary.test(pre))
        return;
    const queryString = text.substring(keyIndex + key.length, cursor);
    return {
        text: queryString,
        position: keyIndex + key.length
    };
}

const properties = ['position:absolute;', 'overflow:auto;', 'word-wrap:break-word;', 'top:0px;', 'left:-9999px;'];
const propertyNamesToCopy = [
    'box-sizing',
    'font-family',
    'font-size',
    'font-style',
    'font-variant',
    'font-weight',
    'height',
    'letter-spacing',
    'line-height',
    'max-height',
    'min-height',
    'padding-bottom',
    'padding-left',
    'padding-right',
    'padding-top',
    'border-bottom',
    'border-left',
    'border-right',
    'border-top',
    'text-decoration',
    'text-indent',
    'text-transform',
    'width',
    'word-spacing'
];
const mirrorMap = new WeakMap();
function textFieldMirror(textField, markerPosition) {
    const nodeName = textField.nodeName.toLowerCase();
    if (nodeName !== 'textarea' && nodeName !== 'input') {
        throw new Error('expected textField to a textarea or input');
    }
    let mirror = mirrorMap.get(textField);
    if (mirror && mirror.parentElement === textField.parentElement) {
        mirror.innerHTML = '';
    }
    else {
        mirror = document.createElement('div');
        mirrorMap.set(textField, mirror);
        const style = window.getComputedStyle(textField);
        const props = properties.slice(0);
        if (nodeName === 'textarea') {
            props.push('white-space:pre-wrap;');
        }
        else {
            props.push('white-space:nowrap;');
        }
        for (let i = 0, len = propertyNamesToCopy.length; i < len; i++) {
            const name = propertyNamesToCopy[i];
            props.push(`${name}:${style.getPropertyValue(name)};`);
        }
        mirror.style.cssText = props.join(' ');
    }
    const marker = document.createElement('span');
    marker.style.cssText = 'position: absolute;';
    marker.innerHTML = '&nbsp;';
    let before;
    let after;
    if (typeof markerPosition === 'number') {
        let text = textField.value.substring(0, markerPosition);
        if (text) {
            before = document.createTextNode(text);
        }
        text = textField.value.substring(markerPosition);
        if (text) {
            after = document.createTextNode(text);
        }
    }
    else {
        const text = textField.value;
        if (text) {
            before = document.createTextNode(text);
        }
    }
    if (before) {
        mirror.appendChild(before);
    }
    mirror.appendChild(marker);
    if (after) {
        mirror.appendChild(after);
    }
    if (!mirror.parentElement) {
        if (!textField.parentElement) {
            throw new Error('textField must have a parentElement to mirror');
        }
        textField.parentElement.insertBefore(mirror, textField);
    }
    mirror.scrollTop = textField.scrollTop;
    mirror.scrollLeft = textField.scrollLeft;
    return { mirror, marker };
}

function textFieldSelectionPosition(field, index = field.selectionEnd) {
    const { mirror, marker } = textFieldMirror(field, index);
    const mirrorRect = mirror.getBoundingClientRect();
    const markerRect = marker.getBoundingClientRect();
    setTimeout(() => {
        mirror.remove();
    }, 5000);
    return {
        top: markerRect.top - mirrorRect.top,
        left: markerRect.left - mirrorRect.left
    };
}

const states = new WeakMap();
class TextExpander {
    constructor(expander, input) {
        this.expander = expander;
        this.input = input;
        this.combobox = null;
        this.menu = null;
        this.match = null;
        this.justPasted = false;
        this.lookBackIndex = 0;
        this.oninput = this.onInput.bind(this);
        this.onpaste = this.onPaste.bind(this);
        this.onkeydown = this.onKeydown.bind(this);
        this.oncommit = this.onCommit.bind(this);
        this.onmousedown = this.onMousedown.bind(this);
        this.onblur = this.onBlur.bind(this);
        this.interactingWithList = false;
        input.addEventListener('paste', this.onpaste);
        input.addEventListener('input', this.oninput);
        input.addEventListener('keydown', this.onkeydown);
        input.addEventListener('blur', this.onblur);
    }
    destroy() {
        this.input.removeEventListener('paste', this.onpaste);
        this.input.removeEventListener('input', this.oninput);
        this.input.removeEventListener('keydown', this.onkeydown);
        this.input.removeEventListener('blur', this.onblur);
    }
    dismissMenu() {
        if (this.deactivate()) {
            this.lookBackIndex = this.input.selectionEnd || this.lookBackIndex;
        }
    }
    activate(match, menu) {
        var _a, _b;
        if (this.input !== document.activeElement && this.input !== ((_b = (_a = document.activeElement) === null || _a === void 0 ? void 0 : _a.shadowRoot) === null || _b === void 0 ? void 0 : _b.activeElement)) {
            return;
        }
        this.deactivate();
        this.menu = menu;
        if (!menu.id)
            menu.id = `text-expander-${Math.floor(Math.random() * 100000).toString()}`;
        this.expander.append(menu);
        this.combobox = new Combobox(this.input, menu);
        const { top, left } = textFieldSelectionPosition(this.input, match.position);
        menu.style.top = `${top}px`;
        menu.style.left = `${left}px`;
        this.combobox.start();
        menu.addEventListener('combobox-commit', this.oncommit);
        menu.addEventListener('mousedown', this.onmousedown);
        this.combobox.navigate(1);
    }
    deactivate() {
        const menu = this.menu;
        if (!menu || !this.combobox)
            return false;
        this.menu = null;
        menu.removeEventListener('combobox-commit', this.oncommit);
        menu.removeEventListener('mousedown', this.onmousedown);
        this.combobox.destroy();
        this.combobox = null;
        menu.remove();
        return true;
    }
    onCommit({ target }) {
        const item = target;
        if (!(item instanceof HTMLElement))
            return;
        if (!this.combobox)
            return;
        const match = this.match;
        if (!match)
            return;
        const beginning = this.input.value.substring(0, match.position - match.key.length);
        const remaining = this.input.value.substring(match.position + match.text.length);
        const detail = { item, key: match.key, value: null };
        const canceled = !this.expander.dispatchEvent(new CustomEvent('text-expander-value', { cancelable: true, detail }));
        if (canceled)
            return;
        if (!detail.value)
            return;
        const value = `${detail.value} `;
        this.input.value = beginning + value + remaining;
        const cursor = beginning.length + value.length;
        this.deactivate();
        this.input.focus({
            preventScroll: true
        });
        this.input.selectionStart = cursor;
        this.input.selectionEnd = cursor;
        this.lookBackIndex = cursor;
        this.match = null;
        this.expander.dispatchEvent(new CustomEvent('text-expander-committed', { cancelable: false, detail: { input: this.input } }));
    }
    onBlur() {
        if (this.interactingWithList) {
            this.interactingWithList = false;
            return;
        }
        this.deactivate();
    }
    onPaste() {
        this.justPasted = true;
    }
    async onInput() {
        if (this.justPasted) {
            this.justPasted = false;
            return;
        }
        const match = this.findMatch();
        if (match) {
            this.match = match;
            const menu = await this.notifyProviders(match);
            if (!this.match)
                return;
            if (menu) {
                this.activate(match, menu);
            }
            else {
                this.deactivate();
            }
        }
        else {
            this.match = null;
            this.deactivate();
        }
    }
    findMatch() {
        const cursor = this.input.selectionEnd || 0;
        const text = this.input.value;
        if (cursor <= this.lookBackIndex) {
            this.lookBackIndex = cursor - 1;
        }
        for (const { key, multiWord } of this.expander.keys) {
            const found = query(text, key, cursor, {
                multiWord,
                lookBackIndex: this.lookBackIndex,
                lastMatchPosition: this.match ? this.match.position : null
            });
            if (found) {
                return { text: found.text, key, position: found.position };
            }
        }
    }
    async notifyProviders(match) {
        const providers = [];
        const provide = (result) => providers.push(result);
        const canceled = !this.expander.dispatchEvent(new CustomEvent('text-expander-change', { cancelable: true, detail: { provide, text: match.text, key: match.key } }));
        if (canceled)
            return;
        const all = await Promise.all(providers);
        const fragments = all.filter(x => x.matched).map(x => x.fragment);
        return fragments[0];
    }
    onMousedown() {
        this.interactingWithList = true;
    }
    onKeydown(event) {
        if (event.key === 'Escape') {
            this.match = null;
            if (this.deactivate()) {
                this.lookBackIndex = this.input.selectionEnd || this.lookBackIndex;
                event.stopImmediatePropagation();
                event.preventDefault();
            }
        }
    }
}
class TextExpanderElement extends HTMLElement {
    get keys() {
        const keysAttr = this.getAttribute('keys');
        const keys = keysAttr ? keysAttr.split(' ') : [];
        const multiWordAttr = this.getAttribute('multiword');
        const multiWord = multiWordAttr ? multiWordAttr.split(' ') : [];
        const globalMultiWord = multiWord.length === 0 && this.hasAttribute('multiword');
        return keys.map(key => ({ key, multiWord: globalMultiWord || multiWord.includes(key) }));
    }
    connectedCallback() {
        const input = this.querySelector('input[type="text"], textarea');
        if (!(input instanceof HTMLInputElement || input instanceof HTMLTextAreaElement))
            return;
        const state = new TextExpander(this, input);
        states.set(this, state);
    }
    disconnectedCallback() {
        const state = states.get(this);
        if (!state)
            return;
        state.destroy();
        states.delete(this);
    }
    dismiss() {
        const state = states.get(this);
        if (!state)
            return;
        state.dismissMenu();
    }
}

if (!window.customElements.get('text-expander')) {
    window.TextExpanderElement = TextExpanderElement;
    window.customElements.define('text-expander', TextExpanderElement);
}

// ORBEON:
// export default TextExpanderElement;
