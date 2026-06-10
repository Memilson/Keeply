import 'dart:io';
class NetworkService {
  static final NetworkService _instance = NetworkService._();
  factory NetworkService() => _instance;
  NetworkService._();
  Future<bool> hasInternetConnection() async {
    try {
      final result = await InternetAddress.lookup(
        'example.com',
      ).timeout(const Duration(seconds: 5));
      return result.isNotEmpty && result.first.address.isNotEmpty;
    } catch (_) {
      return false;
    }
  }
}
