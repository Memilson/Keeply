import 'package:flutter/material.dart';
import '../../controllers/ws_controller.dart';

class ScheduleCard extends StatelessWidget {
  final WsController ws;
  const ScheduleCard({super.key, required this.ws});

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final nextRun = DateTime(now.year, now.month, now.day + 1, 2, 0);
    final diff = nextRun.difference(now);
    final hours = diff.inHours;
    final minutes = diff.inMinutes % 60;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: const LinearGradient(colors: [Color(0xFF1E3A5F), Color(0xFF1E293B)], begin: Alignment.topLeft, end: Alignment.bottomRight),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF334155)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const Icon(Icons.schedule, color: Color(0xFF06B6D4), size: 18),
            const SizedBox(width: 8),
            const Text('Agendamento', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 14)),
            const Spacer(),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(color: const Color(0xFF06B6D4).withValues(alpha: 0.15), borderRadius: BorderRadius.circular(8)),
              child: Text('em ${hours}h ${minutes}m', style: const TextStyle(color: Color(0xFF06B6D4), fontSize: 11, fontWeight: FontWeight.w600)),
            ),
          ]),
          const SizedBox(height: 12),
          _infoRow('Proximo', '${nextRun.day.toString().padLeft(2, '0')}/${nextRun.month.toString().padLeft(2, '0')}/${nextRun.year} ${nextRun.hour.toString().padLeft(2, '0')}:${nextRun.minute.toString().padLeft(2, '0')}'),
          _infoRow('Frequencia', 'Diario 02:00'),
          _infoRow('Source', ws.appState.source.isNotEmpty ? ws.appState.source : '—'),
        ],
      ),
    );
  }

  Widget _infoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(children: [
        SizedBox(width: 100, child: Text(label, style: const TextStyle(color: Color(0xFF64748B), fontSize: 12))),
        Expanded(child: Text(value, style: const TextStyle(color: Color(0xFFE2E8F0), fontSize: 12), overflow: TextOverflow.ellipsis)),
      ]),
    );
  }
}
