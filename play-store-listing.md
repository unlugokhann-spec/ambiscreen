# Play Store Listesi — Kopyala/Yapıştır Taslakları

Bunlar Play Console'da ilgili alanlara doğrudan yapıştırabileceğin metinler.
Kendi tercihine göre değiştirebilirsin.

## Uygulama Adı
AmbiScreen

## Kısa Açıklama (max 80 karakter)
Ekranınızı/sesinizi analiz edip WLED LED şeridine ambiyans ışığı gönderir.

## Tam Açıklama (max 4000 karakter)

AmbiScreen, film ve dizi izlerken perde/ekran arkasına ambiyans aydınlatma
ekleyen kendi "Ambilight" çözümünüz. Android projeksiyon cihazınızın veya TV
kutunuzun ekranındaki renkleri gerçek zamanlı okuyup, evinizdeki bir WLED LED
şeridine yansıtır.

ÖZELLİKLER
• Ekran senkronizasyonu: Ekranın kenar bölgelerindeki ortalama rengi hesaplayıp
  WLED'e gerçek zamanlı gönderir.
• Esnek LED yerleşimi: Üst/sağ/alt/sol kenarların her birini ayrı açıp
  kapatabilir, kendi LED sayısını girebilirsiniz — tam perimetre, yalnızca
  üst-alt veya L-şekilli kurulumlar desteklenir.
• Sinemaskop/letterbox tespiti: Üstte/altta kalan siyah şeritler otomatik
  algılanıp örneklemeden hariç tutulur.
• Otomatik WLED keşfi: Ağınızdaki WLED cihazlarını tarayıp listeler, IP
  adresini elle girmenize gerek kalmaz.
• Ses-tepkili yedek mod: Bazı akış servisleri kopya koruması nedeniyle ekran
  paylaşımını engelleyebilir. Bu durumda uygulama, cihazın çaldığı sese göre
  (bas/tiz enerjisi) LED renk ve parlaklığını ayarlayan bir yedek moda geçer.

GEREKSİNİMLER
• Bir WLED destekli LED denetleyici (ESP32/ESP8266 + adreslenebilir LED şerit).
• LED denetleyici ve Android cihazınızın aynı Wi-Fi ağında olması.

AmbiScreen hiçbir veri toplamaz, hesap gerektirmez ve tüm işlemler cihazınızda
ile yerel ağınızda gerçekleşir. Detaylar için gizlilik politikamıza bakınız.

## Kategori
Araçlar (Tools) veya Ev Otomasyonu / Yaşam Tarzı (Lifestyle)

## Gizlilik Politikası URL'si
https://unlugokhann-spec.github.io/ambiscreen/privacy-policy/
(GitHub Pages'i etkinleştirdikten sonra bu URL çalışır olacak — aşağıya bakın)

## Destek E-postası
Repodaki privacy-policy/index.html ve support/index.html dosyalarında
placeholder olarak "destek@ambiscreen.app" kullandım — bu alan adı muhtemelen
sana ait değil. Kendi gerçek e-posta adresinle (gmail vb. olabilir)
değiştirmen gerekiyor, yoksa kullanıcılar sana ulaşamaz.

---

## Hassas İzin Gerekçe Formu Taslakları

Google Play Console, ekran kaydı (MediaProjection) ve ses kaydı (RECORD_AUDIO)
izinleri için "neden kullanıyorsunuz" diye ayrı bir form isteyecek. Aşağıdaki
metinleri kullanabilirsin:

**Ekran kaydı / MediaProjection API kullanım gerekçesi:**
> Uygulama, kullanıcının ekranındaki ortalama renkleri gerçek zamanlı olarak
> hesaplayıp yerel ağdaki bir WLED LED denetleyicisine göndermek için
> MediaProjection API'sini kullanır (ambiyans/bias aydınlatma amaçlı). Yakalanan
> kareler cihaz dışına hiçbir zaman gönderilmez, kaydedilmez veya saklanmaz;
> yalnızca anlık olarak hesaplanan renk değerleri yerel ağdaki kullanıcının
> kendi LED cihazına iletilir.

**Ses kaydı / RECORD_AUDIO (AudioPlaybackCaptureConfiguration) kullanım gerekçesi:**
> Bazı video uygulamaları (DRM korumalı akışlar) işletim sistemi düzeyinde ekran
> yakalamayı engeller. Bu durumda uygulama, yalnızca kullanıcının açık izniyle,
> cihazın çaldığı sesin bas/tiz enerjisini analiz ederek bir ambiyans ışık efekti
> üretir. Ses verisi hiçbir zaman kaydedilmez, dosyaya yazılmaz veya cihaz
> dışına gönderilmez; yalnızca hesaplanan renk/parlaklık değeri yerel ağdaki
> kullanıcının kendi LED cihazına iletilir.

## GitHub Pages'i Etkinleştirme (gizlilik politikası URL'si için)

1. https://github.com/unlugokhann-spec/ambiscreen/settings/pages adresine git.
2. "Build and deployment" → "Source" kısmında **"Deploy from a branch"** seç.
3. Branch: **main**, klasör: **/(root)** seç, Save'e bas.
4. 1-2 dakika sonra şu adresler yayında olur:
   - `https://unlugokhann-spec.github.io/ambiscreen/privacy-policy/`
   - `https://unlugokhann-spec.github.io/ambiscreen/support/`
