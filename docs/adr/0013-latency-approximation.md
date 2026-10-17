# ADR-0013: Gecikme formülü — Erlang-C (M/M/c)

- **Durum:** Kabul edildi (revize edildi: 2026-10-16, M/M/1 yaklaşımının yerini aldı)
- **Tarih:** 2026-10-16

## Bağlam
Utilization modelinde ortalama yanıt süresi `W` gerekiyor. NODE-01'de c = 4 çekirdek var.
İlk taslakta tek kasa (M/M/1) formülü `W = S/(1−ρ)` seçilmişti. Ölçünce c = 4 için gecikmeyi
**1.8×–3.9×** fazla gösterdiği görüldü:

| ρ | M/M/1 yaklaşımı | Erlang-C (M/M/4) |
|---|---|---|
| 0.50 | 40.0 ms | 21.7 ms |
| 0.90 | 200.0 ms | 59.4 ms |
| 0.99 | 2000.0 ms | 509.0 ms |

Sebep **havuzlama etkisi** (pooling): ortak kuyruklu c sunucuda bir istek ancak c sunucunun hepsi
meşgulse bekler. Bu fark oyuncuya yanlış bir ders öğretirdi ("çok çekirdekli sunucu %90'da 10× yavaşlar").

## Karar
```
a   = λ / μ                       (teklif edilen yük, Erlang)
ρ   = λ / (c·μ)
B₀  = 1;  Bₖ = a·Bₖ₋₁ / (k + a·Bₖ₋₁),  k = 1..c     (Erlang-B rekürsiyonu)
C   = B_c / (1 − ρ·(1 − B_c))                          (Erlang-C: bekleme olasılığı)
Wq  = C / (c·μ − λ)
W   = S + Wq
```
- **ρ ≥ 1:** Kararlı durum yoktur. Taban gecikme ρ = 0.99'daki `W` değerinde sabitlenir;
  üstüne kuyruğun boşalma süresi eklenir: `W = W(0.99) + queue / (c·μ)`. Sonlu ve zamanla büyüyen bir değer.
- Kuyruk `queue += (λ − c·μ)·dt`, `[0, queueLimit]` aralığında tutulur; taşan kısım `rejectedRps` olur.
- Kodekste `S/(1−ρ)` "tek kasa sezgisi" olarak gösterilir; havuzlama etkisi ayrı bir kodeks maddesi olur.

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| M/M/1 `S/(1−ρ)` | En basit, temkinli | c = 4'te 2–4× yanlış; yanlış sezgi öğretir | Doğruluk ve eğitim |
| Sakasegawa yaklaşımı | Kapalı form | `pow` gerektirir, yine de yaklaşık | Erlang-C hem kesin hem ucuz |
| Tam DES | Dağılım (p99) da çıkar | Pahalı (ADR-0004) | Ölçek |

## Sonuçlar
- Olumlu: M/M/c için kesin sonuç; maliyeti c adımlık bir döngü; sadece `+ − × ÷` (ADR-0012 açısından güvenli).
- Bedel: M/M/c varsayımı (Poisson geliş, üstel servis süresi). Gerçek servis süreleri daha düzenliyse
  gerçek gecikme biraz daha düşük, kuyruklu dağılımlarda (heavy tail) daha yüksek olur.
- Occupancy (Dilim 1b): havuz da M/M/c'dir (`c = poolSize`, `μ = 1/holdTime`); Erlang-C kodu paylaşılır.
  `pending = min(Lq + backlog, queueLimit)`, sonlu kuyruklu M/M/c/K modelinin basit bir yaklaşımıdır.
  Sınıra yakın bölgede kesin M/M/c/K'ye geçmek gerekirse o zaman değerlendirilecek.
- İstek hattı (Dilim 3a): havuzun arkasındaki katman **kapalı sistemdir** (en fazla `poolSize` istek içeride).
  DB gecikmesi `min(Erlang-C, max(S, poolSize / X))` ile sınırlanır ve aynı tick'te hesaplanır. Açık kuyruk formülü
  tek başına kullanılınca bir tick'lik gecikmeli geri besleme salınım üretmişti (DB CPU %7 ↔ %120).
  Bilinen sınırlama: `DB_CPU` kaynağının kendi gösterdiği gecikme hâlâ açık kuyruk değeridir.
- Sis perdesi (Dilim 3c): `latency_p99` paneli üstel kuyruk yaklaşımıyla `ln(100) × ortalama` gösterir; kesin değildir.
- Takip: p99 için ileride `P(W > t) = C·e^{−(cμ−λ)t}` kullanılabilir (`StrictMath.exp` ile).
