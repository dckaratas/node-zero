# ADR-0007: Başarısızlık = hata bütçesi (kalkan) + postmortem

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Klasik tower defense'te "can" biter ve oyun kaybedilir. Gerçek SRE dünyasında hedef sıfır hata değildir;
SLO içinde kalmaktır. Hata bütçesi aynı zamanda risk alma (deploy, deney) izni demektir.

## Karar
- Kalkan enerjisi = hata bütçesi. SLO %99 → dalga boyunca isteklerin %1'i başarısız olabilir.
- Başarısız/SLO dışı her istek kalkandan düşer. Kalkan biterse dalga kaybedilir.
- Her dalga sonunda **postmortem**: oyuncu kök nedeni seçer; doğruysa bonus kazanır (suçlamasız kültür).

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Can puanı (HP) | Tanıdık | Gerçek dünyayla bağı yok | Eğitim değeri |
| Uptime yüzdesi | Basit | Kısmi bozulmayı (yavaşlık) yakalamaz | SLO daha zengin |

## Sonuçlar
- Olumlu: SLO/SLI/hata bütçesi kavramları oynanarak öğrenilir.
- Bedel: "Başarısız istek" tanımı netleşmeli (5xx, 429, gecikme eşiğini aşanlar).
- Uygulama (Dilim 3b): bütçe dalga başına; gecikme SLO'su tick ortalaması > 500 ms (p99 değil, ADR-0013).
  Kalkan sıfırlanınca dalga `FAILED`. Fizik testleri ve benchmark için `GameBalance.PRACTICE` dalgayı sonuna kadar oynatır.
