# Node Zero — Oyun Tasarım Dokümanı (GDD)

> Eğitim amaçlıdır. Gerçek teknoloji isimleri öğretici amaçla kullanılır; logo kullanılmaz.

## 1. Vizyon
Yıl **2187**. Oyuncu, yörüngedeki **NODE-01** sunucu istasyonunun Sistem Komutanı (SRE).
Merkezde sunucu var. Dışarıdan (trafik, saldırı) ve içeriden (bellek, thread, disk) gelen, yazılımın
yaşam döngüsü boyunca karşılaşılan problemlerle kolaydan zora mücadele edilir.

Web tabanlı, eğitici bir **bilim kurgu tower defense + sunucu tasarım/yönetim** oyunu.

### Benzer proje ve farkımız
**Server Survival** (GitHub ~6.4k yıldız): sadece istemcide çalışan bulut tower defense.

| Node Zero farkı | Açıklama |
|---|---|
| Server-authoritative simülasyon | Sonucu Java'daki sunucu belirler (ADR-0003) |
| Sunucunun iç dünyası | Thread, heap, bağlantı havuzu, GC — sadece kutular değil |
| Yaşam döngüsü | Deploy, migration, teknik borç, sertifika süresi |
| Sis perdesi | Gözlemlenebilirlik yoksa sorun görünmez |
| Postmortem | Kök neden analizi bir oyun mekaniği |
| Yapay zekâ | "Sentetik Zihinler" bölümü |

## 2. Kesinleşmiş kararlar
| # | Karar | Seçim | ADR |
|---|---|---|---|
| 1 | Zaman | Gerçek zamanlı + aktif duraklatma | [0001](adr/0001-real-time-with-active-pause.md) |
| 2 | Görünüm | 2D altıgen grid, TypeScript + PixiJS | [0002](adr/0002-2d-hex-grid-pixijs.md) |
| 3 | Simülasyon | Java, server-authoritative, saf fonksiyon motor (Model A → C) | [0003](adr/0003-server-authoritative-pure-engine.md) |
| 4 | Gerçekçilik | Akış modeli + formüller + tracer istekler | [0004](adr/0004-semi-simulation-flow-plus-tracers.md) |
| 5 | İçerik | Veri odaklı YAML | [0005](adr/0005-data-driven-yaml-content.md) |
| 6 | Oyuncu modu | Tek oyunculu → seed'li haftalık meydan okuma | [0006](adr/0006-single-player-then-seeded-weekly.md) |
| 7 | Başarısızlık | Hata bütçesi (kalkan = SLO) + postmortem | [0007](adr/0007-error-budget-and-postmortem.md) |
| 8 | Eğitim | Kodeks + postmortem soruları | [0008](adr/0008-codex-and-postmortem-education.md) |
| 9 | Dil | Baştan i18n; arayüz EN, içerik TR + EN | [0009](adr/0009-i18n-from-day-one.md) |
| 10 | Platform | Web | [0010](adr/0010-web-platform.md) |
| 11 | Yapay zekâ | "Sentetik Zihinler" + AIOps kulesi | [0011](adr/0011-synthetic-minds-ai-chapter.md) |
| 12 | Determinizm | Yasaklı API listesi, seed'li `SplittableRandom` | [0012](adr/0012-determinism-policy.md) |
| 13 | Gecikme formülü | Erlang-C (M/M/c) + ρ ≥ 1'de kuyruk boşalma süresi | [0013](adr/0013-latency-approximation.md) |
| 14 | Performans | Tick < 20 µs/oturum, JMH ile ölçülür | [0014](adr/0014-performance-budget.md) |
| 15 | Oyun sunucusu | Bellekte oturum + replay günlüğü, çekirdek başına tick shard'ı, ham WebSocket + JSON | [0015](adr/0015-game-server-architecture.md) |
| 16 | İstemci | Vite + TS + PixiJS, sözleşme fixture'ları, sunucuda filtrelenen katalog | [0016](adr/0016-browser-client.md) |
| 17 | Postmortem/kodeks/i18n | Zorunlu tek denemelik postmortem, sunucuda açılan kodeks, public i18n paketi | [0017](adr/0017-postmortem-codex-i18n.md) |
| 18 | Olay anı oynanışı | Yakma hızı ve bölüm sonu (7a), müdahale eylemleri + mürettebat (7b); kaldırma, alarm sırada | [0018](adr/0018-incident-gameplay.md) |

