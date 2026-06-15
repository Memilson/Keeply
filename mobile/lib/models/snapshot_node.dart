class SnapshotNode {
  final String name;
  final String path;
  final bool directory;
  final int size;
  final DateTime? modifiedAt;
  const SnapshotNode({
    required this.name,
    required this.path,
    required this.directory,
    this.size = 0,
    this.modifiedAt,
  });
  factory SnapshotNode.fromJson(Map<String, dynamic> json) {
    return SnapshotNode(
      name: json['name'] as String? ?? '',
      path: json['path'] as String? ?? '',
      directory: json['directory'] as bool? ?? false,
      size: json['size'] as int? ?? 0,
      modifiedAt: json['lastModified'] != null
          ? DateTime.tryParse(json['lastModified'] as String)
          : null,
    );
  }
  Map<String, dynamic> toJson() => {
    'name': name,
    'path': path,
    'directory': directory,
    'size': size,
    'lastModified': modifiedAt?.toIso8601String(),
  };
}
