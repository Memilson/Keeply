class RemoteFile {
  final String id;
  final String name;
  final String mimeType;
  final int size;
  final String path;
  final DateTime? modifiedAt;
  final String? snapshotId;
  RemoteFile({
    required this.id,
    required this.name,
    required this.mimeType,
    required this.size,
    required this.path,
    this.modifiedAt,
    this.snapshotId,
  });
  factory RemoteFile.fromSnapshotJson(Map<String, dynamic> json) {
    final id = json['id'] as String? ?? '';
    final sourcePath = json['sourcePath'] as String? ?? '';
    final completedAt = json['completedAt'] != null
        ? DateTime.tryParse(json['completedAt'] as String)
        : null;
    final startedAt = json['startedAt'] != null
        ? DateTime.tryParse(json['startedAt'] as String)
        : null;
    final compressedSize = (json['totalCompressedSize'] as int?) ?? 0;
    final date = completedAt ?? startedAt ?? DateTime.now();
    final formattedDate =
        '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
    final displayName = 'Backup ($formattedDate)';
    return RemoteFile(
      id: id,
      snapshotId: id,
      name: displayName,
      mimeType: 'application/x-keeply-snapshot',
      size: compressedSize,
      path: sourcePath,
      modifiedAt: completedAt ?? startedAt,
    );
  }
  factory RemoteFile.fromSnapshotFileJson(Map<String, dynamic> json) {
    final path = json['path'] as String? ?? '';
    final fileName = _lastSegment(path);
    final ext = fileName.contains('.') ? fileName.split('.').last : '';
    return RemoteFile(
      id: path, 
      name: fileName,
      mimeType: _mimeFromExt(ext),
      size: (json['size'] as int?) ?? 0,
      path: path,
      modifiedAt: json['lastModified'] != null
          ? DateTime.tryParse(json['lastModified'] as String)
          : null,
    );
  }
  factory RemoteFile.fromJson(Map<String, dynamic> json) {
    return RemoteFile(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      mimeType: json['mimeType'] as String? ?? 'application/octet-stream',
      size: (json['size'] as int?) ?? 0,
      path: json['path'] as String? ?? '',
      modifiedAt: json['modifiedAt'] != null
          ? DateTime.tryParse(json['modifiedAt'] as String)
          : null,
    );
  }
  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'mimeType': mimeType,
    'size': size,
    'path': path,
    'modifiedAt': modifiedAt?.toIso8601String(),
    'snapshotId': snapshotId,
  };
  static String _lastSegment(String path) {
    if (path.isEmpty) return 'Arquivo';
    final segments = path.replaceAll('\\', '/').split('/');
    return segments.where((s) => s.isNotEmpty).lastOrNull ?? path;
  }
  static String _mimeFromExt(String ext) {
    switch (ext.toLowerCase()) {
      case 'pdf':
        return 'application/pdf';
      case 'jpg':
      case 'jpeg':
        return 'image/jpeg';
      case 'png':
        return 'image/png';
      case 'gif':
        return 'image/gif';
      case 'webp':
        return 'image/webp';
      case 'doc':
      case 'docx':
        return 'application/msword';
      case 'xls':
      case 'xlsx':
        return 'application/vnd.ms-excel';
      case 'ppt':
      case 'pptx':
        return 'application/vnd.ms-powerpoint';
      case 'zip':
        return 'application/zip';
      case 'mp4':
        return 'video/mp4';
      case 'mp3':
        return 'audio/mpeg';
      case 'txt':
        return 'text/plain';
      default:
        return 'application/octet-stream';
    }
  }
}
