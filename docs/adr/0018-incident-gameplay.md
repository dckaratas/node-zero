# ADR-0018: Olay anı oynanışı (Dilim 7)

- **Durum:** Kabul edildi; 7a uygulandı (7b müdahale, 7c kaldırma, 7d alarm sırada)
- **Tarih:** 2026-10-22

## Bağlam
Dilim 6 sonunda oyuncu sadece kart alıyor, dalga başlatıyor ve postmortem cevaplıyordu. Dalga sırasında karar baskısı yoktu,
kalkanın ne hızla eridiği görünmüyordu, bölümün sonu belirsizdi. Canlıya alma (eski Dilim 7) en sona alındı.

## Karar
| # | Konu | Seçim |
|---|---|---|
| AD | Kalkan görünürlüğü (7a) | Kalkan çubuğu + yüzde, **yakma hızı** (SRE burn rate) ve "boşalmaya kalan süre". %50 ve %25'te uyarı, 0'da tam ekran bildirim; dalga FAILED. Kodekste `burn-rate` |
| AE | Bölüm sonu (7a) | Son dalga kazanılıp postmortemi cevaplanınca özet: dalga başına kalkan, kredi, kök neden; 1–3 yıldız. Game over yok; başarısız dalga tekrar oynanır |
| AF | Müdahale modeli (7b) | `content/actions/*.yaml`, `RunAction(id, option)`; kredi yok, bedel mürettebat + bekleme süresi; her eylemin kendi zararı var |
| AG | Mürettebat (7b) | Dalga başına 8 puan; sayaçların `toil` alanı da bu bütçeden düşer |
| AH | İlk eylemler (7b) | Restart, disk temizleme (`truncate` / eski `.gz` / tuzak `rm` açık dosya), `pg_cancel_backend`, rollback |
| AI | Kaldırma (7c) | `RemoveCounter`; kurulum kadar sürer, iade yok, bakım biter; disk büyütme geri alınamaz; bağımlısı olan kalkmaz; içgörüler kalır |
| AJ | Alarm (7d) | sim-core'da deterministik kurallar (metrik, operatör, eşik, `for`), en fazla 5; tetiklenince belirgin uyarı, **duraklatma yok** |

## Değerlendirilen alternatifler
| Alternatif | Neden seçilmedi |
|---|---|
| Sadece kalkan çubuğu | Erimenin hızı görünmüyor; "ne kadar acil?" sorusu cevapsız |
| Kalıcı kayıp (game over) | Eğitim oyununda deneme cesaretini kırar |
| Müdahaleyi krediyle satmak | Gerçekte restart'ın parası yok; bedeli insan zamanı ve kesinti |
| Alarmı istemcide değerlendirmek | Sunucu bilmez; postmortem ve puanlamada kullanılamaz |

## Ek (Dilim 7a, 2026-10-22): kalkan ve bölüm sonu
- **Yakma hızı** `Observer`'da hesaplanır, state'e yeni alan eklemez: `lastFailedRps / (bütçe / dalga süresi)`.
  1 = bütçe tam dalga sonunda biter. Kalan süre = `kalan bütçe / lastFailedRps`. Trafik şekli değişken olduğu için hız
  "düzgün tempo"ya göredir (SRE tanımı); kalan süre o anki hıza göre tahmindir.
- Hazırlıksız dalga 3'te yakma hızı ~80× çıkıyor, kalkan ~1 saniyede bitiyor. %1 bütçe gerçekten acımasız; ders bu.
- **Dalga sonuçları** `Progress.results` (dalga numarasına göre sıralı; tekrar oynanan dalga eskisinin yerini alır ama tek
  postmortem kararını korur). `ChapterScore` özeti hesaplar: 1 yıldız bitirmek, 1 yıldız ortalama kalkan ≥ %50,
  1 yıldız tüm kök nedenler doğru.
- Son dalgadan sonrası reddedilir ("chapter 1 ends at wave 5"); bölüm numarası tehditlerin `chapter` alanından gelir.
- Protokol: snapshot'ta `burnRate`, `shieldEmptyInS` (her zaman); `results` delta alanı; `chapter` postmortem gibi
  (varsa gönderilir). Yeni fixture'lar: `snapshot-burning`, `snapshot-chapter`.
- İstemci: eşik geçişleri `shieldAlert` saf fonksiyonunda (her eşik dalga başına bir kez, aşağı yönde). Çöküş bildirimi
  3,5 sn ekranı kaplar (tıklayınca kapanır); postmortem diyaloğu onu bekler. Bölüm özeti postmortem kapandıktan sonra açılır.
- `burn-rate` kodeks maddesi log-storm'un maddelerine eklendi (ilk tehdit dalgasının postmortemiyle açılır).
