# ADR-0015: Oyun sunucusu mimarisi (Dilim 4)

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-17

## Bağlam
`sim-core` saf ve deterministik (ADR-0003, ADR-0012). Sunucunun işi: oturumları tutmak, 10 Hz'de tick atmak,
oyuncu komutlarını almak ve sis perdeli görünümü (`Observation`) göndermek. Performans açık bir gereksinim (ADR-0014).

## Karar
| # | Konu | Seçim |
|---|---|---|
| K | Oturumlar | Bellekte, tek sunucu. Her oturum seed + kabul edilen komut günlüğü tutar → birebir replay (`GET /api/sessions/{id}/replay`) |
| L | Tick | Çekirdek başına bir `TickShard` thread'i; oturumlar round-robin dağıtılır; `scheduleAtFixedRate(100 ms)`. Döngü süresi `nodezero.tick.loop`, taşmalar `nodezero.tick.overruns` (Micrometer → `/actuator/prometheus`) |
| M | Protokol | Ham WebSocket `/ws/game` + JSON, `type` alanlı mesajlar. Her tick tam `Observation` (`snapshot`) |
| N | Çerçeve | Spring Boot 4.1.1; tüm proje sürümleri Boot BOM'undan (JUnit 6, Jackson 2.21 + 3.1). `sim-core` Spring'e bağımlı değil |

Ayrıntılar:
- **Komut doğrulama denemeyle:** `Game.step` saf olduğu için her komut adayla birlikte denenir; kural dışı olan `rejected` mesajıyla
  döner, diğerleri aynı tick'te uygulanır. Son başarılı deneme bir sonraki state'tir (ek adım yok).
- **Aktif duraklatma:** duraklatılmış oturum tick atmaz; gelen komutlar bekletilir, devam edilince uygulanır (ADR-0001).
- **Yavaş istemci:** `ConcurrentWebSocketSessionDecorator` (1 sn / 512 KB); aşan istemci kopar, shard beklemez.
- **Thread modeli:** oturum state'i sadece kendi shard thread'inde değişir (kilit yok); girişler lock-free kuyrukla gelir.

## Değerlendirilen alternatifler
| Alternatif | Neden seçilmedi |
|---|---|
| Oturum başına (virtual) thread | 10 Hz'de binlerce zamanlayıcı; sapma ve bağlam değişimi; shard ile aynı işi daha pahalıya yapar |
| STOMP | Pub/sub gerekmiyor; ek çerçeve ve mesaj yükü |
| Baştan delta snapshot | Ölçmeden karmaşıklık; aşağıdaki ölçüm şimdi ihtiyacı gösteriyor |
| Redis/PostgreSQL'de oturum | Tek sunucuda erken; yeniden başlatmada oturum kaybı kabul edildi |

## Ölçüm (2026-10-17, 12 çekirdek, `SchedulerLoadTest`, JSON serileştirme dahil, gönderim hariç)
| Oturum | En yavaş shard döngüsü (ort.) | Tepe | Giden JSON |
|---|---|---|---|
| 1000 | 3.9 ms / 100 ms | 192 ms | 4.2 MB/s |
| 5000 | 10.7 ms / 100 ms | 270 ms | 21 MB/s (≈168 Mbit/s) |

## Sonuçlar
- Olumlu: CPU bol; 5000 oturumda çekirdek başına ~%11.
- **Bulgu 1:** oturum başına ~25 µs/tick'in ~2 µs'si simülasyon, gerisi JSON serileştirme.
- **Bulgu 2:** darboğaz bant genişliği. 10 Hz × ~420 B × 5000 ≈ 168 Mbit/s. Çözüm adayları: snapshot'ı 5 Hz'e
  düşürmek (istemci interpolasyon yapacak, Dilim 5), değişen metrikleri delta göndermek, sıkıştırma (permessage-deflate).
- Tepe değerler (~200 ms) muhtemelen ısınma dönemi (JIT/GC); Micrometer `max` ısınmayı da kapsıyor. Doğrulanmadı.
- Sınırlama: yeniden başlatmada oturumlar kaybolur; kimlik doğrulama yok (replay uç noktası UUID ile korunuyor). Dilim 7.
- Tuzak notu: kendi parent POM'umuz `-parameters` bayrağını eklemek zorunda (`@PathVariable`); artımlı derleme
  derleyici ayarı değişince eski sınıfları yeniden derlemedi → `clean` gerekti.
