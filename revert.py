import os

files_to_modify = [
    'src/main/java/com/eymistaken/simplecps/gui/ColorPickerScreen.java',
    'src/main/java/com/eymistaken/simplecps/gui/HudEditorScreen.java',
    'src/main/java/com/eymistaken/simplecps/gui/KeystrokesDesignerScreen.java'
]

replacements = {
    'click.x()': 'click.comp_4798()',
    'click.y()': 'click.comp_4799()',
    'charInput.codePoint()': 'charInput.comp_4793()',
    'charInput.modifiers()': 'charInput.comp_4794()',
    'keyInput.scancode()': 'keyInput.comp_4796()',
    'keyInput.modifiers()': 'keyInput.comp_4797()'
}

for file_path in files_to_modify:
    with open(file_path, 'r') as f:
        content = f.read()

    for k, v in replacements.items():
        content = content.replace(k, v)

    with open(file_path, 'w') as f:
        f.write(content)
