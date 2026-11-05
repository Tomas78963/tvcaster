# TV Video Gönder 📺 (v1.2)

Telefonunuzdaki videoları, **aynı Wi-Fi ağındaki Chromecast veya Android TV** cihazınıza gönderen tarayıcı + yayın uygulaması. **TV tarafına hiçbir uygulama kurmanız gerekmez** (Google Cast varsayılan alıcısı kullanılır).

## Özellikler

- 🌐 **Yerleşik tarayıcı** (WebView): adres çubuğu, geri/ileri/yenile, pop-up engelleme
- 🔍 **Video yakalama**: Sayfadaki `.mp4 / .m3u8 / .mpd / .webm` bağlantıları otomatik bulunur, ekranda **"TV'de Oynat"** düğmesi çıkar, birden çok video listelenir
- 🔗 **Doğrudan bağlantı**: Panodaki video bağlantısını yapıştır → tarayıcıyı atlayıp doğrudan TV'ye gönder
- 📺 **Google Cast**: Yayın simgesinden TV seçimi, oynat/duraklat, ±10 sn, ilerleme çubuğu, TV sesi, durdurma
- 🛡️ **v1.2 yenilikleri**:
  - Video TV'ye yüklenmezse **açık hata mesajı** (hata koduyla) gösterilir — sessiz başarısızlık yok
  - Gönderim öncesi **erişim ön-testi**: TV'nin videoyu indiremeyeceği durumlar (Referer/çerez korumalı, süreli bağlantı) önceden tespit edilip uyarılır
  - TV'de oynatma hatası anında bildirilir (biçim/CORS/süresi dolmuş bağlantı)
  - Yayın başlayınca telefondaki sayfa videosu otomatik duraklatılır
- 🔔 **Bildirim/lockscreen denetimi**: Yayın sırasında ön plan hizmeti ile bildirimden kontrol
- 📂 **Yan menü (Türkçe)**: Tarayıcı · Son kullanılan medyalar · Oynatma listesi · Sık kullanılanlar · Tarayıcı geçmişi · En çok ziyaret edilenler · Ayarlar · Hakkında · Koyu tema anahtarı (Room veritabanı ile cihazda saklanır)
- 🛡️ **Reklam/pop-up engelleme**: `blocklist.txt` alan adları + sayfaya CSS enjeksiyonu (Ayarlar'dan kapatılabilir)
- 🎨 **Material 3**, lacivert-turuncu, koyu tema varsayılan

## Bilinen Sınırlamalar

- DRM korumalı içerik (Netflix, Disney+, Amazon Prime vb.) gönderilemez
- Bazı siteler videoları yalnızca kendi sunucularından oynatır (Referer/çerez kısıtı) → uygulama artık bunu önceden uyararak bildirir
- Süreli (token) bağlantılarda sayfayı yenileyip videoyu hemen seçin
- Telefon ile TV aynı Wi-Fi'da olmalıdır (AP izolasyonu kapalı olmalı)
- Reklam engelleme %100 garanti değildir; `blocklist.txt` genişletilebilir

## Dosya Yapısı

```
TVVideoGonder/
├── .github/workflows/build.yml      ← GitHub Actions: APK'yı bulutta derler
├── settings.gradle
├── build.gradle
├── gradle.properties
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/blocklist.txt     ← Reklam alan adları (düzenlenebilir)
        ├── java/com/veysel/tvcaster/
        │   ├── MainActivity.kt      ← Tarayıcı + video yakalama + ön-test + yayın kontrolü
        │   ├── CastHelper.kt        ← Cast işlemleri + yükleme geri bildirimi + erişim ön-testi
        │   ├── CastOptionsProvider.kt
        │   ├── CastForegroundService.kt  ← Bildirim denetimi
        │   ├── AdBlocker.kt
        │   ├── VideoAdapter.kt
        │   ├── LibraryActivity.kt / SettingsActivity.kt / AboutActivity.kt
        │   └── data/Db.kt           ← Room: 5 tablo + DAO'lar
        └── res/                     ← Türkçe metinler, lacivert-turuncu tema, ikonlar
```

## 🚀 APK'ya Çevirme — Bilgisayar Gerektirmez (GitHub Actions)

1. **github.com** adresinde ücretsiz hesap açın / giriş yapın.
2. Sağ üstteki **+** → **New repository** deyin. Ad: `tvcaster`, Görünürlük: **Public** → **Create repository**.
3. Açılan sayfada **"uploading an existing file"** bağlantısına tıklayın.
4. ZIP'i ayıkladığınız klasörü açın, **klasörün İÇİNDEKİ TÜM DOSYA VE KLASÖRLERİ seçin (Ctrl+A)** ve GitHub yükleme alanına **sürükleyip bırakın**.
   - ⚠️ Klasörün kendisini değil, içindekileri sürükleyin. `.github` klasörü gizli olabilir: Windows'ta **Görünüm → Gizli öğeler**, macOS'ta **Cmd+Shift+.** ile gösterin.
   - Daha önce yükleme yaptıysanız: aynı depoya yeni dosyaları sürükleyip **eski dosyaların üzerine yazdırın** (aynı adlı dosyalar otomatik güncellenir).
5. Yüklenince **"Commit changes"** düğmesine basın.
6. **Actions** sekmesi → derleme otomatik başlar (ilk seferde "I understand my workflows..." onayı gerekebilir). **5-10 dakika** sürer.
7. Tamamlanınca işe girin, alttaki **Artifacts** → **TV-Video-Gonder-APK** indirin → içindeki **app-debug.apk** telefonu kurun.
8. Eski sürüm kuruluysa üzerine yükleyecektir (veriler korunur).

### Kullanım
1. Telefon ve TV'yi **aynı Wi-Fi** ağına bağlayın.
2. Uygulamada video sitesine girin veya video bağlantısını yapıştırın.
3. **"TV'de Oynat"** düğmesi çıkınca dokunun → videoyu seçin → listeden TV'nizi seçin.
4. Video TV'de başlamazsa uygulama artık **sebebini açıkça söyler**:
   - "Erişim uyarısı" → site videomu koruyor; sayfayı yenileyip hemen deneyin
   - "TV videoyu yüklemedi (hata kodu X)" → bağlantı süresi dolmuş; sayfayı yenileyin
   - "TV videoyu çalamadı" → biçim/CORS sorunu; farklı kaynak deneyin

## Yerelde Derlemek İsterseniz (İsteğe Bağlı)

Android Studio (Koala veya üstü) → **Open** → proje klasörünü seçin → **Build APK**. AGP 8.5.2 + Gradle 8.7 kullanılır.

## Özelleştirme

- **Uygulama adı**: `app/src/main/res/values/strings.xml` → `app_name`
- **Reklam listesi**: `app/src/main/assets/blocklist.txt` (satır başına alan adı)
- **Paket adı**: `app/build.gradle` → `applicationId` (şu an `com.veysel.tvcaster`)
