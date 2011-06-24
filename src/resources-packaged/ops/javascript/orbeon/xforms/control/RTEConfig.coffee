# Copyright (C) 2011 Orbeon, Inc.
#
# This program is free software; you can redistribute it and/or modify it under the terms of the
# GNU Lesser General Public License as published by the Free Software Foundation; either version
# 2.1 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# The full text of the license is available at http://www.gnu.org/copyleft/lesser.html

ORBEON.xforms.control.RTEConfig =
    toolbar: {
        collapse: true,
        # We don't want a titlebar to be shown above the editor
        titlebar: false,
        draggable: false,
        buttonType: 'advanced',
        buttons: [
            {
                group: 'fontstyle', label: 'Font Name and Size',
                buttons: [
                    {
                        type: 'select', label: 'Arial', value: 'fontname', disabled: true,
                        menu: [
                            { text: 'Arial', checked: true },
                            { text: 'Arial Black' },
                            { text: 'Comic Sans MS' },
                            { text: 'Courier New' },
                            { text: 'Lucida Console' },
                            { text: 'Tahoma' },
                            { text: 'Times New Roman' },
                            { text: 'Trebuchet MS' },
                            { text: 'Verdana' }
                        ]
                    },
                    { type: 'spin', label: '13', value: 'fontsize', range: [ 9, 75 ], disabled: true }
                ]
            },
            { type: 'separator' },
            {
                group: 'textstyle', label: 'Font Style',
                buttons: [
                    { type: 'push', label: 'Bold CTRL + SHIFT + B', value: 'bold' },
                    { type: 'push', label: 'Italic CTRL + SHIFT + I', value: 'italic' },
                    { type: 'push', label: 'Underline CTRL + SHIFT + U', value: 'underline' },
                    { type: 'separator' },
                    { type: 'push', label: 'Subscript', value: 'subscript', disabled: true },
                    { type: 'push', label: 'Superscript', value: 'superscript', disabled: true }
                ]
            },
            { type: 'separator' },
            {
                group: 'textstyle2', label: '&nbsp;',
                buttons: [
                    { type: 'color', label: 'Font Color', value: 'forecolor', disabled: true },
                    { type: 'color', label: 'Background Color', value: 'backcolor', disabled: true },
                    { type: 'separator' },
                    { type: 'push', label: 'Remove Formatting', value: 'removeformat', disabled: true },
                    { type: 'push', label: 'Show/Hide Hidden Elements', value: 'hiddenelements' }
                ]
            },
            { type: 'separator' },
            {
                group: 'undoredo', label: 'Undo/Redo',
                buttons: [
                    { type: 'push', label: 'Undo', value: 'undo', disabled: true },
                    { type: 'push', label: 'Redo', value: 'redo', disabled: true }

                ]
            },
            { type: 'separator' },
            {
                group: 'alignment', label: 'Alignment',
                buttons: [
                    { type: 'push', label: 'Align Left CTRL + SHIFT + [', value: 'justifyleft' },
                    { type: 'push', label: 'Align Center CTRL + SHIFT + |', value: 'justifycenter' },
                    { type: 'push', label: 'Align Right CTRL + SHIFT + ]', value: 'justifyright' },
                    { type: 'push', label: 'Justify', value: 'justifyfull' }
                ]
            },
            { type: 'separator' },
            {
                group: 'parastyle', label: 'Paragraph Style',
                buttons: [
                    {
                        type: 'select', label: 'Normal', value: 'heading', disabled: true,
                        menu: [
                            { text: 'Normal', value: 'none', checked: true },
                            { text: 'Header 1', value: 'h1' },
                            { text: 'Header 2', value: 'h2' },
                            { text: 'Header 3', value: 'h3' },
                            { text: 'Header 4', value: 'h4' },
                            { text: 'Header 5', value: 'h5' },
                            { text: 'Header 6', value: 'h6' }
                        ]
                    }
                ]
            },
            { type: 'separator' },
            {
                group: 'indentlist2', label: 'Indenting and Lists',
                buttons: [
                    { type: 'push', label: 'Indent', value: 'indent', disabled: true },
                    { type: 'push', label: 'Outdent', value: 'outdent', disabled: true },
                    { type: 'push', label: 'Create an Unordered List', value: 'insertunorderedlist' },
                    { type: 'push', label: 'Create an Ordered List', value: 'insertorderedlist' }
                ]
            },
            { type: 'separator' },
            {
                group: 'insertitem', label: 'Insert Item',
                buttons: [
                    # The order of the following two lines is changed compared to the default in editor.js, to move
                    # the "Insert image" icon before the "Create link" icon. This solves an issue on IE6 where some
                    # space show up after the RTE icons when the RTE is placed inside a dialog and the dialog size
                    # is set relative to the viewport size using CSS. This can be reproduced by running the
                    # "dialog" sandbox example.
                    { type: 'push', label: 'Insert Image', value: 'insertimage' },
                    { type: 'push', label: 'HTML Link CTRL + SHIFT + L', value: 'createlink', disabled: true }
                ]
            }
        ]
    }
