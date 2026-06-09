import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';

/// Serviço centralizado de permissões do app.
/// 
/// Lida com as diferenças entre versões do Android:
/// - Android 12 e anterior: READ_EXTERNAL_STORAGE
/// - Android 13+ (API 33): READ_MEDIA_IMAGES, READ_MEDIA_VIDEO
/// - Android 14+ (API 34): READ_MEDIA_VISUAL_USER_SELECTED
class PermissionService {
  static final PermissionService _instance = PermissionService._();
  factory PermissionService() => _instance;
  PermissionService._();

  int? _sdkVersion;

  /// Obtém a versão do SDK Android.
  Future<int> getSdkVersion() async {
    if (_sdkVersion != null) return _sdkVersion!;
    if (!Platform.isAndroid) return 0;
    final info = await DeviceInfoPlugin().androidInfo;
    _sdkVersion = info.version.sdkInt;
    debugPrint('📱 Android SDK: $_sdkVersion');
    return _sdkVersion!;
  }

  /// Verifica se todas as permissões de armazenamento estão concedidas.
  Future<bool> hasStoragePermission() async {
    final sdk = await getSdkVersion();

    if (sdk >= 33) {
      // Android 13+: permissões granulares
      final images = await Permission.photos.isGranted;
      final videos = await Permission.videos.isGranted;
      return images && videos;
    } else {
      // Android 12 e anterior
      return await Permission.storage.isGranted;
    }
  }

  /// Solicita permissões de armazenamento ao usuário.
  /// Retorna true se as permissões foram concedidas.
  Future<bool> requestStoragePermission() async {
    final sdk = await getSdkVersion();

    if (sdk >= 33) {
      // Android 13+: solicitar permissões granulares
      final statuses = await [
        Permission.photos,
        Permission.videos,
      ].request();

      final allGranted = statuses.values.every(
        (status) => status.isGranted || status.isLimited,
      );

      if (!allGranted) {
        debugPrint('⚠️ Permissões de mídia não concedidas: $statuses');
      }
      return allGranted;
    } else {
      // Android 12 e anterior
      final status = await Permission.storage.request();
      if (!status.isGranted) {
        debugPrint('⚠️ Permissão de armazenamento não concedida: $status');
      }
      return status.isGranted;
    }
  }

  /// Solicita permissão de câmera.
  Future<bool> requestCameraPermission() async {
    final status = await Permission.camera.request();
    return status.isGranted;
  }

  /// Verifica se a permissão de câmera está concedida.
  Future<bool> hasCameraPermission() async {
    return await Permission.camera.isGranted;
  }

  /// Verifica se as permissões foram negadas permanentemente
  /// e o usuário precisa ir nas configurações do sistema.
  Future<bool> isStoragePermanentlyDenied() async {
    final sdk = await getSdkVersion();
    if (sdk >= 33) {
      return await Permission.photos.isPermanentlyDenied;
    }
    return await Permission.storage.isPermanentlyDenied;
  }

  /// Abre as configurações do app no sistema.
  Future<void> openSettings() async {
    await openAppSettings();
  }
}
