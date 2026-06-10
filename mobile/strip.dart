import 'dart:io';

void main() {
  final dir = Directory('lib');
  if (!dir.existsSync()) return;

  final files = dir.listSync(recursive: true).whereType<File>().where((f) => f.path.endsWith('.dart'));

  final stringPattern = r'r?"""(?:\\.|[^\\])*?"""|r?\'\'\'(?:\\.|[^\\])*?\'\'\'|r?"(?:\\.|[^"\\])*"|r?\'(?:\\.|[^\'\\])*\'';
  final commentPattern = r'(/\*[\s\S]*?\*/|//.*)';
  final regex = RegExp('($stringPattern)|$commentPattern');

  for (final file in files) {
    String content = file.readAsStringSync();
    
    String newContent = content.replaceAllMapped(regex, (match) {
      if (match.group(2) != null) {
        return '';
      }
      return match.group(1)!;
    });

    final lines = newContent.split('\n').where((line) => line.trim().isNotEmpty).toList();
    file.writeAsStringSync(lines.join('\n') + '\n');
  }
}
