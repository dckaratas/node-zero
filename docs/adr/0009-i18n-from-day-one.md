# ADR-0009: Baştan i18n — arayüz EN, içerik TR + EN

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Hedef kitle hem Türkçe konuşan öğrenenler hem de uluslararası portföy izleyicisi.
Sonradan i18n eklemek, kod içine gömülü metinlerin tek tek bulunması demek.

## Karar
- Kullanıcıya görünen her metin bir anahtarla (`text_key`, `question_key`) veya `{en, tr}` nesnesiyle tanımlanır.
- Arayüz metinleri EN; oyun içeriği (tehditler, kodeks, postmortem) TR + EN.
- sim-core metin üretmez, sadece anahtar döner; çeviriyi istemci yapar.

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Sadece EN | Basit | Türkçe öğrenen kitle kaybolur | Hedef kitle |
| Sonradan i18n | Hızlı başlangıç | Pahalı refactor | Teknik borç |

## Sonuçlar
- Olumlu: Motor dilden bağımsız; yeni dil eklemek sadece içerik işi.
- Bedel: Dilim 2'deki şema doğrulaması, her anahtarın her iki dilde de tanımlı olmasını kontrol etmeli.
