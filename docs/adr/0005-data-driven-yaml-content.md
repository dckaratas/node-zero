# ADR-0005: Veri odaklı YAML içerik

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-16

## Bağlam
Onlarca tehdit ve karşı aksiyon (counter) olacak. Her biri için Java kodu yazmak motor ile içeriği
birbirine bağlar; içerik eklemek derleme gerektirir.

## Karar
Tehditler `content/threats/*.yaml`, karşı aksiyonlar `content/counters/*.yaml` dosyalarında tanımlanır
(şema: GDD §8). Motor sadece genel kavramları bilir: kaynak modeli, trafik şekli, etki koşulu.
Yükleme Jackson YAML ile, yükleme anında şema doğrulamasıyla yapılır (Dilim 2).

## Değerlendirilen alternatifler
| Alternatif | Artı | Eksi | Neden seçilmedi |
|---|---|---|---|
| Java sınıfları | Tip güvenliği, IDE desteği | Her içerik için kod + derleme | Ölçeklenmez |
| JSON | Standart | Yorum yok, el ile yazması zahmetli | Okunabilirlik |
| DSL/Script (Lua, Groovy) | Esnek | Determinizm ve güvenlik riski | Fazla güç |

## Sonuçlar
- Olumlu: İçerik koddan ayrık; topluluk katkısı mümkün.
- Bedel: Koşullar (`"rho >= 0.9"`) için küçük, güvenli bir ifade ayrıştırıcı gerekir.
- Uygulama (Dilim 2): Jackson YAML → record'lar; kurallar compact constructor'larda, dosyalar arası
  kurallar `ContentLoader`'da (hepsi tek raporda). Koşullar kendi ayrıştırıcımızla (`Condition`), ifade motoru yok.
- Sınırlama: Jackson record alanlarını tamponladığı için hata satırı, alanın değil **kapsayan nesnenin bittiği** satırdır;
  alan yolu (`threat.postmortem.options[1]`) kesindir.
- Not: Enum eşlemesi `ACCEPT_CASE_INSENSITIVE_ENUMS` ile; karşılaştırma locale'den bağımsız (tr_TR testli).
