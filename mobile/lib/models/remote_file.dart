class RemoteFile {
  final String id;
  final String name;
  final String mimeType;
  final int size;
  final String path;
  final DateTime? modifiedAt;

  RemoteFile({
    required this.id,
    required this.name,
    required this.mimeType,
    required this.size,
    required this.path,
    this.modifiedAt,
  });

  factory RemoteFile.fromJson(Map<String, dynamic> json) {
    return RemoteFile(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      mimeType: json['mimeType'] as String? ?? 'application/octet-stream',
      size: (json['size'] as int?) ?? 0,
      path: json['path'] as String? ?? '',
      modifiedAt: json['modifiedAt'] != null
          ? DateTime.parse(json['modifiedAt'] as String)
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
  };
}
