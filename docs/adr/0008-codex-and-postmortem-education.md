# ADR-0008: Eğitim katmanı = Kodeks + postmortem soruları

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Oyunun amacı öğretmek, ama ders anlatımı oyunu bölmemeli. Öğrenme en iyi, bir problemi
yaşadıktan hemen sonra gerçekleşir.

## Karar
- **Kodeks:** Oyun içi wiki. Her tehdit ve counter `codex: [...]` ile maddelere bağlanır;
  ilk karşılaşmada açılır ve tek tıkla okunabilir.
- **Postmortem soruları:** Dalga sonrası çoktan seçmeli kök neden sorusu; aldatıcı seçenekler
  gerçek yanılgılardan seçilir (ör. ani trafik artışında "DDoS").

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Ayrı tutorial modu | Yapılandırılmış | Oyunu böler, atlanır | Akış |
| Harici blog/doküman | Ucuz | Bağlam kopar | Yerinde öğrenme |

## Sonuçlar
- Olumlu: Öğrenme oyun döngüsüne gömülü.
- Bedel: Her kodeks maddesi TR + EN yazılmalı (ADR-0009).
