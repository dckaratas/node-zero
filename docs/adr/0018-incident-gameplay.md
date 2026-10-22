# ADR-0018: Olay anı oynanışı (Dilim 7)

- **Durum:** Kabul edildi; 7a uygulandı (7b müdahale, 7c kaldırma, 7d alarm sırada)
- **Tarih:** 2026-10-22

## Bağlam
Dilim 6 sonunda oyuncu sadece kart alıyor, dalga başlatıyor ve postmortem cevaplıyordu. Dalga sırasında karar baskısı yoktu,
kalkanın ne hızla eridiği görünmüyordu, bölümün sonu belirsizdi. Canlıya alma (eski Dilim 7) en sona alındı.

## Karar
| # | Konu | Seçim |
|---|---|---|
| AD | Kalkan görünürlüğü (7a) | Kalkan çubuğu + yüzde, **yakma hızı** (SRE burn rate) ve "boşalmaya kalan süre". %50 ve %25'te uyarı, 0'da tam ekran bildirim; dalga FAILED. Kodekste `burn-rate` |
| AE | Bölüm sonu (7a) | Son dalga kazanılıp postmortemi cevaplanınca özet: dalga başına kalkan, kredi, kök neden; 1–3 yıldız. Game over yok; başarısız dalga tekrar oynanır |
| AF | Müdahale modeli (7b) | `content/actions/*.yaml`, `RunAction(id, option)`; kredi yok, bedel mürettebat + bekleme süresi; her eylemin kendi zararı var |
| AG | Mürettebat (7b) | Dalga başına 8 puan; sayaçların `toil` alanı da bu bütçeden düşer |
| AH | İlk eylemler (7b) | Restart, disk temizleme (`truncate` / eski `.gz` / tuzak `rm` açık dosya), `pg_cancel_backend`, rollback |
| AI | Kaldırma (7c) | `RemoveCounter`; kurulum kadar sürer, iade yok, bakım biter; disk büyütme geri alınamaz; bağımlısı olan kalkmaz; içgörüler kalır |
| AJ | Alarm (7d) | sim-core'da deterministik kurallar (metrik, operatör, eşik, `for`), en fazla 5; tetiklenince belirgin uyarı, **duraklatma yok** |

## Değerlendirilen alternatifler
| Alternatif | Neden seçilmedi |
|---|---|
| Sadece kalkan çubuğu | Erimenin hızı görünmüyor; "ne kadar acil?" sorusu cevapsız |
| Kalıcı kayıp (game over) | Eğitim oyununda deneme cesaretini kırar |
| Müdahaleyi krediyle satmak | Gerçekte restart'ın parası yok; bedeli insan zamanı ve kesinti |
| Alarmı istemcide değerlendirmek | Sunucu bilmez; postmortem ve puanlamada kullanılamaz |

## Ek (Dilim 7a, 2026-10-22): kalkan ve bölüm sonu
- **Yakma hızı** `Observer`'da hesaplanır, state'e yeni alan eklemez: `lastFailedRps / (bütçe / dalga süresi)`.
  1 = bütçe tam dalga sonunda biter. Kalan süre = `kalan bütçe / lastFailedRps`. Trafik şekli değişken olduğu için hız
  "düzgün tempo"ya göredir (SRE tanımı); kalan süre o anki hıza göre tahmindir.
- Hazırlıksız dalga 3'te yakma hızı ~80× çıkıyor, kalkan ~1 saniyede bitiyor. %1 bütçe gerçekten acımasız; ders bu.
- **Dalga sonuçları** `Progress.results` (dalga numarasına göre sıralı; tekrar oynanan dalga eskisinin yerini alır ama tek
  postmortem kararını korur). `ChapterScore` özeti hesaplar: 1 yıldız bitirmek, 1 yıldız ortalama kalkan ≥ %50,
  1 yıldız tüm kök nedenler doğru.
