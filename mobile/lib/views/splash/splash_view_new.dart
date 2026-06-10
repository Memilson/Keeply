import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../controllers/auth_controller.dart';
import '../../services/api_client_service.dart';

class SplashView extends StatefulWidget {
  const SplashView({super.key});
  @override
  State<SplashView> createState() => _SplashViewState();
}
class _SplashViewState extends State<SplashView>
    with SingleTickerProviderStateMixin {
  late AnimationController _logoController;
  late Animation<double> _logoFade;
  @override
  void initState() {
    super.initState();
    _setupAnimations();
    _initializeApp();
  }
  void _setupAnimations() {
    _logoController = AnimationController(
      duration: const Duration(milliseconds: 1200),
      vsync: this,
    );
    _logoFade = CurvedAnimation(
      parent: _logoController,
      curve: Curves.easeInOut,
    );
    _logoController.forward();
  }
  Future<void> _initializeApp() async {
    await Future.delayed(const Duration(milliseconds: 1500));
    if (!mounted) return;
    final auth = Provider.of<AuthController>(context, listen: false);
    final success = await auth.initializeApp();
    if (!mounted) return;
    if (success) {
      await Future.delayed(const Duration(milliseconds: 400));
      if (mounted) Navigator.of(context).pushNamedAndRemoveUntil('/main', (route) => false);
    } else if (auth.errorMessage.isEmpty) {
      await Future.delayed(const Duration(milliseconds: 800));
      if (mounted) Navigator.of(context).pushReplacementNamed('/pairing');
    }
  }
  void _showError(String message) {}
  @override
  void dispose() {
    _logoController.dispose();
    super.dispose();
  }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF0F172A), Color(0xFF1E293B)],
          ),
        ),
        child: Center(
          child: Consumer<AuthController>(
            builder: (context, auth, _) {
              if (auth.errorMessage.isNotEmpty) {
                return _buildErrorWidget(auth);
              }
              return _buildLoadingWidget(auth.loadingStatus);
            },
          ),
        ),
      ),
    );
  }
  Widget _buildLoadingWidget(String loadingStatus) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        FadeTransition(
          opacity: _logoFade,
          child: CustomPaint(
            size: const Size(120, 120),
            painter: SparkPainter(),
          ),
        ),
        const SizedBox(height: 48),
        FadeTransition(
          opacity: _logoFade,
          child: const Text(
            'Keeply',
            style: TextStyle(
              fontSize: 32,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
        ),
        const SizedBox(height: 12),
        FadeTransition(
          opacity: _logoFade,
          child: Text(
            'Seus arquivos. Sua segurança.',
            style: TextStyle(fontSize: 14, color: Colors.grey[400]),
          ),
        ),
        const SizedBox(height: 60),
        const SizedBox(
          width: 50,
          height: 50,
          child: CircularProgressIndicator(
            valueColor:
                AlwaysStoppedAnimation<Color>(Color(0xFF3B82F6)),
            strokeWidth: 3,
          ),
        ),
        const SizedBox(height: 20),
        Text(
          loadingStatus,
          style: const TextStyle(fontSize: 14, color: Colors.white70),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 80),
        Text(
          'Versão 1.0.0',
          style: TextStyle(fontSize: 11, color: Colors.grey[600]),
        ),
      ],
    );
  }
  Widget _buildErrorWidget(AuthController auth) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 80,
            height: 80,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: Colors.red, width: 2),
            ),
            child: const Center(
              child: Icon(Icons.error_outline, color: Colors.red, size: 48),
            ),
          ),
          const SizedBox(height: 24),
          const Text(
            'Falha na Autenticação',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            auth.errorMessage,
            style: TextStyle(fontSize: 13, color: Colors.grey[400]),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 32),
          ElevatedButton.icon(
            onPressed: () {
              auth.errorMessage = '';
              _initializeApp();
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF3B82F6),
              padding:
                  const EdgeInsets.symmetric(horizontal: 32, vertical: 12),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            icon: const Icon(Icons.refresh),
            label: const Text(
              'Tentar Novamente',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: () async {
              await auth.logout();
              if (mounted) {
                Navigator.of(context).pushReplacementNamed('/pairing');
              }
            },
            style: OutlinedButton.styleFrom(
              side: const BorderSide(color: Color(0xFF334155), width: 1),
              padding:
                  const EdgeInsets.symmetric(horizontal: 32, vertical: 12),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            icon: const Icon(Icons.login_rounded),
            label: const Text(
              'Entrar com Senha',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Colors.white70),
            ),
          ),
        ],
      ),
    );
  }
}
class SparkPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = const Color(0xFF7B61FF)
      ..style = PaintingStyle.fill;
    final path = Path();
    final w = size.width / 24;
    final h = size.height / 24;
    path.moveTo(12 * w, 0 * h);
    path.cubicTo(13 * w, 6.5 * h, 14.8 * w, 8.2 * h, 22 * w, 9.5 * h);
    path.cubicTo(14.8 * w, 10.8 * h, 13 * w, 12.5 * h, 12 * w, 19 * h);
    path.cubicTo(11 * w, 12.5 * h, 9.2 * w, 10.8 * h, 2 * w, 9.5 * h);
    path.cubicTo(9.2 * w, 8.2 * h, 11 * w, 6.5 * h, 12 * w, 0 * h);
    path.close();
    canvas.drawPath(path, paint);
  }
  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
