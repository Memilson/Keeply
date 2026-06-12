import 'package:flutter/material.dart';

class KeeplyMark extends StatelessWidget {
  final double size;

  const KeeplyMark({super.key, this.size = 32});

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      size: Size.square(size),
      painter: const KeeplyMarkPainter(),
    );
  }
}

class KeeplyMarkPainter extends CustomPainter {
  const KeeplyMarkPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.shortestSide / 48;
    final paint = Paint()
      ..shader = const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF9C8BFF), Color(0xFF6C4DFF)],
      ).createShader(Offset.zero & size)
      ..style = PaintingStyle.fill;

    canvas.save();
    canvas.scale(scale, scale);

    final main = Path()
      ..moveTo(20, 6)
      ..cubicTo(22, 19, 24.5, 23.5, 41, 26)
      ..cubicTo(24.5, 28.5, 22, 33, 20, 46)
      ..cubicTo(18, 33, 15.5, 28.5, -1, 26)
      ..cubicTo(15.5, 23.5, 18, 19, 20, 6)
      ..close();
    canvas.save();
    canvas.translate(2, -2);
    canvas.drawPath(main, paint);
    canvas.restore();

    final spark = Path()
      ..moveTo(39, 5)
      ..cubicTo(39.7, 9.5, 40.8, 10.6, 45, 11.3)
      ..cubicTo(40.8, 12, 39.7, 13.1, 39, 17.5)
      ..cubicTo(38.3, 13.1, 37.2, 12, 33, 11.3)
      ..cubicTo(37.2, 10.6, 38.3, 9.5, 39, 5)
      ..close();
    canvas.drawPath(spark, paint);
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
