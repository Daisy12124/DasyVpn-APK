# قواعد مربوط به libv2ray داخل خود AAR هست (proguard.txt مصرف‌کننده)
# ولی برای اطمینان نگه می‌داریم:
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-dontwarn go.**
-dontwarn libv2ray.**

# مدل‌های سریالایزیونی
-keepclassmembers class com.dasyvpn.app.model.** { *; }
-keep class com.dasyvpn.app.model.** { *; }
