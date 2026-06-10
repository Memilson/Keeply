import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import '../services/secure_storage_service.dart';
import '../services/permission_service.dart';

class SettingsController extends ChangeNotifier {
  final SecureStorageService _secureStorage = SecureStorageService();
  final PermissionService _permissionService = PermissionService();

  String userName = 'Usuário';
  String userEmail = 'Carregando...';
  String backendUrl = 'Carregando...';
  String downloadDir = 'Padrão (Downloads)';
  bool isBiometricEnabled = false;
  bool isInitializing = true;

  Future<void> loadSettings() async {
    isInitializing = true;
    notifyListeners();

    userName = await _secureStorage.getUserName() ?? 'Usuário';
    userEmail = await _secureStorage.getUserEmail() ?? 'Não identificado';
    backendUrl = await _secureStorage.getBackendUrl() ?? 'Não configurado';
    
    final dir = await _secureStorage.getDownloadDir();
    if (dir != null && dir.isNotEmpty) {
      downloadDir = dir;
    }

    isBiometricEnabled = await _secureStorage.isBiometricsEnabled();
    
    isInitializing = false;
    notifyListeners();
  }

  Future<void> toggleBiometric(bool value) async {
    isBiometricEnabled = value;
    await _secureStorage.setBiometricsEnabled(value);
    notifyListeners();
  }

  Future<void> changeDownloadFolder() async {
    final hasPerm = await _permissionService.requestStoragePermission();
    if (!hasPerm) return;

    final result = await FilePicker.platform.getDirectoryPath();
    if (result != null) {
      await _secureStorage.saveDownloadDir(result);
      downloadDir = result;
      notifyListeners();
    }
  }

  Future<void> clearSession() async {
    await _secureStorage.clearAll();
    notifyListeners();
  }
}
