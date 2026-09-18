# AmbiScreen

Android projeksiyon cihazında (veya Android TV/telefon ekran yansıtmasında) izlenen
film/dizinin renklerini gerçek zamanlı okuyup, perde arkasına monte edilmiş bir
LED şeride yansıtan ambiyans aydınlatma uygulaması — kısaca kendi "Ambilight"ın.

## Nasıl çalışır

```
[Android projeksiyon]              [WLED kontrolcü]           [LED şerit]
  ekran görüntüsü   --MediaProjection-->  küçük çözünürlükte yakala (160x90)
        |                                        |
        v                                        v
  kenar bölgelerinin ortalama rengi hesaplanır (ColorExtractor)
        |
        v
  DDP protokolü ile UDP paketi (port 4048) ------------------> WLED (Wi-Fi)
                                                                     |
                                                                     v
                                                        WS2812B LED şerit yanar
```

- **Görüntü kaynağı**: `MediaProjection` API'si ile ekranın tamamı yakalanır,
  bu sayede YouTube veya DRM'siz herhangi bir video oynatıcı fark etmeksizin
  çalışır. Netflix gibi Widevine L1 DRM kullanan servislerde bu API donanım
  seviyesinde engellenir — bu durum için `hdmi-capture/` altındaki alternatif
  çözüme bakın.
- **Renk çıkarımı**: Ekranın çevresi, ayarlanan LED yerleşimine göre (kenar
  başına açık/kapalı ve LED sayısı) dilimlere ayrılır, her dilimin ortalama
  rengi hesaplanır (`ColorExtractor.kt`, `LedLayoutConfig`).
- **İletim**: Hesaplanan renkler, WLED'in yerleşik desteklediği **DDP**
  (Distributed Display Protocol) ile UDP üzerinden gönderilir (`DdpSender.kt`).
  WLED tarafında ekstra bir eşleştirme/uygulama gerekmez; "Sync Interfaces"
  ayarında DDP açık olduğu sürece gelen paketleri otomatik algılar.

## Donanım listesi

| Parça | Not |
|---|---|
| ESP32 veya ESP8266 geliştirme kartı | WLED firmware'i çalıştıracak |
| WS2812B / SK6812 adreslenebilir LED şerit | Perde/ekran çevresine monte edilecek uzunlukta |
| 5V güç kaynağı | LED sayısına göre yeterli amperaj (örn. 60 LED ≈ 3.6A tam beyaz/parlaklıkta) |
| Perde arkasına montaj için U-kanal/bant | Işığın duvara/perdeye eşit yansıması için |

**WLED kurulumu**: [wled.app](https://kno.wled.ge/) üzerinden ESP32/ESP8266'ya
WLED firmware'ini yükleyin, Wi-Fi ağınıza bağlayın, LED sayısını ve tipini WLED
arayüzünden tanımlayın. "Sync Interfaces" sekmesinde DDP girişinin (port 4048)
açık olduğundan emin olun (varsayılan olarak açıktır).

## Uygulama ayarları

Uygulama içinde ayarlanabilenler:

- **WLED IP adresi / portu** — WLED cihazının yerel ağdaki IP'si (WLED
  arayüzünün ana sayfasında görünür), varsayılan port 4048. "Ağda WLED cihazı
  ara" butonuyla mDNS üzerinden otomatik de bulunabilir.
- **LED yerleşimi (kenar başına)** — Üst/sağ/alt/sol kenarların her biri ayrı
  ayrı açılıp kapatılabilir ve kendi LED sayısı girilebilir. Böylece tam
  perimetre, yalnızca üst-alt, L-şekilli (örn. üst+sol) veya tek kenar gibi
  farklı fiziksel LED kurulumları desteklenir (`LedLayoutConfig`).
- **Başlangıç kenarı** — Şeridin fiziksel olarak hangi kenardan başladığını
  belirtir; açık kenarlar bu noktadan saat yönünde sırayla DDP'ye gönderilir.
- **Kenar örnekleme payı (%)** — Renk ortalaması alınırken ekranın kenarından
  içeri doğru ne kadar alan kullanılacağı.
- **Parlaklık, yumuşatma, güncelleme aralığı** — Görsel his ve pil/ağ yükü
  arasındaki dengeyi ayarlar.
- **Yön ters çevirme** — Kablolama sırası, hesaplanan sıranın (başlangıç
  kenarından saat yönünde) tam tersiyse açılır.
