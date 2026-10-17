# ADR-0016: Tarayıcı istemcisi (Dilim 5)

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-17

## Bağlam
Sunucu her oturuma en fazla 5 Hz'de sis perdeli görünüm gönderiyor (ADR-0015 eki). İstemcinin işi bunu çizmek,
oyuncunun eylemlerini göndermek. Oyun mantığı istemcide **yok** (sunucu otoriter, ADR-0003).

## Karar
| # | Konu | Seçim |
|---|---|---|
| O | Araç zinciri | Aynı repoda `client/`: Vite + TypeScript (strict, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`) + PixiJS 8 + Vitest + ESLint (`typescript-eslint` strict). CI'da ayrı job |
| P | Akıcılık | `GameStore`: son iki görünüm arasında interpolasyon; ekran bir snapshot aralığı (~200 ms) geride, **tahmin yok**. Sayısal alanlar harmanlanır; faz, kredi, listeler anında değişir |
| Q | Çizim / UI | Pixi sadece altıgen istasyon haritası (yük renkleri, istek akışı). HUD ve paneller DOM + CSS (erişilebilirlik, i18n, metin) |
| R | Protokol tipleri | Elle yazılmış TS tipleri + katı ayrıştırıcı (bilinmeyen alan = hata) + **sözleşme fixture'ları**: sunucu `ContractFixturesTest` `client/src/protocol/fixtures/server/*.json` üretir/doğrular; istemcinin ürettiği eylemler `fixtures/client/*.json`, sunucu bunları ayrıştırır |

Canlıda istemci Nginx'ten statik dosya olarak sunulur; JVM sadece oyunu taşır (Dilim 7). Geliştirmede Vite `/ws` ve `/api`'yi
`localhost:8080`'e proxy'ler (tek origin, CORS yok).

## Sözleşme düzeltmesi
`seed` 64 bitlik `long`; JS `number` 2^53 üstünü kesin tutamaz → `welcome` ve replay'de **string** olarak gönderilir
(`@JsonFormat(shape = STRING)`). Fixture testi ve gerçek oturumdaki negatif 19 haneli seed ile doğrulandı.

## Değerlendirilen alternatifler
| Alternatif | Neden seçilmedi |
|---|---|
| Ayrı repo | Protokol değişikliği iki PR + sürüm koordinasyonu; çalışma anı yüküne etkisi yok |
| Phaser | Fizik/sahne yönetimi gereksiz; bize sadece 2D çizim lazım |
| React UI | Bu boyutta gereksiz bağımlılık; DOM sınıfı yeterli. Panel sayısı artarsa yeniden değerlendirilir |
| JSON Schema / OpenAPI'den kod üretimi | 5 mesaj tipi için ağır; fixture testi aynı güvenceyi verir |
| Tahmin (extrapolation) | Oyuncuya hiç olmamış bir değeri gösterme riski; 200 ms gecikme bu oyunda fark edilmez |

## Sonuçlar
- Kapasiteler (200 thread, 10 bağlantı) istemcide sabit (`station.ts`): oyuncunun kendi konfigürasyonu, sızıntı değil.
  İstasyonlar farklılaşınca `welcome` mesajına taşınacak.
- Yeniden bağlanma yeni oturum açar (oturuma dönüş Dilim 7).
- Paket boyutu: Pixi renderer'ları ayrı parçalar; gzip ile ilk yük ~150 KB mertebesinde.
- Dev konsol yardımcısı `nodezero.send(...)` sadece `vite dev`'de; sayaç paneli Dilim 5b.

## Ek (Dilim 5b, 2026-10-17): sayaç paneli
| # | Konu | Seçim |
|---|---|---|
| V | Katalog | `GET /api/sessions/{id}/catalog?lang=en\|tr`: isim, takas metni, maliyet, süre, seçenek, gereksinim; metinler istenen dilde çözülmüş. `CounterCatalog` açılışta iki dil için bir kez kurulur, istek sadece filtreler. ETag + `Cache-Control: private, no-cache` → değişmemişse 304. Sözleşme fixture'ı: `fixtures/server/catalog.json` (3 örnek sayaç) |
| W | Kilitli sayaçlar | İçgörü isteyenler (`create-index`, `reschedule-job`) içgörü kazanılana kadar **sunucuda** katalogdan çıkarılır (sis perdesi sunucu tarafında, geliştirici araçlarıyla da görülmez); belirince "new" rozeti. Sayaç isteyenler kilitli ve gerekçeli ("Needs Sensor Array") görünür |
| X | Kurulum durumu | `Observation.counters` artık `{id, option, readyTick}` (sim-core `Observation.Counter`); kart "Building… n s" gösterir |
| Y | İyimser arayüz | `Orders`: tıklamada kart "queued", kredi "700 → 550 (queued)"; snapshot sayacı listeleyince onay, `rejected` gelince geri alma + kartta neden. Aktif duraklatmada komutlar bekletildiği için asıl değeri orada |

Tarayıcıda doğrulandı: satın alma → queued → building → online; postgres-exporter → DB metrikleri + "Create index" (new);
duraklatmada sıraya koyma ve devamda onay. Bulunan hata: Pixi `resizeTo` sadece pencere boyutunu dinliyor; dikey kaydırma
çubuğu çıkınca canvas taştı → host `ResizeObserver` ile izleniyor.

Kapsam dışı: sayaç ayarları (`keep_days`, `timeout_ms`…) komutta yok, varsayılanlar kullanılıyor; dil seçimi Dilim 6.
