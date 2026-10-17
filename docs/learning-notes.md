# Öğrenme Notları

Kontrol soruları ve düzeltilen yanlış sezgiler. Mülakat hazırlığı için.

## Kuyruk teorisi
- ρ ile gecikme ilişkisi **doğrusal değil** (ilk sezgim %40 artıştı).
  - **Tek kasa** (M/M/1, `W = S/(1−ρ)`): ρ 0.5 → 0.9 olunca gecikme **5×**.
  - **4 kasa, ortak kuyruk** (M/M/4, Erlang-C): 21.7 → 59.4 ms, yani **~2.7×**. (Düzeltme, 2026-10-16.)
  - Sebep **havuzlama etkisi**: istek ancak tüm sunucular meşgulse bekler. 64 çekirdekli DB %85'te rahat,
    tek thread'li Redis %85'te can çeker. Az sayıda büyük havuz > çok sayıda küçük havuz.
  - Ama ρ → 1'de her ikisi de patlar: M/M/4'te ρ = 0.99 → 509 ms.
- Kontrol sorusu (2026-10-16): "Bizim M/M/1 formülü c > 1'de temkinli mi?" → Temkinli ✅, ama gerekçeyi
  ρ tavanıyla (0.99) karıştırdım. Tavan sıfıra bölmeyi önler; fazla tahminin sebebi havuzlamayı yok saymak.
  Fazla tahmin = erken ölçekleme (kredi kaybı); eksik tahmin = sürpriz kesinti (tehlikeli).

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

## Kuyruk dolunca ne olur? (2026-10-16)
- Cevabım: "bağlantı reddi, hata bütçesini düşürmeli" → **yön doğru, eksik**.
- Tomcat `acceptCount` dolunca istek **uygulamaya hiç ulaşmaz**; reddi işletim sisteminin TCP backlog'u yapar.
  Linux'ta varsayılan (`tcp_abort_on_overflow=0`) davranış SYN'i sessizce düşürmektir → istemci **connection timeout** görür
  (anında "refused" değil; SYN birkaç kez tekrar denenir).
- Önde Nginx/LB varsa kullanıcı **502/504** görür. 503 (uygulama bilerek "meşgulüm" der) ve 429 (rate limiter) **farklı** şeylerdir.
- **Sis perdesi dersi:** Uygulamanın kendi metrikleri (`http_server_requests_seconds_count`) bu istekleri **saymaz**, çünkü
  Spring onları hiç görmez. Sadece LB/edge metrikleri veya kullanıcı şikâyetleri gösterir. "Dashboard'da hata yok ama
  kullanıcılar şikâyetçi" durumunun klasik sebebidir.

## Ertelenen sorular (ayrıntılı anlatım aşamasında ele alınacak)
- Dilim 1b: DB havuzu doygunken (golden tick 150) CPU `rho` neden sabit 0.85? Gerçek Spring'de CPU'ya ne olur,
  model neyi eksik bırakıyor? (USE, thread doygunluğunda CPU düşük; kaynaklar arası bağlantı Dilim 3)
- Dilim 1b: Tomcat thread havuzu mu, Hikari mi önce dolar; kullanıcı hangi hatayı görür?
  (Hikari `connectionTimeout` → `SQLTransientConnectionException` → 500; thread'ler bekleyerek tükenir → acceptCount)

## Dayanıklılık
- Naive retry yükü **katlar** (×2, ×3...). Retry fırtınası, düşen servisi tamamen boğar → backoff + jitter + bütçe.

## Determinizm
- `System.nanoTime` değiştirilemez (monotonik) ama **makinenin o anki hızını** ölçer → simülasyona gizli
  4. girdi ekler. Sonuç yalnızca `(state, commands, seed)`'e bağlı olmalı. Değiştirilebilen saat
  `currentTimeMillis`'tir (NTP).
- `tr_TR` locale: `"id".toUpperCase()` → `"İD"`. Tanımlayıcılarda `Locale.ROOT`.
  `String.format("%.2f")` de etkilenir: `0,50` yazar.
- Koleksiyon sırası (2026-10-16, cevabı bilmiyordum):
  - `Set.of`/`Map.of` iterasyon sırası **her JVM açılışında değişir** (rastgele salt) → 3 çalıştırmada 3 farklı sıra gördük.
  - `HashMap<Enum,…>`: enum `hashCode`'u kimlik tabanlı → sıra garanti değil.
  - forbiddenapis **imzayı** görür, çalışma anı türünü görmez: `Map#entrySet`'i yasaklamak `TreeMap`'i de yasaklar.
    Çözüm: sırasız koleksiyonun **üretildiği** yerleri yasakla (`new HashMap`, `Set.of`, `Collectors.toSet`).
- Determinizm testi (2026-10-16): "aynı girdiyi iki kez çalıştır, karşılaştır" testi **aynı JVM içinde** koşar.
  `Set.of`'un salt'ı JVM başına bir kez seçilir → iki çalıştırma da aynı sırayı görür → test hatayı **yakalayamaz**.
  Gerçek koruma: **golden master** testi (son state'in hash'i dosyada sabit; farklı JVM/OS'ta da aynı çıkmalı).
