# Yunit - Web & Redis Entegrasyonu (Geliştirici Dokümantasyonu)

Yunit, veritabanı (MySQL) kullanan diğer eklentilerin aksine, **Web Marketinizden (PHP, Node.js vb.) bakiye yüklendiğinde oyuncunun oyundan çıkıp girmesine gerek kalmadan** anında güncellenmesini sağlayan bir Redis Pub/Sub altyapısına sahiptir.

Bu dokümantasyon, Yunit'i kendi özel web yazılımınıza nasıl bağlayacağınızı gösterir.

---

## Çalışma Mantığı

1. Oyuncu web sitenizden Yunit satın alır.
2. Web siteniz (Backend) MySQL veritabanındaki `yunit_accounts` tablosunda oyuncunun `balance` değerini günceller.
3. Web siteniz aynı anda Redis sunucusuna bağlanarak `yunit:sync` kanalına oyuncunun **UUID**'sini mesak olarak gönderir.
4. O an aktif olan Minecraft sunucuları bu mesajı saniyesinde yakalar, oyuncunun önbelleğini temizler ve güncel bakiyeyi ekrana yansıtır.

---

##  Entegrasyon Örnekleri

Aşağıda web geliştiricileri için farklı dillerde örnek kodlar bulunmaktadır. İşlem sırası her zaman aynıdır: **Önce MySQL güncelle, sonra Redise Publish at.**

### 1. PHP Örneği (Predis / phpredis)

```php
<?php
$uuid = "OYUNCU_UUID";
$amountToAdd = 50.00;

// 1. MySQL İşlemi (Bakiyeyi Ekle)
$stmt = $pdo->prepare("UPDATE yunit_accounts SET balance = balance + :amount WHERE uuid = :uuid");
$stmt->execute(['amount' => $amountToAdd, 'uuid' => $uuid]);

// 2. Redis İşlemi (Sunuculara Haber Ver)
$redis = new Redis();
$redis->connect('127.0.0.1', 6379);
// $redis->auth('sifreniz_varsa_buraya');

// Sadece UUID stringini "yunit:sync" kanalına gönderin
$redis->publish('yunit:sync', $uuid);

echo "Bakiye eklendi ve sunuculara iletildi!";
?>
```

### 2. Node.js Örneği (ioredis / redis)

```javascript
const Redis = require("ioredis");
const redis = new Redis({
  host: "127.0.0.1",
  port: 6379,
  // password: "sifreniz_varsa_buraya"
});

async function addYunitBalance(uuid, amountToAdd) {
    // 1. MySQL İşlemi (Bakiyeyi Ekle)
    await db.query(
        "UPDATE yunit_accounts SET balance = balance + ? WHERE uuid = ?", 
        [amountToAdd, uuid]
    );

    // 2. Redis İşlemi (Sunuculara Haber Ver)
    // Sadece UUID stringini "yunit:sync" kanalına gönderin
    await redis.publish("yunit:sync", uuid);
    
    console.log("Bakiye eklendi ve sunuculara iletildi!");
}

addYunitBalance("OYUNCU_UUID", 50.00);
```

---

## Önemli Notlar
- Yunit eklentisinin `config.yml` dosyasında `redis.enabled: true` olduğundan emin olun.
- Gönderdiğiniz (Publish ettiğiniz) veri bir JSON vs. olmamalıdır, sadece ve sadece oyuncunun **saf UUID stringi** olmalıdır (Örn: `566141f1-ff77-4fbb-af1e-c8cf996fad29`).
- Kanal adı varsayılan olarak `yunit:sync`'tir. Kodlarınızda bu kanala yayın yapmanız zorunludur
