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
  bu sayede Netflix, YouTube veya herhangi bir video oynatıcı fark etmeksizin
  çalışır (DRM korumalı bazı içerikler ekran yakalamayı engelleyebilir).
- **Renk çıkarımı**: Ekran, LED sayısına göre çevresi (üst/sağ/alt/sol kenar)
  boyunca dilimlere ayrılır, her dilimin ortalama rengi hesaplanır
  (`ColorExtractor.kt`).
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
  arayüzünün ana sayfasında görünür), varsayılan port 4048.
- **LED sayısı** — Şeritteki toplam LED adedi, perimetre etrafına orantılı
  dağıtılır.
- **Kenar örnekleme payı (%)** — Renk ortalaması alınırken ekranın kenarından
  içeri doğru ne kadar alan kullanılacağı.
- **Parlaklık, yumuşatma, güncelleme aralığı** — Görsel his ve pil/ağ yükü
  arasındaki dengeyi ayarlar.
- **Yön ters çevirme** — Fiziksel LED kablolama yönü, hesaplanan sırayla
  (saat yönü: üst→sağ→alt→sol) ters ise açılır.

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

## Bilinen sınırlamalar / yol haritası

- [ ] Çoklu LED düzeni desteği (yalnızca tek perimetre şeridi; L-şekilli veya
      matris düzenler için `ColorExtractor` genişletilmeli).
- [ ] Netflix gibi DRM korumalı akışlarda ekran yakalama engellenebilir —
      bu durumda alternatif olarak harici bir HDMI capture + PC/Raspberry Pi
      tabanlı Hyperion.NG çözümü değerlendirilebilir.
- [ ] Kararan sahnelerde siyah bar (letterbox) tespiti ekleyerek gereksiz siyah
      kenar örneklemesinin önüne geçmek.
- [ ] Uygulama içi WLED cihaz keşfi (mDNS/UDP broadcast) — şu an IP manuel giriliyor.
