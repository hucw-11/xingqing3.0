# 同学日程

原生 Android 学生课表与日程 App，适用于 Android 8.0 及以上。浅灰背景、蓝色日期条、时间线卡片，底部仅保留课表与日程。

## 使用

1. 在课表右上角设置开学日期、学期周数和 12 节课的时间。开学日期所在的周一到周日为第 1 周。
2. 点击课表空格录入课程，支持连续节次、指定周次、单周和双周。重叠课程会提示冲突。
3. 日程页点击日期切换，箭头切换周，点击月份可以选任意日期。「今天」返回当天。
4. 点击 ＋ 添加全天或定时日程；点击卡片编辑、完成或删除。课程卡片会打开对应课程的编辑表单，修改会影响整门课的所有周次。
5. 课程按当天星期和学期周次自动生成当天安排，按起始时间归入上午（12:00 前）或下午。修改节次时间会同步影响课表及日程。

内容离线保存在应用内部存储，正常关闭和重新打开会保留。卸载 App 或清除应用数据会删除本机记录。第一版不提供账号、跨设备同步、节假日调课和提醒。

## GitHub 云端编译

本地无需安装 Java、Gradle、Android Studio 或 Android SDK。

提交代码后，GitHub → Actions → **Android APK** 会执行单元测试、Android Lint 和 APK 编译。通过后，在该次运行的 Artifacts 下载 **student-schedule-apk**，解压得到 `app-debug.apk`，传到 Android 手机安装。也可以在 Actions 手动点击 Run workflow。

这是供个人试用的 Debug 签名包。正式持续发布前应配置固定的私有签名密钥；签名不同的包无法直接覆盖安装。

## 代码

- `MainActivity.java`：原生界面、编辑表单、离线保存。
- `ScheduleRules.java`：学期周次、上课日期及时间校验。
- `ScheduleRulesTest.java`：开学周边界、周次范围、课程匹配、时间边界测试。
- `.github/workflows/android.yml`：完全在 GitHub 上准备环境、校验和编译。

构建版本固定为 AGP 8.7.3、Gradle 8.9、Java 17、compile SDK 35，兼容关系参见 [Android 官方文档](https://developer.android.com/build/releases/agp-8-7-0-release-notes)。
