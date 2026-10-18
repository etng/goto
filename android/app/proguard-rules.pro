# Goto release 混淆规则（R8 已开启：isMinifyEnabled=true）。
#
# 当前无需额外 -keep 的原因：
# - SettingsCatalog 反射 android.provider.Settings 及其嵌套类（Global/Secure/System），
#   这些是 Android 框架类，运行在设备系统里、不在本 App 的 APK 中，R8 不会处理，
#   因此反射扫描系统设置入口天然安全，不会被混淆/裁剪影响。
# - 数据类（Entry / Config 等）均以手写 JSON（org.json）序列化，非反射序列化，无需 keep。
#
# 若后续引入 Gson / kotlinx-serialization 之类的反射序列化，需在此补对应 -keep 规则。

# 保留 BuildConfig（已注入 BUILD_DATE 等，供页脚读取；被内联引用，通常无需，但保险）
-keep class cn.y10n.gotoapp.BuildConfig { *; }
