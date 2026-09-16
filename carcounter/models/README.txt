هذا المجلد اختياري.

إن كان لديك ملف نموذج جاهز (TFLite) وتريد بناء التطبيق دون تنزيله من الإنترنت:
ضع الملف هنا بالاسم:

    model.tflite

وسيقوم Gradle بنسخه تلقائيًا إلى carcounter/src/main/assets/model.tflite
عند البناء. أي نموذج كشف أجسام (COCO) بصيغة TFLite يعمل، مثل:

  - lite-model_efficientdet_lite0_detection_metadata_1.tflite   (الأدق — الافتراضي)
  - lite-model_ssd_mobilenet_v1_1_metadata_2.tflite             (الأسرع)

ملفات *.tflite في هذا المجلد غير مرفوعة إلى المستودع (انظر .gitignore).
