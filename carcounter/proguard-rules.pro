# قواعد الحماية للنسخة النهائية (release).
# التصغير معطّل افتراضيًا في هذه النسخة الأولى؛ عند تفعيله أبقِ أصناف TensorFlow Lite
# لأنها تعتمد على النداء العكسي (reflection) عبر الحزمة الأصلية.
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.task.** { *; }
-dontwarn org.tensorflow.lite.**
