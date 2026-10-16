# ADR-0003: Java'da server-authoritative simülasyon, saf fonksiyon motor

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Haftalık meydan okuma ve skor tablosu (ADR-0006) hile yapılamayan bir sonuç gerektirir.
Ayrıca projenin amacı Java/backend yetkinliğini göstermek.

## Karar
Simülasyon Java 21 ile `sim-core` adlı saf bir kütüphanede yazılır ve sonucu sunucu belirler (**Model A**).
Motor saf fonksiyondur: `Simulation.step(GameState, List<Command>, long tick) -> GameState`.
Spring, I/O, duvar saati yoktur. Böylece ileride TeaVM/WASM ile istemcide de çalıştırılabilir
(**Model C**: istemci tahmin eder, sunucu doğrular).

| Model | Açıklama |
|---|---|
| A | Sunucu simüle eder, istemci sadece çizer |
| B | İstemci simüle eder (Server Survival) — hileye açık |
| C | İkisi de aynı motoru çalıştırır; sunucu otoritedir, istemci gecikmesiz tahmin yapar |

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Sadece istemci (TS) | Sunucu maliyeti yok | Hile, Java gösterilemez | Proje hedefleri |
| Motor Spring içinde | Hızlı başlangıç | Test/taşınabilirlik zor | Saflık kaybolur |

## Sonuçlar
- Olumlu: Unit test kolay (girdi → çıktı), replay mümkün, WASM'a taşınabilir.
- Bedel: Determinizm disiplini zorunlu (ADR-0012); state immutable olduğundan her tick yeni nesne üretir (GC baskısı — ölçülecek).
- Takip: Model C'ye geçerken TeaVM'in desteklemediği JDK API'leri kontrol edilmeli.
