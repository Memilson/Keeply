class KeeplyDevice {
  final String id;
  final String name;
  final String hostname;
  final String osName;
  final DateTime? lastSeenAt;
  const KeeplyDevice({
    required this.id,
    required this.name,
    required this.hostname,
    required this.osName,
    this.lastSeenAt,
  });
  factory KeeplyDevice.fromJson(Map<String, dynamic> json) {
    return KeeplyDevice(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      hostname: json['hostname'] as String? ?? '',
      osName: json['osName'] as String? ?? '',
      lastSeenAt: json['lastSeenAt'] != null
          ? DateTime.tryParse(json['lastSeenAt'] as String)
          : null,
    );
  }
  String get displayName {
    if (name.isNotEmpty) return name;
    if (hostname.isNotEmpty) return hostname;
    return 'Dispositivo';
  }
}
