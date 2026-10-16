# ADR-0002: 2D altıgen grid, TypeScript + PixiJS

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Merkezde sunucu, etrafında modüller (kuleler) ve dışarıdan gelen trafik var. Komşuluk ve mesafe
hesapları (ör. LB'nin hangi node'lara yakın olduğu) görsel olarak anlaşılır olmalı.

## Karar
Harita 2D altıgen grid. İstemci TypeScript + PixiJS (WebGL) ile yazılacak (Dilim 5).

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Kare grid | Basit | Çapraz komşuluk belirsiz (4 vs 8) | Altıgende 6 komşu eşit uzaklıkta |
| Phaser | Tam oyun motoru | Fizik, sahne yönetimi gereksiz; simülasyon zaten sunucuda | Sadece render gerekiyor |
| 3D (Three.js) | Görsel etki | Maliyet yüksek, eğitim değerine katkısı az | Kapsam |
| DOM/SVG | Kolay | Yüzlerce mekik/partikülde yavaş | Performans |

## Sonuçlar
- Olumlu: Hafif, istemci tamamen "aptal" render katmanı olabilir.
- Bedel: Altıgen koordinat sistemi (axial/cube) öğrenilmeli.
- Takip: Grid koordinatları sim-core'da da temsil edilecek (Dilim 3, modül yerleştirme).
