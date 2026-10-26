# Heritage — Android ilova (APK)

Bu ilova **https://heritageedu.uz** saytini ochadi. Saytdagi `index.html` ni o'zgartirib,
Cloudflare'ga yuklasangiz, ilova keyingi ochilishda **o'zgarishni avtomatik ko'rsatadi** —
APK'ni qayta yasash shart emas.

## APK yasash (eng oson yo'l — GitHub orqali, kompyuterga hech narsa o'rnatmasdan)
1. github.com da yangi repo oching (masalan `heritage-apk`).
2. Shu papkadagi **barcha fayllarni** (`.github` papkasi bilan) repoga yuklang.
3. Repo'da **Actions** bo'limiga kiring -> "Build APK" -> **Run workflow**.
4. 3-5 daqiqadan keyin yashil belgi chiqadi. Ichiga kirib, pastdagi **Heritage-APK** ni yuklab oling (zip ichida `app-debug.apk`).
5. APK'ni telefonga o'tkazib o'rnating ("noma'lum manbalardan o'rnatish"ga ruxsat bering).

## Variant 2 — Android Studio
Papkani Android Studio'da oching -> **Build > Build APK(s)**.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Sozlamalar
- Sayt manzili: `app/src/main/java/uz/heritageedu/davomat/MainActivity.java` ichida `SITE` va `HOST`.
- Ilova nomi: `app/src/main/res/values/strings.xml`.
- Ikonka: Heritage logotipi (`ic_launcher_foreground.png`, har xil o'lchamda). Almashtirish kerak bo'lsa, `app/src/main/res/mipmap-*` ichidagi fayllarni yangilang. Play Market uchun 512x512 ikonka: `ic_launcher_512.png`.
  `app` -> New -> Image Asset ishlating.

## Nimalar ishlaydi
- Login, Firebase, hamma sahifalar (sayt qanday bo'lsa, shunday)
- Rasm yuklash va Excel import (fayl tanlash)
- Excel/PDF yuklab olish -> telefondagi **Downloads** papkasiga tushadi
- Telegram/Instagram/telefon havolalari tashqi ilovada ochiladi
- Internet yo'q bo'lsa — "Qayta urinish" tugmali sahifa

## Eslatma
- Bu "debug" APK: o'rnatish va ishlatish uchun yetarli. Google Play'ga qo'yish uchun alohida imzolangan (release/AAB) versiya kerak bo'ladi.
- Yangilanish ko'rinmasa: Cloudflare'da `index.html` uchun kesh sarlavhasini tekshiring (`Cache-Control: max-age=0, must-revalidate` bo'lishi kerak).