## 3. Oyun döngüsü
```
Hazırlık ──► Dalga ──► Postmortem ──► Araştırma ──┐
(yerleştir,   (izle,     (kök neden     (teknoloji   │
 yapılandır)   müdahale,  seç → bonus)   ağacı)      │
   ▲           duraklat)                              │
   └──────────────────────────────────────────────────┘
```

### Dalga, kalkan ve kredi (Dilim 3b)
| Kural | Değer |
|---|---|
| Kalkan (hata bütçesi) | Her dalga bir **istasyon gününün** bütçesiyle başlar: %1 × 100 rps × 1440 sn = 1440 istek (SLO %99). Taban yükte tam kesinti ~14 sn dayanır. Kalkan = harcanmamış bütçe oranı (ADR-0018 AK) |
| Başarısız istek | Ret (Tomcat, Hikari, CPU kuyruğu) + 429 + kesinti + statement timeout + yazma kilidi + dolu diskte yazma + **500 ms'den yavaş isteklerin payı**: ortalama gecikmeye göre üstel kuyruk, `e^(−500/ortalama)` (ADR-0018 AL) |
| Dalga sonu | Trafik şekli bitince `COMPLETE`; kalkan sıfırlanırsa `FAILED` (ödül yok, tam ekran bildirim). Kalkanın yanında yakma hızı ve kalan süre (ADR-0018) |
| Bölüm sonu (Dilim 7a) | Son dalga kazanılıp postmortemi cevaplanınca özet + 1–3 yıldız (bitirmek, ortalama kalkan ≥ %50, tüm kök nedenler doğru). Game over yok |
| Ödül | `round(300 × kalan kalkan) − kurulu karşı aksiyonların upkeep toplamı` |
| Postmortem (Dilim 6a) | Tehditli dalga bitince (COMPLETE/FAILED) açılır; cevaplanmadan sonraki dalga başlamaz; tek deneme, tehdit başına bir kez; doğru → +100 kr; cevap önceden gönderilmez (ADR-0017) |
| Kalıcı tehditler | `persists: true` olanlar (DEBUG log, büyüyen tablo, günlük job) sonraki dalgalarda da etkili: teknik borç |
| Karşı aksiyon zamanlaması | Kurulum süresince geçiş yan etkileri (kesinti, yazma kilidi, ek I/O); sonra etkin. Hazırlıkta kurmak ucuzdur |
| Teşhis (insight) | `requires_insight` olan aksiyonlar ancak teşhis açıldıktan sonra alınır (ör. `pg-stat-statements`) |

Etki türleri Java'da (`EffectKind`): parametre değiştirenler, donanım değiştirenler (çekirdek/thread/disk ekleme),
sadece kurulumda zarar verenler ve anlatısal olanlar. YAML bilinmeyen tür kullanırsa kurallar yüklenmez.

### Sis perdesi (Dilim 3c)
Simülasyon her şeyi hesaplar; oyuncuya sunucuda filtrelenmiş bir `Observation` gider (istemci gizli veriyi hiç görmez).

| Modül | Maliyet | Gerekir | Açar |
|---|---|---|---|
| Hiçbiri | — | — | Kalkan + gecikmeli kullanıcı şikâyeti (`delay_s` boyunca hata sürerse) |
| Sensör Dizisi (Prometheus) | 200 kr, 5 sn | — | `request_rate`, `cpu_utilization`, `tomcat_threads_busy`, `hikaricp_connections_*`, `disk_usage_ratio`, `disk_fill_rate` |
| Arşiv Sensörleri (postgres_exporter) | 100 kr, 3 sn | Prometheus | `db_cpu_utilization`, `db_io_utilization` + teşhis `pg-stat-statements` |
| Komuta Ekranı (Grafana) | 150 kr, 5 sn | Prometheus | `latency_avg`, `latency_p99`, `error_rate`, `disk_forecast`, `traffic_heatmap` + teşhis `traffic-heatmap` |
| Grafana + exporter | — | ikisi | `slow_queries` |

Kurallar: tehdit YAML'ındaki her metrik katalogda olmalı ve doğru bölümde durmalı (ham metrik `prometheus:`, panel `grafana:`).
Görünüm hiçbir zaman tehdit kimliği veya kök neden içermez (testle korunur).

