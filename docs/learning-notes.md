# Öğrenme Notları

Kontrol soruları ve düzeltilen yanlış sezgiler. Mülakat hazırlığı için.

## Kuyruk teorisi
- ρ ile gecikme ilişkisi **doğrusal değil**: `W = S/(1−ρ)`. ρ 0.5 → 0.9 olunca gecikme **5 katına** çıkar
  (ilk sezgim %40 artıştı). `UtilizationModelTest` bu gerçeği sabitleyen bir test içerir.

## Teşhis (USE yöntemi)
- Thread havuzu doygunluğunda uygulama **CPU'su düşük** görünür (thread'ler bekler, hesap yapmaz).
  İlk sezgim "yüksek" idi. → Utilization, Saturation, Errors'a ayrı ayrı bak.

## Kaynak modeli seçimi (henüz sezgisel değil — pratik gerekiyor)
| Soru | Evet ise |
|---|---|
| Saniyede ne kadar iş yapılabiliyor? (hız) | **utilization** |
| Sabit sayıda slot var ve iş slotu süre boyunca tutuyor mu? | **occupancy** |
| Zamanla birikiyor ve kendiliğinden boşalmıyor mu? | **fill** |

- 2026-10-16: HikariCP → occupancy, disk alanı → fill, disk I/O → utilization: **doğru** (I/O'da emin değildim).
- Aynı fiziksel kaynak iki modelde birden olabilir: log-storm hem disk alanını (fill, saatler) hem disk I/O'yu
  (utilization, dakikalar) vurur. "Disk sorunu" demek eksik — hangi model?

## Dayanıklılık
- Naive retry yükü **katlar** (×2, ×3...). Retry fırtınası, düşen servisi tamamen boğar → backoff + jitter + bütçe.

## Determinizm
- `System.nanoTime` değiştirilemez (monotonik) ama **makinenin o anki hızını** ölçer → simülasyona gizli
  4. girdi ekler. Sonuç yalnızca `(state, commands, seed)`'e bağlı olmalı. Değiştirilebilen saat
  `currentTimeMillis`'tir (NTP).
- `tr_TR` locale: `"id".toUpperCase()` → `"İD"`. Tanımlayıcılarda `Locale.ROOT`.
