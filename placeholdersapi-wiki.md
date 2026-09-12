# Yunit - PlaceholderAPI (PAPI) Dokümantasyonu

Yunit, Sunucudaki diğer eklentilerle (Scoreboard, Hologram, Tab Menüsü vb.) tam uyumlu çalışabilmesi için PlaceholderAPI desteği sunar.

Sunucu, PAPI sorgularını işlerken doğrudan veritabanına bağlanmak yerine,  RAMde bulunan özel **Caffeine Cache** sistemini kullanır. Bu sayede saniyede on binlerce kez bakiye sorgusu yapılsa bile en ufak bir TPS kaybı yaşanmaz.

---

## 📌 Kullanılabilir Değişkenler (Placeholders)

Aşağıdaki değişkenleri, PlaceholderAPI destekleyen herhangi bir eklentide kullanabilirsiniz:

### 1. `%yunit_balance%` (Önerilen)
Oyuncunun mevcut bakiyesini okunaklı, noktalı ve virgüllü bir şekilde (örneğin: `1.500,50`) gösterir.
- **Örnek Çıktı:** `1.500,00`

### 2. `%yunit_balance_raw%`
Oyuncunun bakiyesini işlemlerde ve kodlamada kullanılabilecek, formatlanmamış düz sayı (örneğin: `1500.50`) şeklinde gösterir.
- **Kullanım Alanı:** Büyüktür/küçüktür (matematiksel, mantıksal) kontrolleri yaparken.
- **Örnek Çıktı:** `1500.50`

### 3. `%yunit_currency_name%`
Sunucunuzun `config.yml` dosyasında belirlediğiniz **Para Birimi Adını** gösterir.
- **Kullanım Alanı:** Bilgilendirme mesajları veya menü başlıkları.
- **Örnek Çıktı:** `Yunit` (veya config'de ne ayarladıysanız)

### 4. `%yunit_currency_symbol%`
Sunucunuzun `config.yml` dosyasında belirlediğiniz **Para Birimi Sembolünü** gösterir.
- **Kullanım Alanı:** Bakiye göstergelerinin sonuna eklemek için (Örn: `%yunit_balance% %yunit_currency_symbol%`)
- **Örnek Çıktı:** `YN`

---

## ⚙️ Gereksinimler
- **PlaceholderAPI:** Bu değişkenlerin çalışması için sunucunuzda [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) eklentisinin kurulu olması gereklidir.
- **Not:** Yunit için ekstra bir komut (Örn: `/papi ecloud download yunit`) yazmanıza **gerek yoktur**. Yunit, kendi eklentisi içinden PAPIye otomatik olarak bağlanır ve kendini kaydeder.