### Temel mekanikler
| Mekanik | Gerçek dünya karşılığı |
|---|---|
| Sis perdesi | İzleme yoksa sorun görünmez; sinyal sadece gecikmeli kullanıcı şikâyeti |
| Kalkan enerjisi | Hata bütçesi (SLO %99 → %1 bütçe) |
| Mürettebat yorgunluğu | Toil — elle yapılan tekrar eden işler |
| Gecikmeli sonuç | Teknik borç: bugünkü kestirme, birkaç dalga sonra patlar |
| `side_effect` | Hiçbir çözüm bedava değil (maliyet, kesinti, yeni risk) |

## 4. Tema eşlemesi
| Gerçek kavram | Oyun içi |
|---|---|
| CPU | Reaktör |
| RAM / Heap | Bellek Halkası |
| Disk | Kargo Ambarı |
| Thread havuzu | Mürettebat |
| Bağlantı havuzu (HikariCP) | Yanaşma Limanları |
| Load balancer | Yanaşma Kontrol Kulesi |
| Cache (Redis) | Hızlı Erişim Deposu |
| DB (PostgreSQL) | Arşiv Kalesi |
| Kuyruk (Kafka) | Bekleme Yörüngesi |
| WAF | Kalkan Filtresi |
| Prometheus | Sensör Dizisi |
| Grafana | Komuta Ekranı (HUD) |
| HTTP isteği | Kargo mekiği |
| DDoS | Drone sürüsü |

## 5. Simülasyon parametreleri
| Parametre | Değer |
|---|---|
| Tick | 10 Hz (`DT = 0.1` sn) |
| Zaman ölçeği | 1 gerçek sn = 1 oyun dk |
| Başlangıç bütçesi | 1000 kredi |
| SLO | %99 |
| NODE-01 CPU | 4 çekirdek, çekirdek başına μ = 50 rps (S = 20 ms) → kapasite 200 rps |
| Tomcat | `maxThreads = 200`, `acceptCount = 100` |
| HikariCP | `maximumPoolSize = 10` |
| Koloniler | Mars (istasyonla aynı saat dilimi), Europa (+12 saat) |

## 6. Üç kaynak modeli (motor çekirdeği)
| Model | Ne zaman | Formül |
|---|---|---|
| **utilization** | İş hızıyla ölçülen kaynaklar (CPU, disk I/O, ağ) | `ρ = λ/(c·μ)`; `W = S + Wq` (Erlang-C, M/M/c); ρ ≥ 1 ise kuyruk `(λ − cμ)` hızıyla büyür |
| **occupancy** | Sınırlı slot, iş slotu belli süre tutar (thread, DB bağlantısı, lock) | `L = λ·W` (Little); havuz boyutuyla karşılaştır → pending / reject |
| **fill** | Zamanla biriken (disk alanı, bellek sızıntısı, kuyruk) | `level += (in − out)·dt`; kapasiteye ulaşınca failure |

Gecikme Erlang-C ile hesaplanır; ρ ≥ 1 için `W = W(ρ=0.99) + queue / (c·μ)` (ADR-0013).
Sezgi: tek kasada `S/(1−ρ)`; c kasa ortak kuyrukla çok daha az bekletir (havuzlama etkisi).

| ρ (NODE-01, c=4, μ=50) | W |
|---|---|
| 0.50 | 21.7 ms |
| 0.90 | 59.4 ms |
| 0.99 | 509.0 ms |

### İstek hattı (NODE-01)
```
istek ─► Tomcat thread'leri ─► CPU ─► HikariCP ─► DB CPU (+ yazmalar: DB I/O)
              (200 / 100)        (4×50)    (10)        (4 çekirdek)
   └─ her istek log yazar ─► Disk (100 MB, 75 MB dolu)
```
- İstek hızları aşağı doğru aynı tick'te akar; her havuz arkasına en fazla `havuz / tutma süresi` kadar iletir.
- Thread tutma süresi = CPU gecikmesi + havuz bekleme + sorgu süresi (önceki tick, sönümlenmiş).
- İçerik sadece **kök nedeni** tanımlar (ör. yavaş sorgu → `db_cpu` maliyeti); havuz doygunluğu hattan doğar.

