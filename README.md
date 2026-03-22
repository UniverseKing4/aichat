# AI Chat

AI Chat is a powerful, fully-featured Android application that provides comprehensive AI-powered chat capabilities using the Pollinations AI API.

## Features

### Core Functionality
- 💬 **Full Chat Interface** - Natural conversation with AI models
- 🤖 **23 Free AI Models** - OpenAI, Gemini, Claude, DeepSeek, Perplexity, and more
- 🖼️ **Image Analysis** - Attach images to your messages for vision-based AI analysis
- 🎨 **Image Generation** - Create images from text descriptions using FLUX and other models
- 📝 **Message Management** - Edit, copy, and delete individual messages
- 💾 **Multiple Conversations** - Create, manage, and switch between unlimited conversations
- 📤 **Import/Export** - Backup and restore conversations as JSON files
- 🎯 **System Instructions** - Custom system prompts per conversation
- 🗑️ **Multi-Select Delete** - Batch delete conversations with checkboxes

### User Interface
- 🌓 **Dark/Light Theme** - Beautiful Material Design 3 UI in both themes
- 📱 **Material Design 3** - Modern, polished, and responsive interface
- 🎨 **Sidebar Navigation** - Smooth drawer with conversation list
- 📍 **Scroll Position Memory** - Returns to exact scroll position per conversation
- ⌨️ **Smart Keyboard** - Auto-opens unless sidebar is open, scrolls to show last message
- ✨ **Markdown Support** - Rich text formatting in AI responses

### Performance & Quality
- ⚡ **Fast & Smooth** - Optimized performance with zero lags
- 🔒 **Crash-Proof** - Comprehensive error handling everywhere
- 🚀 **Production-Ready** - Full code review, memory leak prevention, thread-safe
- 🔄 **Automatic Builds** - CI/CD via GitHub Actions
- 📦 **Optimized** - Single HTTP client instance, proper lifecycle management

## Available Models (23)

- **OpenAI:** GPT-5 Mini, GPT-5 Nano, GPT-4o Mini Audio, GPT-5.4 (seraphyn.ai)
- **Google:** Gemini 2.5 Flash Lite, Gemini with Search
- **Anthropic:** Claude Haiku 4.5, Claude Sonnet 4.6 (api.airforce)
- **DeepSeek:** V3.2 - Efficient Reasoning
- **Perplexity:** Sonar Fast, Sonar Reasoning (with Web Search)
- **Mistral:** Small 3.2 24B
- **Qwen:** Qwen3 Coder 30B, Qwen Character, Qwen3Guard 8B
- **Moonshot:** Kimi K2.5 - Vision & Multi-Agent
- **Amazon:** Nova Micro
- **Z.ai:** GLM-5 - 744B MoE
- **MiniMax:** M2.5 - Coding & Agentic
- **Step:** 3.5 Flash
- **MIDIjourney:** AI Music Composition
- **Custom:** NomNom (Web Research), Polly (AI Assistant with GitHub/Web Tools)

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

1. Open Settings (3-dot menu → API Key Settings)
2. Enter your Pollinations AI API key
3. Get a key at: https://enter.pollinations.ai

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

### Conversation Management
- **New Chat:** Tap + button in toolbar
- **Switch Chats:** Open sidebar (swipe from left or tap menu)
- **Rename/Delete:** Long press conversation in sidebar
- **Multi-Delete:** Long press → Select Multiple → check conversations → delete
- **Import/Export:** Use buttons in sidebar

### Message Actions
- **Copy:** Tap copy icon on any message
- **Edit:** Tap edit icon to modify message text
- **Delete:** Tap delete icon with confirmation

### Settings
- **Model Selection:** Tap model icon in toolbar
- **Dark Mode:** Toggle theme from toolbar
- **System Instructions:** 3-dot menu → System Instructions
- **Delete Conversation:** 3-dot menu → Delete Conversation
- **Clear Chat:** Tap clear icon in toolbar

## Technical Details

### Architecture
- **Language:** Kotlin
- **UI:** Material Design 3, ViewBinding
- **Async:** Kotlin Coroutines (Dispatchers.IO for network)
- **HTTP:** OkHttp3 with connection pooling
- **Storage:** SharedPreferences for conversations and messages
- **Image Loading:** Glide
- **Markdown:** Markwon

### Requirements
- Android Studio
- JDK 17+
- Android SDK 34
- Min SDK 24

### Project Structure

```
app/
├── src/main/
│   ├── java/com/aichat/app/
│   │   ├── MainActivity.kt (850+ lines)
│   │   ├── ChatAdapter.kt
│   │   ├── ConversationAdapter.kt
│   │   ├── ConversationManager.kt
│   │   ├── ChatMessage.kt
│   │   ├── Conversation.kt
│   │   └── AIChatApp.kt
│   ├── res/
│   │   ├── layout/
│   │   ├── drawable/
│   │   ├── values/
│   │   └── menu/
│   └── AndroidManifest.xml
├── build.gradle.kts
├── proguard-rules.pro
└── release.keystore
```

## Performance Optimizations

- Single HTTP client instance (connection pooling)
- Proper coroutine lifecycle management
- Memory leak prevention (job cancellation in onDestroy)
- Efficient RecyclerView with ViewBinding
- Scroll position caching per conversation
- ProGuard rules for release builds

## License

This project is open source.

## Version

Current: v0.0.72 (Production-Ready)
