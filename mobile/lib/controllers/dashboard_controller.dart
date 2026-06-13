import 'dart:convert';
import 'package:flutter/foundation.dart';
import '../services/api_client_service.dart';
import '../services/secure_storage_service.dart';

class DashboardController extends ChangeNotifier {
  final ApiClientService _api = ApiClientService();
  final SecureStorageService _storage = SecureStorageService();

  bool isLoading = true;
  String? errorMessage;
  
  int totalFiles = 0;
  int successfulBackups = 0;
  int totalStorageBytes = 0;
  int downloadedFiles = 0;

  DashboardController() {
    _loadCachedData();
  }

  Future<void> _loadCachedData() async {
    try {
      final cachedStr = await _storage.getDashboardCache();
      if (cachedStr != null && cachedStr.isNotEmpty) {
        final data = jsonDecode(cachedStr) as Map<String, dynamic>;
        _applyData(data);
      }
    } catch (_) {
      // Ignorar erros de cache
    }
  }

  void _applyData(Map<String, dynamic> data) {
    totalFiles = data['totalFiles'] as int? ?? 0;
    successfulBackups = data['successfulBackups'] as int? ?? 0;
    totalStorageBytes = data['totalStorageBytes'] as int? ?? 0;
    downloadedFiles = data['downloadedFiles'] as int? ?? 0;
    notifyListeners();
  }

  Future<void> fetchMetrics() async {
    isLoading = true;
    errorMessage = null;
    notifyListeners();

    try {
      // CÁLCULO LOCAL: sem modificar o Backend
      // Busca a primeira página gigante de snapshots (até 1000 backups)
      final snapshots = await _api.listFiles(page: 1, pageSize: 1000);
      
      int tFiles = 0;
      int tBytes = 0;
      
      for (final snap in snapshots) {
        tFiles += snap.totalFiles;
        tBytes += snap.size; // Tamanho comprimido do snapshot
      }

      final data = {
        'totalFiles': tFiles,
        'successfulBackups': snapshots.length,
        'totalStorageBytes': tBytes,
        'downloadedFiles': 0, // Downloads não são rastreados pela API antiga do backend
      };

      _applyData(data);
      
      // Salvar em cache
      await _storage.saveDashboardCache(jsonEncode(data));
      
      isLoading = false;
      notifyListeners();
    } on NetworkException catch (e) {
      errorMessage = 'Modo Offline: Visualizando dados armazenados no cache.';
      isLoading = false;
      notifyListeners();
    } catch (e) {
      errorMessage = e.toString().replaceAll('ApiException: ', '');
      isLoading = false;
      notifyListeners();
    }
  }
}