### Model seçme rehberi
| Soru | Model |
|---|---|
| Saniyede ne kadar iş yapılabiliyor? | utilization |
| Sabit slot var ve iş slotu süre boyunca tutuyor mu? | occupancy |
| Zamanla birikiyor, kendiliğinden boşalmıyor mu? | fill |

Bir kaynak birden fazla modelde olabilir (disk: alan = fill, I/O = utilization).

## 7. Bölüm 1 "Ateşleme" — tehditler
| Dalga | id | Model | Özet | Karşı aksiyonlar | Doğru kök neden |
|---|---|---|---|---|---|
| 2 | `log-storm` | fill (disk) | Deploy sonrası DEBUG log açık kaldı; 200 rps × 2 KB. Prometheus yoksa görünmez | Log seviyesini INFO yap (toil), logrotate (150 kr), disk büyüt (300 kr, sorunu erteler) | Yanlış log seviyesi |
| 3 | `traffic-surge` "Koloni Festivali" | utilization (CPU) | Habersiz kampanya, ×3 trafik (ramp 20 sn / plato 60 sn / decay 40 sn). Dalga 2'de iletişim kanalına ipucu | Reaktör yükseltme (+4 çekirdek, 400 kr, 5 sn kesinti), Nginx `limit_req` (100 kr, 429 döner). LB kilitli (Bölüm 3) | Kapasite planlanmadı (DDoS aldatıcı) |
| 4 | `slow-endpoint` "Arşiv Kayması" | occupancy (thread + Hikari) | `orders` 1M satırı geçince indekssiz sorgu yavaşlar (rows/500 ms). **Uygulama CPU'su düşük (aldatıcı)**, DB CPU yüksek | `statement_timeout` (sigorta), `CREATE INDEX` (blocking: yazma kilidi / `CONCURRENTLY`: yavaş ama güvenli; `pg_stat_statements` ile teşhis gerekir), `maxThreads` artırmak (semptom tedavisi) | Eksik indeks + veri büyümesi |
| 5 | `nightly-report-job` "Gece Defteri" | utilization (DB I/O) + occupancy (Hikari 8/10) | Cron `0 3 * * *` istasyon saati = Europa'da 15:00 (yoğun saat). Süre rows/50k dk | Zamanlamayı değiştir (heatmap gerekir), Spring Batch chunking + throttle, bulkhead (job için ayrı pool=2), **tuzak: naive retry (yük ×2)**. Read replica kilitli (Bölüm 3) | Job, kullanıcı trafiğiyle aynı kaynakları paylaştı ve yoğun saate denk geldi |

## 8. İçerik şeması
Dosyalar `sim-core/src/main/resources/content/` altında; yükleme sırası `index.yaml`'da.
Kurallar (yükleyici zorlar):

| Kural | Örnek hata |
|---|---|
| Bilinmeyen alan yok | `threat.colour: unknown field 'colour'` |
| Modele göre zorunlu alan | utilization → `service_time_ms`, occupancy → `hold_time_ms`, fill → `units_per_request` |
| Koşul sözdizimi `metrik op sayı/metrik` | `rho >> 0.9` reddedilir |
| Postmortem'de tam 1 doğru seçenek | |
| id = dosya adı; counter referansları çözülmeli | `unknown counter 'magic-fix'` |
| Her metin anahtarı EN ve TR'de var, kullanılmayan anahtar yok | `missing TR text for 'pm.x'` |

