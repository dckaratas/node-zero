# ADR-0017: Postmortem, kodeks ve i18n uygulaması (Dilim 6)

- **Durum:** Kabul edildi (6a uygulandı; 6b kodeks, 6c dil seçici)
- **Tarih:** 2026-10-22

## Bağlam
ADR-0007 postmortemi, ADR-0008 kodeksi, ADR-0009 i18n'i ilke olarak koydu. İçerikte postmortem soruları (4 tehdit × 4 seçenek,
EN/TR) hazırdı; kodeks maddeleri atıf olarak vardı ama yazılmamıştı; `reward.unlock` değerleri hiçbir şeye bağlı değildi.

## Karar
| # | Konu | Seçim |
|---|---|---|
| Z | Postmortem akışı | Tehditli dalga bitince (COMPLETE **veya** FAILED) postmortem açılır (`Progress.postmortem`). Cevaplanmadan sonraki dalga başlamaz. Sessiz dalgada yok. **Tek deneme**; aynı tehdit bir kez incelenir (`reviewedThreats`; tekrar oynanan dalga yeniden sormaz). Komut `AnswerPostmortem` → replay'e girer. Doğru seçenek **cevaptan önce gönderilmez** (`Observation.Postmortem.correct = null`) |
| AA | Ödül | Doğru kök neden → `GameBalance.postmortemBonus` (100 kr); yanlış → ceza yok (suçlamasız). `reward.unlock` ileride "aksiyon maddesi" (tekrarı önleyen kalıcı iyileştirme) olarak tasarlanacak |
| AB | Kodeks (6b) | `content/codex/*.yaml`, EN + TR; sunucuda `Progress` ile açılır; `GET /api/sessions/{id}/codex?lang=` sadece açılmışları döner |
| AC | i18n | Arayüz EN. Gizli olmayan anahtarlar (`pm.*`, `hint.*`, `comms.*`) `GET /api/i18n/{lang}` paketiyle (ETag, 1 saat public cache); istemci anahtarı çözer, bilinmeyen anahtar kendisi olarak görünür. Kapılı içerik (katalog, kodeks) sunucuda çözülür. Dil seçici 6c |

## Değerlendirilen alternatifler
| Alternatif | Neden seçilmedi |
|---|---|
| Atlanabilir postmortem | Atlanır; eğitim katmanı boşa gider |
| Çoklu deneme | Tahmini ödüllendirir |
| Yeni `WaveState.Phase.POSTMORTEM` | Faz dalga trafiğini anlatıyor; postmortem dalgadan bağımsız ve cevaptan sonra da (sonuç gösterimi) yaşıyor → ayrı alan daha sade |
| Tüm metinleri tek pakette göndermek | `tradeoff.create_index` gibi metinler çözümü ele verir (W kuralı) |

## Sonuçlar
- Tarayıcıda doğrulandı: log-storm sonrası diyalog, Escape ile kapanmıyor, yanlış cevap → kırmızı/yeşil işaret, kredi değişmez,
  "Start wave" cevaptan sonra açılır.
- **Tuzak:** Chrome, kullanıcı etkileşimi olmadan açılmış `<dialog>`'da `cancel` olayının `preventDefault`'unu yok sayar
  (close request kuralı). Çözüm: `close` olayında durum unutulur, soru açıksa bir sonraki karede yeniden açılır.
- Eski iki test (`WaveAndCounterTest`) postmortemi atlıyordu; aynı tick'te önce cevap, sonra dalga.
