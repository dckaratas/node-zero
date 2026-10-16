# ADR-0006: Önce tek oyunculu, sonra seed'li haftalık meydan okuma

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Çok oyunculu oyun; senkronizasyon, eşleştirme ve hile önleme gibi büyük maliyetler getirir.
Yine de oyuncuların sonuçlarını karşılaştırabileceği bir sosyal katman değerli.

## Karar
1. Önce tek oyunculu kampanya.
2. Sonra **haftalık meydan okuma**: herkes aynı seed ile aynı senaryoyu oynar; skorlar karşılaştırılır.
   Sunucu, oyuncunun komut listesini aynı seed ile yeniden oynatarak (replay) skoru doğrular.

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Gerçek zamanlı çok oyunculu | Etkileyici | Netcode, eşleştirme maliyeti | Kapsam |
| Sadece tek oyunculu | Basit | Tekrar oynama motivasyonu düşük | Sosyal katman eksik |

## Sonuçlar
- Olumlu: Determinizm (ADR-0012) sayesinde replay = doğrulama; hile zorlaşır.
- Bedel: Komut günlüğü saklanmalı; motor sürümü değişirse eski replay'ler bozulabilir → sürümlü kurallar.