Opsiyonel alanlar: `data` (tablo büyümesi; `per_row_ms` için gerekli), `job` (batch süresi),
`trigger.schedule` (cron + saat dilimi), `consumes[].slots` (job'ın tuttuğu bağlantı sayısı).

### Tehdit (`content/threats/*.yaml`)
```yaml
threat:
  id: traffic-surge
  name: { en: "Colony Festival", tr: "Koloni Festivali" }
  chapter: 1
  trigger: { wave: 3, event: marketing-campaign, warning: { wave: 2, channel: comms, text_key: "comms.festival_teaser" } }
  traffic:
    baseline_rps: 100
    shape:
      - { phase: ramp,    duration_s: 20, multiplier: "1.0 -> 3.0" }
      - { phase: plateau, duration_s: 60, multiplier: 3.0 }
      - { phase: decay,   duration_s: 40, multiplier: "3.0 -> 1.0" }
    mix: { read: 0.8, write: 0.2 }
  consumes:   # liste: bir tehdit birden fazla kaynağı tüketebilir
    - { resource: cpu, model: utilization, service_time_ms: 20 }
  effects:
    - { when: "rho >= 0.7", effect: latency_rise,    visual: reactor_glow_orange }
    - { when: "rho >= 0.9", effect: slo_breach_risk, visual: reactor_glow_red }
    - { when: "rho >= 1.0", effect: queue_growth }
    - { when: "queue >= accept_count", effect: reject_connections }
  visibility:
    without_monitoring: { signal: user_complaints, delay_s: 30 }
    prometheus: [cpu_utilization, request_rate]
    grafana:    [latency_avg, error_rate, dashboards]
  counters: [vertical-scale-cpu, nginx-rate-limit]
  locked_counters: [{ id: load-balancer, unlocks_in_chapter: 3, hint_key: "hint.lb_needs_two_nodes" }]
  postmortem:
    question_key: "pm.traffic_surge.q"
    options:
      - { id: a, key: "pm.slow_code" }
      - { id: b, key: "pm.capacity_not_planned", correct: true }
      - { id: c, key: "pm.database" }
      - { id: d, key: "pm.ddos" }
    reward: { unlock: capacity-forecast-panel }
  codex: [utilization-law, vertical-vs-horizontal, rate-limiting, http-429-vs-503, tomcat-threads]
```

### Counter (`content/counters/*.yaml`) alanları
| Alan | Anlamı |
|---|---|
| `id`, `name{en,tr}` | Kimlik ve görünen ad |
| `real_tech` | Gerçek teknoloji (ör. Nginx `limit_req`) |
| `cost{credits, upkeep_per_wave}` | Kurulum ve dalga başı bakım maliyeti |
| `toil` | Mürettebat yorgunluğu bedeli |
| `apply_time_s` | Devreye alma süresi |
| `config` | `player_editable` parametreler |
| `options` | Aynı çözümün farklı uygulanışları (ör. `CREATE INDEX` vs `CONCURRENTLY`) |
| `requires_insight` | Önce teşhis gerekir (ör. `pg_stat_statements`) |
| `effect` / `side_effect` | Etki ve bedeli |
| `tradeoff_key` | Kodeks açıklaması |
| `better_alternative` | Postmortem'de önerilecek daha iyi çözüm |
| `codex` | İlgili kodeks maddeleri |

## 9. Kampanya taslağı
| # | Bölüm | Tema |
|---|---|---|
| 1 | Ateşleme | Tek sunucu |
| 2 | Gözcü Kuleleri | Gözlemlenebilirlik |
| 3 | Çoğalan Kalabalık | LB, cache, read replica |
| 4 | Kalenin İçi | JVM: GC, bellek sızıntısı, deadlock, virtual thread → darboğaz Hikari'ye kayar |
| 5 | Kervan Yolu | Canary, rollback, migration |
| 6 | Kuşatma | Güvenlik |
| 7 | Bağımlı Krallıklar | Circuit breaker, retry + backoff, idempotency |
| 7.5 | Sentetik Zihinler | GPU OOM, token maliyeti, prompt injection, RAG, agent döngüsü |
| 8 | Kayıp Bölge | DR, RPO/RTO |
| 9 | Eski Ruhlar | Teknik borç, sertifika |
| Final | — | Gerçek postmortemler |

## 10. Yol haritası
| Dilim | İçerik |
|---|---|
| 0 | Repo: CLAUDE.md, GDD, ADR'ler, Maven iskeleti, CI |
| 1a | sim-core: GameState, Simulation, UtilizationModel |
| 1b | OccupancyModel |
| 1c | FillModel (kullanıcı yazar) |
| 2 | YAML içerik yükleyici + Bölüm 1 tehdit/counter dosyaları + şema doğrulama |
| 3 | Dalga yöneticisi, hata bütçesi, komutlar, sis perdesi |
| 4 | Spring Boot oyun sunucusu + WebSocket (5 Hz, değişince gönderilen snapshot; ADR-0015 eki) |
| 5 | TypeScript/PixiJS istemci (altıgen harita, HUD, interpolasyon; ADR-0016; 5b sayaç paneli + iyimser arayüz) |
| 6 | Postmortem + Kodeks + i18n |
| 7 | Canlıya alma: Docker, VPS, Prometheus/Grafana ile oyunun kendisini izleme |
