# ADR-0010: Platform = Web

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Portföy projesi: mülakatı yapan kişi bir linke tıklayıp hemen oynayabilmeli. Kurulum adımı kitleyi düşürür.

## Karar
Oyun tarayıcıda çalışır. Sunucu Spring Boot + WebSocket (Dilim 4), istemci TS + PixiJS (Dilim 5).

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Masaüstü (Electron/JavaFX) | Çevrimdışı | Kurulum gerekir, dağıtım zor | Erişilebilirlik |
| Mobil | Geniş kitle | Karmaşık HUD küçük ekrana sığmaz | UX |

## Sonuçlar
- Olumlu: Sıfır kurulum, kolay paylaşım.
- Bedel: Sunucu barındırma maliyeti ve gecikme yönetimi (Model C ileride çözer, ADR-0003).
