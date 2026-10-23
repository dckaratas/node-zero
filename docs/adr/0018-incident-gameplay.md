# ADR-0018: Olay anı oynanışı (Dilim 7)

- **Durum:** Kabul edildi, uygulandı (7a kalkan + bölüm sonu, 7b müdahale + mürettebat, 7c kaldırma + ayar, 7d alarm)
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

## Ek (Dilim 7b, 2026-10-22): müdahale ve mürettebat
- İçerik: `content/actions/chapter-1.yaml`. Efektin `from_s`/`to_s` penceresi yoksa eylem anında bir kez olur
  (`EffectKind.Timing.INSTANT`); varsa o pencerede tutar (kesinti, soğuk JVM, duraklatılmış iş; mevcut `BUILD` türleri).
  Sayaçlar anlık eylem efekti kullanamaz; yükleyici reddeder.
- Komut `RunAction(id, option)` → replay'e girer. `Actions` önce bekleme süresine, sonra mürettebata bakar; anlık efektler
  state'i hemen değiştirir. Düşürülen istekler (`reset_queues`, `cancel_queries`) `WaveState.withExtraFailures` ile kalkana yazılır.
- Mürettebat: `OpsState` (`Progress.ops`): dalga başına 8 puan, **biten dalga yeni vardiya getirir** (hazırlık + sonraki
  dalga aynı vardiyada). Sayaçların `toil` alanı aynı bütçeden düşer.
- `rm` tuzağı: silinen logun payı `orphanedMb`'ye geçer, disk seviyesi değişmez; restart (`free_orphaned_disk`) boşaltır.
  Log miktarı = disk seviyesi − başlangıç seviyesi − öksüz pay. Eski arşivler bir kez silinebilir.
- Rollback: tetikleyicisi `event: deploy` olan aktif tehditleri kaldırır (log-storm); başka tehdide etkisi yok.
- Kodeks: `mitigation-vs-fix`, `deleted-open-files` (eylem ilk çalışınca açılır). Toplam 26 madde.
- Protokol: snapshot'ta `crew` (her zaman), `actions` (delta: her eylemin son çalışması, `endTick`, `readyTick`);
  katalogda `crewPerShift`, sayaçlarda `toil`, `actions` kartları (kapısız). İstemci mesajı `run_action`.
- İstemci: "Incident response" bölümü (seçenek başına bir düğme; olay anında iki adımlı form yok), başlıkta `Crew 6/8`.

| Ölçüm (DEFAULT, hazırlıksız) | Eylemsiz | Eylemle |
|---|---|---|
| Dalga 3 · 8. sn restart | ❌ 19,3 sn | ❌ 14,7 sn (aşırı yükte restart işe yaramaz, kesinti üstüne biner) |
| Dalga 4 · 5. sn restart | ❌ 27,1 sn | ❌ 20,6 sn |
| Dalga 5 · 2. sn sorgu iptali | ❌ 15,6 sn | ❌ 24,9 sn (iş duraklayınca ~9 sn kazanç) |
| Dalga 2 · 3. sn rollback | ✅ %100 | ✅ %65 (gereksiz rollback 5 sn kesinti bedeli) |

- Tasarım gereği eylemler semptomu yatıştırır, kalıcı çözüm sayaçtadır; yanlış anda kullanılan eylem kalkanı yakar.
- Benchmark: `step` ~3,3 µs (değişmedi).

## Ek (Dilim 7c, 2026-10-22): kaldırma ve ayar
- `RemoveCounter`: kurulum süresi kadar sürer (`InstalledCounter.removedTick`), bu sürede sayaç çalışmaya devam eder ve
  kurulumdaki geçiş bedelleri (`BUILD` efektleri: kesinti, yazma kilidi) **yeniden** uygulanır: "geri almak, yapmak kadar
  sürer ve aynı yerden acıtır". Bitince yapısal etki geri alınır (8 → 4 çekirdek). İade yok; mürettebat bedeli kurulumla aynı.
- Reddedilenler: kurulum sürerken, zaten kaldırılırken, başka sayaç ona dayanıyorken (Grafana varken Prometheus),
  `removable: false` (disk büyütme: hacim küçülmez). İzleme kalkınca metrikler kaybolur, **içgörüler kalır**.
