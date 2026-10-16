# ADR-0001: Gerçek zaman + aktif duraklatma

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Canlı ortam olayları zaman baskısı altında yaşanır: kuyruk her saniye büyür, hata bütçesi akar.
Öte yandan oyun eğitici; oyuncunun düşünmeye, kodeksi okumaya, dashboard'a bakmaya zamanı olmalı.

## Karar
Simülasyon 10 Hz tick ile gerçek zamanlı akar; oyuncu istediği an duraklatıp komut verebilir
(RTS'lerdeki "active pause"). Duraklatmada simülasyon tick'i ilerlemez.

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Tur tabanlı | Düşünme zamanı sınırsız | Zaman baskısı ve "kuyruk büyüyor" hissi kaybolur | Olay yönetiminin özü kaçar |
| Saf gerçek zaman | Gerilim yüksek | Yeni başlayan öğrenemez | Eğitim amacıyla çelişir |

## Sonuçlar
- Olumlu: Zaman baskısı + öğrenme alanı birlikte.
- Bedel: Komutlar tick'e bağlanmalı (`Simulation.step(..., commands, tick)`); duraklatma sunucu tarafında yönetilir.
- Takip: Haftalık meydan okumada duraklatma sınırı/cezası gerekebilir (ADR-0006).
