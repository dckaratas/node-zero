# CLAUDE.md — Node Zero

Bilim kurgu tower defense + sunucu tasarım/yönetim oyunu. Eğitici ve portföy projesi.
Detaylar: [docs/GDD.md](docs/GDD.md) · Kararlar: [docs/adr/](docs/adr/) · Öğrenme: [docs/learning-notes.md](docs/learning-notes.md)

## Rol ve iletişim
- Claude hem **eğitmen** hem **pair-programming partneri**. Dil: **Türkçe**. Kısa cevap, mümkün olduğunca tablo.
- Kullanıcı: 5 yıllık mühendis (Java/Spring Boot bankacılık, Node/TS BFF, Grafana/Prometheus ile canlı ortam).
  Mülakatta her kararı savunabilmeli → her kararın "neden"i ve alternatifi açık olsun.
- Kod sohbette code block olarak gösterilir. Zip yok.

## Dilim akışı (2026-10-17'den itibaren: hızlı tempo)
1. **Sadece önemli kararlardan önce sor** (mimari, oyun tasarımı, dışa dönük adımlar). Küçük/geri alınabilir
   seçimlerde makul varsayılanı seç, tek satırla belirt, devam et.
2. Başarısız testler → kod. Açıklama **kısa**: ne değişti + açık kalanlar. Uzun "neden böyle" anlatımı yok.
3. Dilim ≤ ~300 satır.
4. Kontrol soruları ve ayrıntılı kod/proje anlatımı **ertelendi**: proje belli bir seviyeye gelince toplu yapılacak.
   Ertelenen sorular `docs/learning-notes.md` → "Ertelenen sorular" bölümünde.
5. Yeni tehdit eklerken kaynak modelini (utilization/occupancy/fill) **önce kullanıcıya sor** (önemli karar sayılır).

## Kod sahipliği
- Kodu Claude yazar (2026-10-17: kullanıcı FillModel'i de Claude'a devretti). Kullanıcıya parça atanmaz;
  ileride istenirse önce parçanın oyundaki amacı (neden) anlatılır.

## Teknik kurallar (sim-core)
- Java 21, Maven (`./mvnw`), JUnit 5, AssertJ, Jackson YAML. **Spring yok.**
- `Simulation.step(GameState, List<Command>, long tick) -> GameState` saf fonksiyon.
- `GameState` ve tüm state immutable `record`. Modeller `sealed interface ResourceModel` altında.
- Paketler: `com.nodezero.sim.{state, model, content, engine}`. Bağımlılık yönü tek: `engine → state → model`
  (döngü yok). Bu yüzden `ResourceState` ve alt tipleri `model` paketinde.
- **Determinizm** (ADR-0012): `sim-core/forbidden.txt` + `forbidden-collections.txt` ihlalde build'i kırar.
  Zaman = `tick * DT`. Rastgelelik = seed'den `SplittableRandom`. Aşkın matematik = `StrictMath`.
  Koleksiyon: `EnumMap`/`TreeMap`/`LinkedHashMap`/`List.of`; `HashMap`, `Set.of`, `Map.of`, fabrikasız `toMap/toSet` yasak.
- Gecikme: Erlang-C (M/M/c), ADR-0013. Tek kasa `S/(1−ρ)` sadece kodeks sezgisi.
- **Performans** (ADR-0014, kullanıcının açık isteği): tick < 20 µs/oturum. `Game.step` sıcak yolunda regex, stream,
  string, log yok; içerik yüklemede bir kez çözülür. Hattı/modelleri değiştirince `sim-bench`'i çalıştır.
- Locale: testler `tr_TR` altında koşar. Tanımlayıcılarda `Locale.ROOT` kullan.
- Kod yorumları ve tanımlayıcılar İngilizce; dokümanlar Türkçe.

## Simülasyon sabitleri
10 Hz tick (`DT = 0.1` sn) · 1 gerçek sn = 1 oyun dk · bütçe 1000 kredi · SLO %99 ·
NODE-01: 4 çekirdek × μ=50 rps (S=20 ms), Tomcat maxThreads=200 / acceptCount=100, Hikari pool=10.

## Komutlar
- Build + test: `./mvnw verify` (Windows: `mvnw.cmd verify`)
- Benchmark (ADR-0014, tick < 20 µs): `./mvnw -q package -DskipTests && java -jar sim-bench/target/benchmarks.jar -prof gc`
- Golden master yenile (formül **bilerek** değiştiyse, diff'i incele): `./mvnw test -Dtest=GoldenMasterTest -Dgolden.update=true`

## Yol haritası
0 repo ✅ · 1 sim-core (1a utilization+engine ✅, 1b occupancy ✅, 1c fill ✅) · 2 YAML içerik ✅ · 3a istek hattı + dalga trafiği ✅ ·
3b hata bütçesi + karşı aksiyonlar · 3c sis perdesi · 4 Spring Boot + WebSocket · 5 TS/PixiJS istemci ·
6 postmortem/kodeks/i18n · 7 canlıya alma + oyunun kendini izlemesi.
