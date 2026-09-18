# AmbiScreen — HDMI capture modu (DRM korumalı içerik için)

## Neden gerekli

Netflix gibi servisler Widevine L1 DRM kullanır ve oynatma sırasında Android'e
"güvenli yüzey" (output protection) bayrağı koyar. Bu bayrak açıkken
`MediaProjection` (uygulamanın normal modda kullandığı ekran yakalama API'si)
o pikselleri okuyamaz — kare verisi olarak sadece siyah döner. Bu, Android
işletim sisteminin donanım/DRM seviyesinde uyguladığı bir kısıtlamadır;
uygulama içinden yazılımla aşılamaz.

Buradaki çözüm farklı bir katmanda çalışır: projektörün **HDMI çıkışına**
(ekrana giden, zaten çözülmüş görüntü sinyali) ayrı bir capture cihazıyla
"dışarıdan" bakılır. Bu, Ambilight/Hyperion topluluğunda uzun süredir
kullanılan standart bir yöntemdir.

## Donanım

| Parça | Not |
|---|---|
| HDMI splitter (1'e 2) | Projektörün HDMI çıkışını hem asıl ekrana hem capture cihazına dağıtır |
| USB HDMI capture kartı (UVC uyumlu) | ~15-25$ aralığındaki genel amaçlı "HDMI to USB" dongle'lar Windows/Linux/Mac'te sürücüsüz tanınır |
| Raspberry Pi 4 / Zero 2 W **veya** zaten sahip olduğunuz bir Windows/Mac/Linux bilgisayar | Betiği çalıştıracak cihaz. Ayrı bir Pi almak istemiyorsanız mevcut bilgisayarınızı kullanabilirsiniz — aşağıda Windows için ayrı bir kurulum bölümü var. |

**Önemli uyumluluk notu**: Bazı capture kartları HDCP el sıkışmasını uygular
ve korumalı bir kaynağa bağlandığında görüntü yerine siyah/gürültülü kare
verir — bu durumda bu yöntem o cihazla çalışmaz. Genel amaçlı, ucuz UVC HDMI
capture dongle'ların çoğu HDCP el sıkışması uygulamaz ve düz bir video akışı
olarak pikselleri verir; bu yüzden DIY ambiyans aydınlatma projelerinde
yaygın olarak tercih edilirler. Cihazınızın uyumluluğunu, kendi sahip
olduğunuz/izlemeye yetkili olduğunuz içerikle test ederek doğrulayın.

## Kurulum — Windows (kendi bilgisayarınız, Raspberry Pi almadan)

1. HDMI splitter'ı projektörün çıkışına takın; bir çıkışı asıl ekrana, diğerini
   capture kartına bağlayın. Capture kartını bilgisayarınızın USB portuna takın
   (bağlar bağlamaz Windows genelde sürücüsüz otomatik tanır; tanımazsa
   kutusuyla gelen sürücü CD'sini/linkini kullanın).
2. [python.org](https://www.python.org/downloads/) üzerinden Python 3.10+
   kurun — kurulum ekranında **"Add python.exe to PATH"** kutusunu
   işaretlemeyi unutmayın.
3. Bu `hdmi-capture` klasörünü bilgisayarınıza indirin (repoyu `git clone`
   ile veya ZIP olarak indirip açın), sonra PowerShell/CMD'de o klasöre girin:
   ```powershell
   cd hdmi-capture
   python -m venv .venv
   .venv\Scripts\pip install -r requirements.txt
   ```
4. Capture kartınızın hangi `capture_device` indeksinde göründüğünü bulun
   (Windows'ta `/dev/video0` gibi bir yol yok, sayısal bir indeks kullanılır):
   ```powershell
   .venv\Scripts\python list_devices.py
   ```
   Projektörden görüntü gelen indeksi not edin (dizüstü/webcam'iniz varsa o
   genelde `0`, harici capture kartı çoğunlukla `1` ya da üzeri çıkar).
5. `config.example.json` dosyasını `config.json` olarak kopyalayıp WLED
   IP'nizi, LED yerleşiminizi (Android uygulamasındaki ile aynı alanlar) ve
   4. adımda bulduğunuz `capture_device` indeksini girin.
6. Elle çalıştırıp doğrulayın:
   ```powershell
   .venv\Scripts\python ambient_sync.py --config config.json
   ```
   Projektörde görüntü değiştikçe WLED şeridinin renginin değiştiğini
   görmelisiniz. Durdurmak için `Ctrl+C`.
7. Her filme başlarken elle çalıştırmak istemiyorsanız bu klasördeki
   `run_ambient_sync.bat` dosyasına çift tıklamanız yeterli. Bilgisayar her
   açıldığında otomatik başlamasını isterseniz bu dosyanın bir kısayolunu
   Windows'un **Başlangıç (Startup)** klasörüne
   (`shell:startup` yazıp Gezgin'in adres çubuğuna yapıştırarak açılır) koyun.

## Kurulum — Raspberry Pi / Linux (7/24 çalışan ayrı bir cihaz)

1. HDMI splitter'ı projektörün çıkışına takın; bir çıkışı asıl ekrana, diğerini
   capture kartına bağlayın. Capture kartını Raspberry Pi'nin USB portuna takın.
2. Raspberry Pi'de Python 3.10+ ve pip kurulu olmalı. Bu klasörü Pi'ye kopyalayın
   (`git clone` veya `scp`), örn. `/opt/ambiscreen-hdmi`.
3. Sanal ortam oluşturup bağımlılıkları kurun:
   ```bash
   cd /opt/ambiscreen-hdmi
   python3 -m venv .venv
   .venv/bin/pip install -r requirements.txt
   ```
4. `config.example.json` dosyasını `config.json` olarak kopyalayıp WLED IP'nizi,
   LED yerleşiminizi (Android uygulamasındaki ile aynı alanlar) ve capture
   cihazınızın yolunu (`v4l2-ctl --list-devices` ile bulun, genelde `0` ya da
   `/dev/video0`) girin.
5. Elle çalıştırıp doğrulayın:
   ```bash
   .venv/bin/python3 ambient_sync.py --config config.json
   ```
   Projektörde görüntü değiştikçe WLED şeridinin renginin değiştiğini görmelisiniz.
6. Pi her açıldığında otomatik başlaması için systemd servisini kurun:
   ```bash
   sudo cp systemd/ambiscreen-hdmi.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now ambiscreen-hdmi.service
   ```
   Servis dosyasındaki `User=pi` ve yolları kendi kurulumunuza göre düzenleyin.

## Android uygulamasıyla ilişkisi

Bu betik, Android uygulamasındaki `ColorExtractor.kt` (letterbox tespiti +
kenar örnekleme) ve `DdpSender.kt` (WLED DDP protokolü) mantığının birebir
Python portudur — aynı `config.json` alan adları Android ayarlar ekranındaki
alanlarla eşleşir. Normal (DRM'siz) içerik için Android uygulamasını,
Netflix gibi korumalı akışlar için bu HDMI capture modunu kullanabilirsiniz;
ikisi aynı anda çalışmamalı (aynı WLED cihazına renk gönderirler).
