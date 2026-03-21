# AI Chat

AI Chat is a powerful Android application that provides comprehensive AI-powered chat capabilities using the Pollinations AI API.

## Features

- 💬 **Full Chat Interface** - Natural conversation with AI models
- 🤖 **12 Free AI Models** - OpenAI, Gemini, Claude, DeepSeek, and more
- 🖼️ **Image Analysis** - Attach images to your messages for vision-based AI analysis
- 🎨 **Image Generation** - Create images from text descriptions using FLUX and other models
- 🌓 **Dark/Light Theme** - Beautiful Material Design 3 UI in both themes
- 📱 **Material Design 3** - Modern, polished, and responsive interface
- 🔄 **Automatic Builds** - CI/CD via GitHub Actions
- ⚡ **Fast & Smooth** - Optimized performance with no lags
- 🔒 **Optional API Key** - Works without one using default service
- ✨ **Markdown Support** - Rich text formatting in responses

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

The app works without an API key. For custom API keys:

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

## How to Use

### Text Chat
1. Type your message in the input field
2. Press send to chat with AI
3. Responses appear with markdown formatting

### Image Analysis
1. Tap the gallery icon to attach an image
2. Type your question about the image
3. Send to get AI analysis with vision models

### Image Generation
1. Type a description of the image you want
2. Tap the camera icon to generate
3. AI creates and displays the image

### Features
- **Clear Chat** - Remove all messages from toolbar
- **Model Selection** - Choose from 12 AI models
- **Dark Mode** - Toggle theme from toolbar
- **Settings** - Configure API key

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
│   │   ├── ChatMessage.kt
│   │   └── ChatAdapter.kt
│   ├── res/
│   └── AndroidManifest.xml
├── build.gradle.kts
└── release.keystore
```

## License

This project is open source.
