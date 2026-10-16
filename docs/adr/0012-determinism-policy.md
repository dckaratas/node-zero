# ADR-0012: Determinizm politikası

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Replay doğrulama (ADR-0006) ve sunucu–istemci uzlaşması (Model C, ADR-0003) için
`(state, commands, seed)` aynıysa çıktı **bit bit aynı** olmalı.

## Karar
| Kaynak | Kural | Zorlama |
|---|---|---|
| Zaman | Sadece `tick * DT`; duvar saati yok (`currentTimeMillis`, `nanoTime`, `now()`) | `forbiddenapis` + `sim-core/forbidden.txt` |
| Rastgelelik | Seed'den türetilen `SplittableRandom`; seed'siz kurucular yasak | `forbiddenapis` |
| Koleksiyon sırası | `HashMap`/`HashSet` iterasyonu yasak; `EnumMap`, `SortedMap`, `List` | Kod incelemesi + determinizm testi |
| Locale | Locale'e bağlı API'ler yasak (`toUpperCase()` vb.) | `jdk-unsafe` imzaları; testler `tr_TR` altında |
| Matematik | Aşkın fonksiyonlar (`pow`, `exp`, `log`, trig) için `StrictMath`; `Math` sürümleri intrinsic yüzünden platforma göre son bitte farklılaşabilir. `sqrt` ve `+ − × ÷` IEEE 754 ile zaten kesin | `forbiddenapis` |
| Paralellik | `step` tek thread'de çalışır | Tasarım |

Doğrulama: aynı girdiyle iki kez çalışan determinizm testi (Dilim 1a, `SimulationTest`).

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Sadece kod incelemesi | Ek araç yok | İnsan hatası | Kural koda gömülmeli |
| ArchUnit | Esnek kurallar | Çağrı düzeyinde yasak için daha fazla kod | forbiddenapis hazır imza setleri sunuyor |
| Sabit noktalı aritmetik (fixed-point) | Platformlar arası kesin | Karmaşık | `double` Java'da IEEE 754 ile zaten deterministik (`strictfp` Java 17'den beri varsayılan) |

## Sonuçlar
- Olumlu: İhlal derleme anında yakalanır, mesajıyla birlikte.
- Bedel: Kütüphane seçimi kısıtlanır.
- Takip: Model C'de JS/WASM tarafında `StrictMath` karşılığının (fdlibm portu) aynı sonucu verdiği doğrulanmalı.
