# ADR-0011: Yapay zekâ — "Sentetik Zihinler" bölümü + AIOps kulesi

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
LLM tabanlı servisler, canlı ortamda klasik web servislerinden farklı problemler yaratır:
GPU belleği, token maliyeti, güvenilmeyen girdinin talimata dönüşmesi.

## Karar
- Bölüm 7.5 "Sentetik Zihinler": GPU OOM (fill), token maliyeti (bütçe), prompt injection (güvenlik),
  RAG (retrieval gecikmesi, utilization), agent döngüsü (sonsuz döngü / maliyet patlaması).
- AIOps kulesi: anomali tespiti yapan kule; doğruluğu %100 değildir (yanlış alarm = toil).

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| AI konusunu dışarıda bırakmak | Kapsam küçük | Güncel değil | Portföy değeri |
| Oyunda gerçek LLM kullanmak | Etkileyici | Maliyet, determinizm kırılır | ADR-0012 |

## Sonuçlar
- Olumlu: Güncel konu, mevcut 3 kaynak modeline oturuyor.
- Bedel: AI davranışı simüle edilir, gerçek model çağrılmaz.
