# Android UI Automator Agent

APK agent untuk HeyPico yang menyediakan UI Automator HTTP server tanpa memerlukan ADB.

## 🎯 Tujuan

Menggantikan dependency ADB dengan HTTP API yang bisa diakses via Zrok tunnel dari MCP server.

## 📱 Fitur

- **Auto-start Service**: Jalan otomatis setelah boot device
- **HTTP Server**: Listen di port 6790 (Appium style) atau 7912 (UIAutomator2 style)
- **WebDriver API**: Compatible dengan Appium WebDriver protocol
- **JSON-RPC API**: Compatible dengan UIAutomator2 JSON-RPC
- **Background Service**: Jalan di background tanpa UI
- **Zrok Ready**: Siap di-share via Zrok tunnel

## 🏗️ Arsitektur

```
Android Device:
├── APK Agent (auto-start)
│   ├── AgentService (background)
│   ├── BootReceiver (auto-start)
│   └── HTTP Server (NanoHTTPD)
│       ├── Port 6790 (Appium WebDriver)
│       └── Port 7912 (UIAutomator2 JSON-RPC)
└── Zrok Client (Termux)
    └── zrok share tcp 6790 --public
```

## 🔄 Flow

```
MCP Server → Zrok URL → APK Agent HTTP → UI Automator → Android UI
```

## 🚀 Build via GitHub Actions

1. Fork repository ini
2. GitHub Actions otomatis build APK
3. Download APK dari Artifacts
4. Install di device Android
5. APK auto-start service setelah boot

## 📋 API Endpoints

### WebDriver Style (Port 6790)
- `GET /wd/hub/status` - Server status
- `POST /wd/hub/session` - Create session
- `POST /wd/hub/session/{id}/element` - Find element
- `POST /wd/hub/session/{id}/element/{id}/click` - Click element

### JSON-RPC Style (Port 7912)
- `GET /info` - Server info
- `POST /jsonrpc` - Execute commands

## 🔧 Commands Supported

- **click**: Click element by text/id
- **input_text**: Input text to element
- **start_app**: Launch app by package name
- **press_key**: Press hardware key (HOME, BACK, etc)
- **swipe**: Swipe gesture
- **screenshot**: Capture screen

## 🛠️ Development

Untuk development lokal, gunakan Android Studio dengan project ini.

## 🏭 Production

Untuk ribuan device, gunakan APK hasil build GitHub Actions.
