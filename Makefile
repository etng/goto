# Goto —— Android 快捷跳转 App + 局域网 Web 管理

WEB_DIR    := web
ANDROID_DIR := android
ASSETS     := $(ANDROID_DIR)/app/src/main/assets/web
APK        := $(ANDROID_DIR)/app/build/outputs/apk/debug/app-debug.apk
GRADLEW    := cd $(ANDROID_DIR) && ./gradlew

.PHONY: web web-dev apk apk-fast test install uninstall all

## 构建 Web 管理页并拷贝到 App assets
web:
	cd $(WEB_DIR) && npm install && npm run build
	mkdir -p $(ASSETS)
	rm -rf $(ASSETS)/*
	cp -r $(WEB_DIR)/dist/* $(ASSETS)/
	@echo "assets 已更新: $(ASSETS)"

## 本地调试管理页（Vite dev server，需手机端 API 代理时自行处理）
web-dev:
	cd $(WEB_DIR) && npm run dev

## 完整构建 APK（含 Web 产物）
apk: web
	$(GRADLEW) :app:assembleDebug

## 只重新构建 APK（跳过 Web 重打包）
apk-fast:
	$(GRADLEW) :app:assembleDebug

## 单元测试
test:
	$(GRADLEW) :app:testDebugUnitTest

## 构建并安装到已连接设备
install: apk
	adb install -r $(APK)

uninstall:
	adb uninstall cn.y10n.goto

all: apk
