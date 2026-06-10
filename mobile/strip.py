import os
import re

def remove_comments(text):
    # Regex to match strings (group 1) and comments (group 2)
    # Multi-line strings, single-line strings, and raw variations
    string_pattern = r'r?"""(?:\\.|[^\\])*?"""|r?\'\'\'(?:\\.|[^\\])*?\'\'\'|r?"(?:\\.|[^"\\])*"|r?\'(?:\\.|[^\'\\])*\''
    comment_pattern = r'(/\*[\s\S]*?\*/|//.*)'
    pattern = re.compile(f'({string_pattern})|{comment_pattern}')
    
    def replacer(match):
        if match.group(2) is not None:
            return ''
        return match.group(1)
        
    result = pattern.sub(replacer, text)
    
    # remove purely empty lines that only had comments
    lines = [line for line in result.split('\n') if line.strip() != '']
    return '\n'.join(lines) + '\n'

for root, dirs, files in os.walk('lib'):
    for file in files:
        if file.endswith('.dart'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            new_content = remove_comments(content)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(new_content)