- Son dalgadan sonrası reddedilir ("chapter 1 ends at wave 5"); bölüm numarası tehditlerin `chapter` alanından gelir.
- Protokol: snapshot'ta `burnRate`, `shieldEmptyInS` (her zaman); `results` delta alanı; `chapter` postmortem gibi
  (varsa gönderilir). Yeni fixture'lar: `snapshot-burning`, `snapshot-chapter`.
- İstemci: eşik geçişleri `shieldAlert` saf fonksiyonunda (her eşik dalga başına bir kez, aşağı yönde). Çöküş bildirimi
  3,5 sn ekranı kaplar (tıklayınca kapanır); postmortem diyaloğu onu bekler. Bölüm özeti postmortem kapandıktan sonra açılır.
- `burn-rate` kodeks maddesi log-storm'un maddelerine eklendi (ilk tehdit dalgasının postmortemiyle açılır).

## Ek (Dilim 7a denge, 2026-10-22): bütçe penceresi ve gecikme payı
Ölçüm: hazırlıksız dalga 3 ilk hatadan 1,4 sn sonra çöküyordu (yakma hızı ×86); oyun ikiliydi (doğru kart = %100, yoksa ~1 sn).
Nedenler: bütçe tek dalganın %1'i (300 istek) ve ortalama gecikme 500 ms'yi geçince **bütün** isteklerin hata sayılması.

| # | Karar | Uygulama |
|---|---|---|
| AK | Bütçe penceresi = bir istasyon günü | `GameBalance.budgetWindowS = 1440`; `WaveState.windowRequests = taban rps × pencere`. Yakma hızı standart SRE tanımı: `lastFailedRps / (bütçe / pencere)` |
| AL | İstek başına gecikme hatası | `Waves.slowShare = e^(−slo/ortalama)`; milyonda birin altı yok sayılır (sıvı model sağlıklı istasyonda kesir istek yazmasın, şikâyet sinyali yanlış tetiklenmesin) |
| AM | Rate limit değişmedi | 429 hata sayılmaya devam ediyor |

**AL'den sapma:** onaylanan metin Erlang-C kuyruk formülünü söylüyordu. İstek gecikmesi üç zincirli kuyruktan geçiyor
(thread, CPU, DB havuzu) ve elimizde dağılımlar değil sadece ortalamalar var; tek kuyruğun formülü zincire uygulanamaz.
Bunun yerine p99 panelinin zaten kullandığı üstel kuyruk varsayımı (ADR-0013) toplam ortalamaya uygulandı; tek ve yoğun bir
M/M/c kuyruğunda Erlang-C de aynı üstel sönümü verir. Amaç (kademeli pay, uçurum yok) aynı.

| Dalga · hazırlık (DEFAULT) | Önce | Sonra |
|---|---|---|
| 3 · yok | ❌ 11,6 sn (ilk hatadan 1,4 sn sonra) | ❌ 19,2 sn (ilk hatadan ~14 sn sonra) |
| 3 · rate limit 150 | ❌ 12,7 sn | ❌ 22,1 sn |
| 3 · CPU büyütme | ✅ %100 | ✅ %100 |
| 3 · CPU + rate limit 150 | ❌ | ❌ (limit 8 çekirdeğe göre çok düşük; ayar 7c'de) |
| 4 · yok / statement-timeout | ❌ 6,3 sn | ❌ 27 sn |
| 5 · yok | ❌ 1,4 sn | ❌ 15,5 sn |
| 5 · batch-chunking | ❌ 1,4 sn | ✅ kalkan %38 (artık kısmi çözüm görünüyor) |
| 5 · bulkhead | ✅ %100 | ✅ %100 |

- Tepki penceresi ~1 sn'den 14–27 sn'ye çıktı; 7b müdahalelerine yer açıldı. Restart (10 sn kesinti) taban yükte bütçenin ~%70'i.
- Benchmark (ADR-0014): `step` 3,1–3,4 µs (önce 3,4–3,8; gürültü içinde), bütçe 20 µs.
- Açık: dalga 2 hâlâ hazırlıksız %100 (disk kalıcı tehdit olarak sonra doluyor); içerik dengesine bırakıldı.
