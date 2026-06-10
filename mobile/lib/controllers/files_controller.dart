import 'package:flutter/material.dart';
import '../models/remote_file.dart';
import '../services/api_client_service.dart';
import '../services/secure_storage_service.dart';

class FilesController extends ChangeNotifier {
  final ApiClientService _apiClient = ApiClientService();

  List<RemoteFile> snapshots = [];
  bool isLoading = true;
  bool hasError = false;
  String errorMessage = '';

  List<RemoteFile> deepSearchResults = [];
  bool isDeepSearching = false;

  Map<String, bool> downloadingFiles = {};

  Future<void> fetchSnapshots() async {
    isLoading = true;
    hasError = false;
    errorMessage = '';
    notifyListeners();

    try {
      final data = await _apiClient.listFiles(page: 1, pageSize: 50);
      snapshots = data;
      isLoading = false;
      notifyListeners();
    } catch (e) {
      isLoading = false;
      hasError = true;
      errorMessage = e.toString().replaceAll('ApiException: ', '');
      notifyListeners();
    }
  }

  Future<void> performDeepSearch(String query) async {
    isDeepSearching = true;
    deepSearchResults = [];
    notifyListeners();

    try {
      if (snapshots.isEmpty) {
        await fetchSnapshots();
      }

      final List<RemoteFile> allResults = [];
      for (var snap in snapshots) {
        try {
          final files = await _apiClient.listSnapshotFiles(snapshotId: snap.id, search: query);
          allResults.addAll(files);
        } catch (e) {
          debugPrint('Erro ao buscar no snapshot \${snap.id}: \$e');
        }
      }

      allResults.sort((a, b) {
        final aDate = a.modifiedAt ?? DateTime.now();
        final bDate = b.modifiedAt ?? DateTime.now();
        return bDate.compareTo(aDate);
      });

      deepSearchResults = allResults;
    } catch (e) {
      debugPrint('Deep search error: \$e');
    } finally {
      isDeepSearching = false;
      notifyListeners();
    }
  }

  void clearSearch() {
    deepSearchResults = [];
    isDeepSearching = false;
    notifyListeners();
  }

  Future<void> downloadFile(BuildContext context, RemoteFile file, String snapshotId) async {
    final secureStorage = SecureStorageService();
    String? customDir = await secureStorage.getDownloadDir();
    
    String downloadPath;
    if (customDir != null && customDir.isNotEmpty) {
      downloadPath = '\$customDir/\${file.name}';
    } else {
      downloadPath = '/storage/emulated/0/Download/\${file.name}';
    }

    downloadingFiles[file.name] = true;
    notifyListeners();

    try {
      await _apiClient.downloadFile(snapshotId, file.path, downloadPath);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('\${file.name} salvo em \$downloadPath'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Erro ao baixar: \$e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      downloadingFiles[file.name] = false;
      notifyListeners();
    }
  }
}
