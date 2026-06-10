import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';
class PermissionService {
  static final PermissionService _instance = PermissionService._();
  factory PermissionService() => _instance;
  PermissionService._();
  int? _sdkVersion;
  Future<int> getSdkVersion() async {
    if (_sdkVersion != null) return _sdkVersion!;
    if (!Platform.isAndroid) return 0;
    final info = await DeviceInfoPlugin().androidInfo;
    _sdkVersion = info.version.sdkInt;
    debugPrint('📱 Android SDK: $_sdkVersion');
    return _sdkVersion!;
  }
  Future<bool> hasStoragePermission() async {
    final sdk = await getSdkVersion();
    if (sdk >= 33) {
      final images = await Permission.photos.isGranted;
      final videos = await Permission.videos.isGranted;
      return images && videos;
    } else {
      return await Permission.storage.isGranted;
    }
  }
  Future<bool> requestStoragePermission() async {
    final sdk = await getSdkVersion();
    if (sdk >= 30) {
      var status = await Permission.manageExternalStorage.request();
      if (status.isGranted) {
        return true;
      }
      if (sdk >= 33) {
        final statuses = await [Permission.photos, Permission.videos].request();
        final allGranted = statuses.values.every((s) => s.isGranted || s.isLimited);
        if (!allGranted) debugPrint('⚠️ Permissões de mídia não concedidas: $statuses');
        return allGranted;
      } else {
        final st = await Permission.storage.request();
        return st.isGranted;
      }
    } else {
      final status = await Permission.storage.request();
      if (!status.isGranted) {
        debugPrint('⚠️ Permissão de armazenamento não concedida: $status');
      }
      return status.isGranted;
    }
  }
  Future<bool> requestCameraPermission() async {
    final status = await Permission.camera.request();
    return status.isGranted;
  }
  Future<bool> hasCameraPermission() async {
    return await Permission.camera.isGranted;
  }
  Future<bool> isStoragePermanentlyDenied() async {
    final sdk = await getSdkVersion();
    if (sdk >= 33) {
      return await Permission.photos.isPermanentlyDenied;
    }
    return await Permission.storage.isPermanentlyDenied;
  }
  Future<void> openSettings() async {
    await openAppSettings();
  }
}
