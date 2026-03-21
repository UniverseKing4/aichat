# AI Chat

AI Chat is an Android application that provides AI-powered chat capabilities using the Pollinations AI API.

## Features

- 🤖 Multiple AI models (12 free models including OpenAI, Gemini, Claude, DeepSeek)
- 🖼️ Image analysis support with vision models
- 🌓 Dark/Light theme
- 📱 Material Design 3
- 🔄 Automatic builds via GitHub Actions
- 🎨 Markdown rendering for responses
- ⚡ Fast & responsive UI
- 🔒 Optional API key (works without one)

## Build

The app automatically builds on every push to `main` branch and creates releases.

### Manual Build

```bash
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/release/`

## Monitor Script

Use the included monitor script to track builds and download releases:

```bash
./monitor.sh
```

## Configuration

### API Key (Optional)

The app works without an API key using the default proxy. For custom API keys:

1. Open Settings in the app
2. Enter your Pollinations AI API key
3. Get a key at: https://enter.pollinations.ai

### Models

Available free models:
- `openai` - OpenAI GPT-5 Mini (Fast & Balanced)
- `openai-fast` - OpenAI GPT-5 Nano (Ultra Fast)
- `qwen-coder` - Qwen3 Coder 30B (Code Generation)
- `mistral` - Mistral Small 3.2 24B
- `gemini-fast` - Google Gemini 2.5 Flash Lite
- `deepseek` - DeepSeek V3.2 (Reasoning)
- `claude-fast` - Anthropic Claude Haiku 4.5
- `kimi` - Moonshot Kimi K2.5 (Vision & Multi-Agent)
- `nova-fast` - Amazon Nova Micro (Ultra Fast)
- `glm` - Z.ai GLM-5 (Long Context)
- `minimax` - MiniMax M2.5 (Coding & Agentic)
- `polly` - Polly AI Assistant (GitHub & Web Tools)

## Development

### Requirements

- Android Studio
- JDK 17+
- Android SDK 34

### Project Structure

```
app/
├── src/main/
│   ├── java/com/aichat/app/
│   │   ├── MainActivity.kt
│   │   ├── AIChatApp.kt
│   │   └── ImagePagerAdapter.kt
│   ├── res/
│   └── AndroidManifest.xml
├── build.gradle.kts
└── release.keystore
```

## License

This project is open source.
