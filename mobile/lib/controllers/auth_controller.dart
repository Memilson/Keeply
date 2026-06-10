import 'dart:io';
import 'package:flutter/material.dart';
import '../services/api_client_service.dart';
import '../services/secure_storage_service.dart';
import '../services/biometric_security_service.dart';
import '../core/constants/app_constants.dart';

class AuthController extends ChangeNotifier {
  final ApiClientService _apiClient = ApiClientService();
  final SecureStorageService _secureStorage = SecureStorageService();
  final BiometricSecurityService _bioSecurity = BiometricSecurityService();

  bool isLoading = false;
  String errorMessage = '';
  String loadingStatus = '';

  Future<bool> initializeApp() async {
    try {
      loadingStatus = 'Verificando sessão...';
      notifyListeners();

      final existingToken = await _secureStorage.getToken();
      if (existingToken != null && existingToken.isNotEmpty) {
        loadingStatus = 'Sessão encontrada. Entrando...';
        notifyListeners();
        return true;
      }
      loadingStatus = 'Redirecionando para login...';
      notifyListeners();
      return false;
    } catch (e) {
      errorMessage = 'Erro inesperado: $e';
      notifyListeners();
      return false;
    }
  }

  Future<bool> login(String email, String password) async {
    isLoading = true;
    errorMessage = '';
    notifyListeners();

    try {
      final serverUrl = AppConstants.backendBaseUrl;
      await _secureStorage.saveBackendUrl(serverUrl);
      await _secureStorage.saveUserEmail(email);
      
      final parts = email.split('@');
      if (parts.isNotEmpty) {
        final rawName = parts[0];
        final formattedName = rawName[0].toUpperCase() + rawName.substring(1);
        await _secureStorage.saveUserName(formattedName);
      }

      await _apiClient.login(email, password);

      final deviceName = 'Celular de ${email.split('@')[0]}';
      final hostname = Platform.isAndroid ? 'Android-Phone' : 'iPhone';
      final osName = Platform.isAndroid ? 'Android' : 'iOS';
      
      await _apiClient.registerDevice(
        name: deviceName,
        hostname: hostname,
        osName: osName,
        agentVersion: '1.0.0',
      );

      await _secureStorage.setPairingStatus(true);
      isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      isLoading = false;
      errorMessage = e.toString().replaceAll('ApiException: ', '');
      notifyListeners();
      return false;
    }
  }

  Future<void> logout() async {
    await _secureStorage.clearAll();
    notifyListeners();
  }
}
