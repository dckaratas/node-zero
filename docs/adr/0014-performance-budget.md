# ADR-0014: Performans bütçesi

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-17

## Bağlam
Simülasyon sunucuda koşuyor (ADR-0003); her oturum 10 Hz'de tick atıyor. Barındırma maliyeti,
bir CPU çekirdeğinin kaç oturumu taşıyabileceğine bağlı. Kullanıcı sunucunun hızlı çalışmasını
açık bir gereksinim olarak koydu.

## Karar
| Kural | Değer |
|---|---|
| Tick bütçesi | Oturum başına **< 20 µs** (10 Hz → çekirdek başına ~5000 oturum) |
| Ölçüm | `sim-bench` modülü (JMH 1.37), `-prof gc` ile tick başına bellek ayırma da izlenir |
| Sıcak yol (`Game.step`) | Regex, stream, string birleştirme, loglama yok; kaynak sayısıyla sınırlı döngüler; içerik yüklemede bir kez çözülür (`Condition`, dalga→tehdit dizisi) |
| Paylaşım | `Game` değişmez ve thread-safe; aynı kuralları kullanan tüm oturumlar tek örneği paylaşır |

Çalıştırma: `./mvnw -q package -DskipTests && java -jar sim-bench/target/benchmarks.jar -prof gc`

### İlk ölçüm (2026-10-17, geliştirici makinesi, Temurin 21)
| Senaryo | µs/tick | B/tick |
|---|---|---|
| Sakin istasyon | 2.4 | 2504 |
| Dalga 3 (CPU doygun) | 1.4 | 2552 |
| Dalga 4 (havuzlar doygun) | 1.7 | 2504 |

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Mutable state + nesne havuzu | Sıfır allocation | Saflık ve replay garantisi kaybolur (ADR-0003) | Ölçüm gereksiz gösterdi |
| CI'da zaman eşikli test | Gerilemeyi otomatik yakalar | Paylaşımlı runner'larda gürültülü, sahte kırmızılar | Şimdilik elle JMH; ileride ayrık bir iş olabilir |

## Sonuçlar
- Olumlu: Bütçenin ~10× altındayız; immutable record tasarımı korunabilir.
- Bedel: ~2.5 KB/tick → 5000 oturumda ~125 MB/s kısa ömürlü nesne. Young GC bunu ucuz toplar; yine de izlenecek.
- Takip: Dilim 4'te (Spring Boot) uçtan uca ölçüm: snapshot serileştirme ve WebSocket maliyeti tick'ten pahalı olacaktır.

### Ölçüm (2026-10-17, Dilim 3b sonrası: hata bütçesi, karşı aksiyonlar, job)
| Senaryo | µs/tick | B/tick |
|---|---|---|
| Sakin istasyon | 1.4 | 2232 |
| Dalga 3 | 1.5 | 2656 |
| Dalga 4 | 1.4 | 2680 |
