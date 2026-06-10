const fs = require('fs');
const path = require('path');

function removeComments(text) {
    const stringPattern = /r?"""(?:\\.|[^\\])*?"""|r?'''(?:\\.|[^\\])*?'''|r?"(?:\\.|[^"\\])*"|r?'(?:\\.|[^'\\])*'/g;
    const commentPattern = /\/\*[\s\S]*?\*\/|\/\/.*/g;
    
    // Replace comments with empty string, keep strings
    let result = text.replace(new RegExp(`${stringPattern.source}|${commentPattern.source}`, 'g'), (match) => {
        if (match.startsWith('//') || match.startsWith('/*')) {
            return '';
        }
        return match;
    });
    
    return result.split('\n').filter(line => line.trim() !== '').join('\n') + '\n';
}

function walkDir(dir) {
    const files = fs.readdirSync(dir);
    for (const file of files) {
        const fullPath = path.join(dir, file);
        if (fs.statSync(fullPath).isDirectory()) {
            walkDir(fullPath);
        } else if (fullPath.endsWith('.dart')) {
            let content = fs.readFileSync(fullPath, 'utf8');
            let newContent = removeComments(content);
            fs.writeFileSync(fullPath, newContent, 'utf8');
        }
    }
}

walkDir('lib');
