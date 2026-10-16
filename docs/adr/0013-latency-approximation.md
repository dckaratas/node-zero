# ADR-0013: Gecikme formülünde bilinçli yaklaşım

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Utilization modelinde ortalama yanıt süresi gerekiyor. Kesin çözüm c sunuculu M/M/c için
**Erlang-C** formülüdür; ancak hesaplaması karmaşık ve oyuncu için açıklaması zordur.
Ayrıca `S/(1−ρ)` ρ = 1'de sonsuza gider (sıfıra bölme).

## Karar
```
ρ = λ / (c·μ)
W = S / (1 − min(ρ, 0.99)) + queue / (c·μ)
queue += max(0, λ − c·μ)·dt − boşalma, [0, queueLimit] aralığında
```
- ρ < 1: M/M/1 tarzı yaklaşım, oyuncunun kodekste göreceği formülle aynı.
- ρ ≥ 1: taban gecikme 0.99'da sabitlenir; artış kuyruğun boşalma süresinden gelir (sonlu, sürekli büyüyen).
- Kuyruk `queueLimit`'e ulaşınca fazlası reddedilir (`rejectedRps`).

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Erlang-C | Kesin | Karmaşık; c > 1'de daha **düşük** gecikme verir | Öğretilebilirlik |
| ρ ≥ 1'de sabit "sonsuz" | Basit | NaN/∞ HUD'u ve hata bütçesini bozar | Sağlamlık |

## Sonuçlar
- Olumlu: Tek formül, sonlu değer, ρ ↑ → W ↑ (monoton).
- Bedel: c > 1'de gecikme olduğundan yüksek çıkar (temkinli tarafta).
- Takip: Mülakat sorusu olarak hazırla: "Neden Erlang-C değil?" → öğretilebilirlik + temkinli tahmin.
