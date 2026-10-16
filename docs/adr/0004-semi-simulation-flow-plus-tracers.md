# ADR-0004: Yarı simülasyon — akış modeli + tracer istekler

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Dalga 3'te 300 rps × 10 Hz tick. Her isteği tek tek simüle etmek (discrete-event simulation)
pahalı ve oyun için gereksiz. Ama oyuncu "bir isteğin başına ne geldiğini" de görmeli.

## Karar
1. **Akış modeli:** Kaynaklar ortalama değerlerle (λ, ρ, kuyruk boyu) ve kuyruk teorisi formülleriyle hesaplanır
   (utilization / occupancy / fill — GDD §6).
2. **Tracer'lar:** Saniyede birkaç istek baştan sona tek tek simüle edilir ve Jaeger tarzı trace (span'ler)
   olarak gösterilir. Tracer'lar akış modelinin o anki değerlerini (W, bekleme) kullanır; sonucu etkilemez.

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Tam DES (her istek) | En gerçekçi, p99 doğal çıkar | CPU maliyeti, determinizm zor | Ölçek |
| Sadece formül | Ucuz | "Bir istek" hikâyesi yok, trace öğretilemez | Eğitim değeri |

## Sonuçlar
- Olumlu: Ucuz + hikâye anlatımı.
- Bedel: Formüller ortalama değer verir; p99 gibi kuyruk dağılımları yaklaşık olur (ADR-0013).
- Takip: Tracer örneklemesi seed'li rastgelelikle yapılmalı (ADR-0012).
