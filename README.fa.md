<div align="center">

<img src="assets/kltune.svg" width="128" height="128" alt="KLtune">

# KLtune

[English](README.md) | **فارسی**

### پخش‌کننده یوتیوب موزیک و آهنگ‌های گوشی برای اندروید — همراه با پروکسی داخلی Aether + Tor و هسته Xray

[![Release](https://img.shields.io/github/v/release/Kolandone/InterTune?label=release&color=E51928&labelColor=131318)](https://github.com/Kolandone/InterTune/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Kolandone/InterTune/total?color=E51928&labelColor=131318)](https://github.com/Kolandone/InterTune/releases)
[![Telegram](https://img.shields.io/badge/Telegram-@kolandjs1-26A5E4?logo=telegram&logoColor=white&labelColor=131318)](https://t.me/kolandjs1)
[![License](https://img.shields.io/badge/license-GPL--3.0-E51928?labelColor=131318)](LICENSE)

[دانلود](#دانلود) · [امکانات](#امکانات) · [کانال و حمایت](#کانال-تلگرام-و-حمایت) · [منابع و لایسنس](#منابع-و-لایسنس)

</div>

---

<div dir="rtl">

**KLtune** یک اپلیکیشن رایگان و متن‌باز اندرویدی برای پخش یوتیوب موزیک و فایل‌های صوتی داخل گوشی است که به‌عنوان فورکی از پروژه **[InterTune](https://github.com/ItzSkyeYT/InterTune)** (مبتنی بر [OuterTune](https://github.com/OuterTune/OuterTune) و [InnerTune](https://github.com/z-huang/InnerTune)) ساخته شده است.

تمام امکانات اصلی InterTune — از جمله پخش بدون تبلیغ، دانلود آفلاین، متن هماهنگ آهنگ (Lyrics)، اکولایزر ۱۰ بانده، صدای فضایی، تشخیص آهنگ، Android Auto و کست — در KLtune دست‌نخورده باقی مانده و در کنار آن، موتورهای داخلی دور زدن محدودیت (**Aether + Tor** و **هسته Xray**) مستقیم به برنامه اضافه شده‌اند تا بدون نیاز به روشن کردن فیلترشکن روی کل گوشی، راحت موزیک گوش بدهید.

---

## امکانات

### پخش و کیفیت صدا
- پخش یکپارچه یوتیوب موزیک و آهنگ‌های داخل حافظه گوشی در یک صف پخش مشترک
- دانلود و ذخیره آفلاین آهنگ‌ها همراه با نمایش متن هماهنگ (Lyrics)
- اکولایزر ۱۰ بانده، صدای فضایی (Spatial Audio)، یکسان‌سازی بلندی صدا و افکت محو شدن بین آهنگ‌ها
- پیشنهادهای هوشمند (Quick Picks)، تشخیص آهنگ در حال پخش، پشتیبانی از Chromecast / DLNA و Android Auto

### موتور داخلی Aether + Tor (بدون نیاز به فیلترشکن خارجی)
- تمام درخواست‌های یوتیوب موزیک، پخش آهنگ، کاورها و حتی صفحه لاگین گوگل (`WebView`) را مستقیم از داخل تونل عبور می‌دهد
- **تنظیم پیش‌فرض آماده برای پخش کامل:** حالت `MASQUE H2 ← Tor` با اسکنر `Ironclad` و پیش‌تنظیم یک‌لمسی برای گرفتن آی‌پی خروجی بدون محدودیت
- پشتیبانی از پروتکل‌های متنوع: `MASQUE` (روی HTTP/2 و HTTP/3)، `WireGuard`، `Gool`، `Gool Classic`، `MiM` و `Tor`
- امکانات کامل ضد فیلترینگ: پروفایل‌های `Noize`، تکه‌تکه کردن TLS (`Fragment`)، بازکننده QUIC v2، SNI سفارشی، `ECH`، فیلتر کشور خروجی، لاگ زنده و تست سرعت و آی‌پی اتصال

### هسته داخلی Xray (برای کانفیگ شخصی و سابسکریپشن)
- اگر کانفیگ شخصی خودتان را دارید، می‌توانید مستقیم داخل برنامه به آن وصل شوید
- پشتیبانی کامل از لینک‌های `vless://` (شامل TLS و REALITY)، `vmess://`، `trojan://`، `ss://` و فایل‌های کامل Xray JSON
- **مدیریت سابسکریپشن:** اضافه کردن، آپدیت کردن و حذف لینک‌های سابسکریپشن (فرمت Base64 و لیست ساده لینک‌ها)
- **پینگ واقعی (Real Delay) و انتخاب خودکار:** تست واقعی پینگ HTTP همه سرورها به‌صورت هم‌زمان، مرتب‌سازی بر اساس کمترین پینگ و اتصال خودکار به سریع‌ترین سرور فعال

### زبان فارسی، نوتیفیکیشن کانال و آپدیت خودکار
- **پشتیبانی از فارسی و انگلیسی:** انتخاب مستقیم زبان `فارسی` یا `English` از داخل تنظیمات برنامه با چیدمان کامل راست‌چین (RTL) و نمایش درست عبارت‌های انگلیسی و آدرس‌ها
- **نوتیفیکیشن پیام‌های جدید کانال:** بررسی پیام‌های جدید کانال تلگرام `@kolandjs1` هر ۱۰ دقیقه (با تایمر ماندگار و تاریخچه پیام‌ها برای جلوگیری از ارسال تکراری)
- **بررسی خودکار آپدیت:** دریافت مستقیم نسخه‌های جدید از گیتهاب برنامه متناسب با معماری پردازنده گوشی شما

---

## دانلود

آخرین نسخه APK را می‌توانید مستقیم از **[صفحه ریلیزهای گیتهاب](https://github.com/Kolandone/InterTune/releases/latest)** دانلود کنید:

| معماری گوشی | پسوند فایل | مناسب برای |
| :--- | :--- | :--- |
| **`arm64-v8a`** | `*-arm64-v8a-release-*.apk` | تقریباً تمام گوشی‌های جدید اندرویدی (۶۴ بیتی) |
| **`armeabi-v7a`** | `*-armeabi-v7a-release-*.apk` | گوشی‌های قدیمی‌تر ۳۲ بیتی |
| **`x86_64`** | `*-x86_64-release-*.apk` | شبیه‌سازها و تبلت‌های x86 |

> روی اندروید ۷.۰ و بالاتر نصب می‌شود. تمام نسخه‌ها با امضای ثابت منتشر می‌شوند تا آپدیت‌های بعدی مستقیم روی نسخه قبلی نصب شوند و نیازی به حذف برنامه نباشد.

---

## کانال تلگرام و حمایت

- **کانال تلگرام:** [@kolandjs1](https://t.me/kolandjs1)

اگر از KLtune خوشتان آمده و دوست دارید از توسعه و نگهداری آن حمایت کنید، می‌توانید از دکمه قلب بالای صفحه داخل برنامه یا آدرس‌های زیر استفاده کنید (از طریق جدول زیر به راحتی آدرس را کپی کنید):

</div>

| شبکه | آدرس کیف پول |
| :--- | :--- |
| **USDT (TRC20)** | `TJj1J8keFZBUrwL6vncrLXZzE1QveFohhM` |
| **USDT (BEP20)** | `0x16789E769B729032A9FB361b725246DE1FB4B584` |
| **TON (GRAM)** | `UQCczkKrpZaQ5url-2XBmX4Xa_6NPrdhKRYuwlZHcSPfoZVm` |

<div dir="rtl">

---

## منابع و لایسنس

- **فورک‌شده از پروژه اصلی:** [ItzSkyeYT/InterTune](https://github.com/ItzSkyeYT/InterTune)
- **پروژه‌های پایه قبلی:** [OuterTune](https://github.com/OuterTune/OuterTune) و [InnerTune](https://github.com/z-huang/InnerTune)
- **موتور داخلی Aether:** [CluvexStudio/Aether](https://github.com/CluvexStudio/Aether)
- منتشرشده تحت لایسنس متن‌باز **GNU General Public License v3.0 (GPL-3.0)** — جزئیات کامل در فایل [LICENSE](LICENSE).

</div>