- **Ses hassasiyeti** — Yalnızca ses-tepkili modda kullanılır; sesin
  parlaklığa dönüşüm kazancını ayarlar.

## Projeyi açma

1. Android Studio (Koala veya üzeri) ile bu klasörü açın.
2. Gradle wrapper jar'ı depoda yer almıyor; Android Studio ilk açılışta
   "Gradle wrapper bulunamadı" uyarısı verirse önerilen düzeltmeyi kabul edin
   (otomatik oluşturur), ya da terminalden `gradle wrapper --gradle-version 8.7`
   çalıştırın.
3. `minSdk 26` hedefler; test cihazınız (Android projeksiyon) buna eşit veya
   üstü olmalı.
4. Uygulamayı projeksiyon cihazına yükleyip çalıştırın, WLED IP'sini girin,
   "Ekran senkronizasyonunu başlat" ile ekran paylaşım iznini onaylayın.

## DRM korumalı içerik (Netflix vb.) için alternatifler

Widevine L1 DRM, oynatma sırasında Android'e "güvenli yüzey" bayrağı koyar ve
bu açıkken `MediaProjection` sadece siyah kare döner — bu, işletim sisteminin
donanım seviyesinde uyguladığı bir kısıtlamadır ve uygulama içinden hiçbir
yazılımla aşılamaz (Widevine L1 sertifikalı, Netflix'i resmi olarak HD/4K
oynatan projektörlerde bu blok her zaman gerçektir). İki alternatif var:

### 1. Uygulama içi ses-tepkili mod (ekstra donanım gerektirmez)

Ekranı okumak yerine cihazın çaldığı SESİ okur (`AudioPlaybackCaptureConfiguration`,
Android 10+) ve bas/tiz enerjisine göre tüm LED'lere aynı renk/parlaklığı
gönderir (`AudioReactiveService.kt`). Aynı ekran paylaşım izin ekranını
kullanır, hiçbir ek parça gerekmez. Ana ekrandaki "Ses-tepkili modu başlat"
butonuyla açılır. Gerçek ekran rengini yansıtmaz, müziğe/sese tepki veren bir
ambiyans sağlar; kaynak uygulama sesi de yakalamaya kapatmışsa (nadir ama
mümkün) bu mod da tepki vermez.

### 2. HDMI capture modu (ekstra bir Raspberry Pi/PC gerektirir)

`hdmi-capture/` klasöründeki Python betiği, projektörün HDMI çıkışına takılan
ayrı bir capture cihazından görüntüyü okuyup aynı algoritmayı (letterbox
tespiti + kenar örnekleme) ve aynı DDP protokolünü kullanarak WLED'e gönderir
— gerçek ekran rengini birebir yansıtır ama ekstra donanım (HDMI splitter +
capture kartı, ve bunu çalıştıracak bir Raspberry Pi **veya** zaten sahip
olduğunuz bir Windows/Mac/Linux bilgisayar) ister. Kurulum adımları için
[`hdmi-capture/README.md`](hdmi-capture/README.md) dosyasına bakın.

## Bilinen sınırlamalar / yol haritası

- [x] Sinemaskop/letterbox içerikte üst-alt (veya pillarbox'ta yan) siyah
      şeritlerin tespit edilip örneklemeden hariç tutulması (`ColorExtractor.detectContentRect`).
- [x] Uygulama içi WLED cihaz keşfi (mDNS, `_http._tcp` üzerinden "WLED-XXXXXX"
      adlı cihazları bulur) — IP'yi elle girmeye gerek kalmadan listeden seçilebilir.
- [x] Uygulama ikonu (adaptive icon).
- [x] Çoklu LED düzeni desteği — kenar başına açık/kapalı + kendi LED sayısı
      ve başlangıç kenarı seçilebiliyor (tam perimetre, yalnızca üst-alt,
      L-şekilli vb. desteklenir). Gerçek 2D matris (ekran arkasında satır
      satır ızgara) düzeni hâlâ kapsam dışı.
- [x] Netflix gibi DRM korumalı akışlar için iki alternatif: (a) ekstra
      donanım gerektirmeyen uygulama içi ses-tepkili mod
      (`AudioReactiveService.kt`, Android 10+), (b) gerçek ekran rengini
      birebir yansıtan ama ekstra bir Raspberry Pi/HDMI capture gerektiren
      `hdmi-capture/` betiği.