- Bakım ücreti listedeki sayaçlardan alındığı için kaldırılan sayaç sonraki dalga sonunda ödenmez. Yeniden satın alınabilir.
- **Ayar (AM'nin devamı):** `ConfigureCounter(id, value)`. Sadece etkiyi doğrudan süren parametre açıldı
  (`drives_effect: true`): Nginx `rate_rps`, `statement_timeout` `timeout_ms`, bulkhead `job_pool_size`. Değer ana efektin
  değerinin yerine geçer; min/max içinde olmalı; 1 mürettebat, hemen etkili (config reload). `keep_days`, `chunk_size`,
  `station_hour`, `max_threads` gibi dolaylı parametreler şimdilik gösterilmiyor (simülasyonda karşılıkları yok).
- Ölçüm: CPU büyütme + rate limit 150 ile dalga 3 ❌ (429'lar bütçeyi yakıyor); `rate_rps = 350` yapınca ✅ kalkan %100.
- İstemci: kurulu kartta "Remove" (ikinci tıkta onay: "no refund") ve ayar alanı + "Apply (1 crew)"; kaldırılırken
  "Removing… N s". Protokol: `remove_counter`, `configure_counter`; snapshot sayaçlarında `removedTick`, `setting`;
  katalogda `removable`, `setting {key, value, min, max}`.

## Ek (Dilim 7d, 2026-10-22): alarm kuralları
- Kural = `metrik ABOVE|BELOW eşik for N sn` (Prometheus alerting rule biçimi); `OpsState.alerts`, kimlikler `a1, a2…`.
  Komutlar `SetAlertRule`, `RemoveAlertRule` → replay'e girer. En fazla 5 kural (alarm yorgunluğu), `for` 0–600 sn.
- Değerlendirme sunucuda, her tick'te modeller ilerledikten sonra (`Alerts.evaluate`, `Game.step` sonu): koşul tutunca
  **pending**, `for` süresi dolunca **firing**, koşul bozulunca hemen çözülür. Durum sadece geçişte değişir (ayırma yok);
  kural yoksa maliyet sıfır. Benchmark değişmedi.
- Görünürlük: kural sadece o an ölçülebilen metriğe kurulabilir (`Monitoring`, Observer ile ortak). İzleme sonradan
  kaldırılırsa kural **veri yok** durumuna düşer ve ateşlenmez; gerçek Prometheus'taki `absent()` dersini taşır.
- **Duraklatma yok** (AJ). İstemci: başlığın altında akışta kırmızı nabızlı banner ("FIRING cpu_utilization > 0.85 for 3 s"
  + Ack), sekme başlığında 🔴, ilgili harita düğümünün çerçevesi kırmızı atar. **Ack istemcide**: bir ateşlemeyi susturur,
  aynı kuralın sonraki ateşlemesi (yeni `firingSinceTick`) yine yüksek sesle gelir. Panelde "Alerts" listesi ve form
  (sadece görünen metrikler).
- Yan düzeltme: HUD'da çok küçük yakma hızı (×0,04, "empty in 41192 s") gizlendi (eşik ×0,1).
- Protokol: snapshot'ta `alerts` (delta); istemci mesajları `set_alert {metric, op, threshold, forS}`, `remove_alert {rule}`.

## Ek (Dilim 8a, 2026-10-22): kapasite, denetçi, süzme
- AR: snapshot `station` (çekirdek, thread, havuz, disk; delta). Harita yükü sunucudan gelen kapasiteye göre (CPU/thread büyütmesinden sonra doğru yüzde).
- AN/AQ: hex üstünde görünmez DOM düğmeleri (AS); seçilen düğüm beyaz çerçeve. Panel başında denetçi: kapasite, USE (ölçülemiyorsa "? needs <modül>", hiç export edilmiyorsa "not exported"), son 60 sn sparkline, düğümün alarm kuralları, "Alert on <metrik>" kısayolu.
- AO: katalogda `nodes` (efekt hedefleri; rate limit → gateway; hedefsizler her düğümde); düğüm seçilince kartlar süzülür, "Show all".
- Düzeltme: alarm formu metrik yokken görünüyordu.

## Ek (Dilim 8b, 2026-10-22): haritada geri bildirim (AP)
- **Rozet:** düğüme etki eden kurulu modül sayısı hex'in sağ üstünde. Hedefsiz modüller (izleme) rozet almaz: her düğüme ait oldukları için yedi hex'te birden işaret bir şey söylemez.
- **İlerleme halkası:** kurulumda camgöbeği, kaldırmada turuncu; oran katalogdaki `applyTimeS`'ten (kaldırma kurulum kadar sürer, 7c).
- **DOWN:** snapshot `station.appDown` (sunucu, `Pipeline.appDown`: aktif bir kesinti penceresi; restart, rollback, CPU değişimi kurulum/kaldırma). Oyuncu kendisi tetiklediği için izleme olmadan da görünür. Tomcat ve CPU grileşir, "DOWN" yazar.
- **Kuyruk:** HikariCP altında `hikaricp_connections_pending` kadar sarı blok (en fazla 8). **AP'den sapma:** Tomcat accept kuyruğu export edilmediği için çizilmedi (AQ, sis perdesi; denetçide de "not exported").
- **Kırmızı akış:** boru başına 6 parçacık, `error_rate` oranında kırmızı (≥ %1 ise en az bir tane; Grafana gerekir), boru boyunca dağıtılmış.
- Hesap istemcide saf fonksiyon (`game/mapFeedback.ts`, testli). `step` sıcak yolu değişmedi (`appDown` sadece gözlemde).

## Ek (Dilim 9a, 2026-10-22): dalga dengesi (AU, AV, AY)
- **AU:** log fırtınası 0,002 → 0,003 MB/istek; disk ~83. sn'de dolar (önce 118. sn, dalga 120 sn).
- **AV:** Arşiv Kayması `initial_rows` 1M → 500k, `rows_per_second` 4000 → 4750: DB ~50. sn'de ısınır, ~105. sn'de çöker
  (önce dalga zaten çökük başlıyordu). Gece Defteri `job.starts_after_s: 20` (yeni, isteğe bağlı alan) ve 1,5M → 500k satır
  (iş 30 → 10 sn).
- **AY:** `BalanceTest`: her tehdit dalgası, önceki tehditler hazırlıkta çözülmüş ve izleme kurulu bir istasyonda tek başına
  oynanır. Tepki = kalkan görünür yanınca (yakma ≥ ×1) doğru eylem + düzeltmeyi satın almak.

| Dalga | Hazırlıksız | Sadece eylem | Sadece geç düzeltme | Eylem + düzeltme | Hazırlıklı |
|---|---|---|---|---|---|
| 2 Log fırtınası | 0,42 | 1,00 (truncate) | 0,42 | 1,00 | 1,00 |
| 3 Festival | 0,00 | 0,00 | 0,25 | 0,25 | 1,00 |
| 4 Arşiv Kayması | 0,51 | — | 0,83 (blocking indeks) | 0,83 | 1,00 |
| 5 Gece Defteri | 0,39 | 0,92 (cancel-queries) | 0,93 | 0,92 | 1,00 |

- Bantlar: hazırlıksız 0,2–0,6, tepki ≥ 0,7, hazırlıklı > 0,99. **Dalga 3 bantları 9b'de** (AW özellik kapatma + AX CPU 20 sn).
- Bulgular: dalga 2'de geç `log-level-info` diski boşaltmaz, kurtaran truncate'tir. Dalga 4'te yangında doğru seçim
  **blocking** indeks (10 sn yazma kilidi), hazırlıkta concurrently; cancel-queries ve statement_timeout kurtarmaz
  (sorgular yine başarısız sayılır). Dalga 5'te cancel-queries 10 sn'lik işi o gün için atlatır; iş ertesi gün döner.
- Etkilenen testler yeni içeriğe göre güncellendi; "dalga FAILED" beklentileri "kalkan < 0,7" oldu. Golden master
  (dalga 4) içerik nedeniyle yenilendi.

## Ek (Dilim 9b, 2026-10-23): festival (AW, AX, AZ, BA, BB)
- **AW + AZ (A1):** yeni eylem `kill-switch` (öneri panelini kapat): 60 sn boyunca istek başına CPU ×0,6, isteklerin %1'i
  başarısız (`cpu_cost`, `shed_share`; yeni BUILD türleri). 2 mürettebat, 60 sn bekleme. Onaylanan %5/30 sn ölçümde
  kurtarılamaz çıktı: %99 SLO'da %5 düşürmek bütçenin 5 katı. Kodeks: `graceful-degradation` (27 madde).
  **Sonra (A3):** seçenekli eylem ("önerileri kapat" ×0,8 hatasız / "aramayı kapat" ×0,6 %2 hata).
- **AX + BB (C2):** CPU büyütmenin seçenekleri: **bakım penceresi** (400 kr, 20 sn, sonda 5 sn geçiş kesintisi) ve
  **canlı geçiş** (650 kr, 30 sn, kesintisiz). Seçenek artık fiyat da taşıyabilir (`options[].credits`, katalog ve kart).
  Kurulum/kaldırma kesintisi artık geçişin **son** `value` saniyesinde (önce tüm kurulum boyunca).
- **BA (B1):** hazırlıksız festival = 0 (istisna). CPU %100'ü geçince kuyruk dolar, isteklerin ~%70'i başarısız olur;
  1440 isteklik bütçe ~14 sn tam kesinti. Tepe çarpanı 2,2'ye inse de 0. Kapasite planlanır, doğaçlanmaz.
- `BalanceTest`: dalga 3 hazırlıksız 0,00 (< 0,2), özellik kapatma + canlı geçiş **0,80**, zirvede bakım penceresi 0,00
  (< 0,2), hazırlıklı 1,00. Diğer dalgalar değişmedi.
- Bulgu: dalga 2'deki festival duyurusu (`comms.festival_teaser`) hiçbir yerde gösterilmiyordu; dalga temalarının
  görünürlüğü ayrı dilimde (9c).
- Yan düzeltme: haritadaki ilerleme halkası seçeneğin kurulum süresini kullanıyor (indeks seçenekleri yanlış oranlanıyordu).
- Benchmark: `step` ~3 µs.
