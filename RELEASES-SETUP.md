# تشغيل التحديث التلقائي بعد جعل المستودع خاصًّا

المستودع صار خاصًّا، فتوقّف زرّ «فحص تحديث التطبيق» لأن ملفات الإصدار
لم تعد تُنزَّل بلا تسجيل دخول. الحل: **مستودع عام صغير للنسخ فقط**،
تبقى معه الشيفرة ومفتاح التوقيع سرًّا.

## خطوتان لمرة واحدة

### 1) أنشئ مستودع النسخ

افتح <https://github.com/new> واملأ:

- **Repository name**: `alameer-releases`
- **Public** ✅ (مهم: النسخ وحدها تكون عامة، لا الشيفرة)
- علّم **Add a README file**
- ثم **Create repository**

### 2) اربطه بمستودع الشيفرة

في مستودع `alameer-station` الخاص:

**أ. المتغيّر** — Settings ← Secrets and variables ← Actions ← تبويب **Variables**
← New repository variable:

| الحقل | القيمة |
|------|--------|
| Name | `RELEASES_REPO` |
| Value | `gnaf157-eng/alameer-releases` |

**ب. الرمز** — أنشئ رمز وصول من <https://github.com/settings/personal-access-tokens/new>:

- **Token name**: `alameer-releases`
- **Expiration**: بلا انتهاء أو سنة
- **Repository access**: Only select repositories ← اختر `alameer-releases`
- **Permissions** ← Repository permissions ← **Contents: Read and write**
- انسخ الرمز الظاهر (يظهر مرة واحدة)

ثم في `alameer-station`: Settings ← Secrets and variables ← Actions ←
تبويب **Secrets** ← New repository secret:

| الحقل | القيمة |
|------|--------|
| Name | `RELEASES_TOKEN` |
| Secret | الرمز المنسوخ |

## بعد الضبط

كل نسخة جديدة تُنشر في المستودعين: العام ليعمل التحديث التلقائي،
والخاص للأرشفة. ورابط التنزيل الدائم يصير:

```
https://github.com/gnaf157-eng/alameer-releases/releases/latest
```

**مهم**: النسخة التي تحمل عنوان التحديث الجديد هي أول نسخة تُبنى بعد ضبط
المتغيّر. لذلك ثبّتها يدويًا مرة واحدة على الجهازين من الرابط أعلاه،
وبعدها يعمل زرّ التحديث وحده.

ما دام `RELEASES_REPO` غير مضبوط، يعمل البناء كما هو بلا تغيير.
