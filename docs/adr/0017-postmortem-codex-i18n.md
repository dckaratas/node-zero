# ADR-0017: Postmortem, kodeks ve i18n uygulaması (Dilim 6)

- **Durum:** Kabul edildi, uygulandı (6a postmortem, 6b kodeks, 6c dil seçici)
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

## Ek (Dilim 6b, 2026-10-22): kodeks
- İçerik: `content/codex/chapter-1.yaml`, 23 madde × EN/TR (başlık + 2 paragraf düz metin). Yükleyici iki yönde katı:
  her `codex:` atfı bir maddeye çözülmeli **ve** hiçbir madde yetim kalmamalı (i18n'deki "kullanılmayan anahtar yok" kuralının eşi).
- Açılma (`Progress.codex`, deterministik, replay ile aynı): **sayaç satın alınınca** o sayacın maddeleri; **tehdidin maddeleri
  postmortem cevaplanınca** (yanlış cevapta da; açıklama ödül değil, öğrenme).
- **AB'den sapma:** AB "tehdit ilk görüldüğünde" diyordu. Uygularken `log-storm` → `log-levels` gibi maddelerin postmortem
  sorusunun cevabını ele verdiği görüldü; bu yüzden tehdit maddeleri cevaptan sonra açılıyor. Amaç (deneyimden hemen sonra
  yerinde öğrenme, ADR-0008) aynı; zamanlama spoiler'ı önlemek için kaydırıldı.
- Protokol: snapshot'ta `codex` (id listesi, `insights`/`counters` gibi değişmediyse yok); metinler
  `GET /api/sessions/{id}/codex?lang=` ile sadece açılmışlar için (ETag/304). İstemci liste değişince yeniden çeker.
- İstemci: `<details>` tabanlı kodeks bölümü (klavye/ekran okuyucu ücretsiz), en yeni üstte, açılana kadar "new"; yenilemede
  açık makaleler açık kalır. Uzun modül listesinin altında kaybolmasın diye panelde Modules'un üstünde.
- Teknik içerik Claude tarafından yazıldı; **kullanıcı teknik doğruluk açısından gözden geçirecek** (öğrenme notlarına eklendi).

## Ek (Dilim 6c, 2026-10-22): içerik dili seçici
- Başlıkta EN/TR seçici; arayüz EN kalır. Seçim `localStorage`'da (erişim hata verirse sessizce yok sayılır); kayıt yoksa
  tarayıcı dili `tr` ile başlıyorsa Türkçe, değilse İngilizce.
- Değişince: i18n paketi, katalog ve kodeks yeni dilde yeniden çekilir; postmortem diyaloğu yeni metinlerle yeniden kurulur.
  "new" rozetleri ve keşifler id'ye bağlı olduğu için dil değişince kaybolmaz.
- İçerik alanlarına (`countersHost`, `codexHost`, diyalog) `lang` niteliği verilir: ekran okuyucu Türkçe metni Türkçe okur.
