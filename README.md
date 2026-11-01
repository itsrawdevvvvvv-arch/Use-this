# Gaffer

An AI agent that lives on your Android phone.

You type what you want done. Gaffer reads the screen, taps, types, scrolls, opens apps, drafts your mail and messages, and reports back what actually happened. It is a real on-device agent loop, not a chatbot with a shortcut menu.

Built for sideloading. Android 8.0 (API 26) and up.

---

## What it actually does

Gaffer runs a tool-calling loop against any OpenAI-compatible model endpoint. Each turn it can:

| Tool | What it does |
|---|---|
| `read_screen` | Dumps the foreground app, every button, field and label, and each element's tap position |
| `tap_text` / `tap_point` | Taps by label, or by precise 0-1 screen coordinates |
| `swipe` | Scrolls lists and pages |
| `type_text` | Fills the focused field |
| `press` | Back, home, recents, notifications, enter |
| `open_app` / `list_apps` | Finds and launches anything installed |
| `read_notifications` | Reads what came in while it was busy (optional permission) |
| `remember` / `recall` | A persistent scratchpad for clients, leads, preferences |
| `send_email` | Opens a pre-filled draft in your mail app |
| `send_whatsapp` | Opens a WhatsApp chat with the message pre-filled |
| `share_text` | Share sheet for Instagram, X, YouTube and friends |
| `add_calendar_event` | Pre-filled calendar event |
| `web_fetch` | Fetches a URL and returns readable text, for research and lead finding |
| `wait` | Pauses for a screen to load |
| `ask_user` | Hands control back to you and stops |
| `task_done` | Ends the run with a summary |

## What it deliberately will not do

These are hard-coded, not prompt suggestions:

- **It never enters passwords, PINs, OTPs or card numbers.** It calls `ask_user` and waits for you.
- **It never taps or types inside banking, UPI or wallet apps.** A package guard refuses before the action reaches the screen.
- **It never sends money, places orders, or commits you to anything.**
- **It never claims a message was sent when it only opened a draft.** Outbound tools stop at the pre-filled compose screen, and every one of them asks for your approval first (you can switch that off in Settings, but it is on by default).

---

## Install

1. Download `app-release.apk` from the Releases page.
2. Open it on the phone and allow installing from this source when Android asks.
3. Launch Gaffer, tap the gear icon, and fill in:
   - **Base URL** — your provider's OpenAI-compatible endpoint, ending in `/v1`
   - **Model** — the model name that endpoint expects
   - **API key**
4. In Settings, tap **Accessibility (screen control)** and switch Gaffer on. This is what gives it eyes and hands.
5. Optional: tap **Notification access** if you want it to read notifications.

### Providers

Anything that speaks `POST {base}/chat/completions` with tool calling works: OpenAI, Groq, OpenRouter, Together, Fireworks, Sarvam, a local vLLM box, Ollama with an OpenAI shim, LM Studio. Only the base URL and model name change.

Tool calling is required. If the model does not support `tools`, Gaffer will just chat and never act.

---

## How the loop works

```
you type a task
      |
      v
+-------------------------------+
|  AgentEngine.runLoop()        |
|  model -> tool_calls -> tools |
|    ^                        | |
|    +---- results -----------+ |
+-------------------------------+
      |                    |
      v                    v
 ToolRunner          ScreenAgentService
 (intents, web,      (accessibility tree,
  memory)             taps, swipes, text)
```

Every step: the model asks for a tool, the tool runs against the live phone, the result goes back into the conversation, and the model decides what to do next. It stops when the model calls `task_done`, calls `ask_user`, hits the step limit, or you press Stop.

A foreground notification keeps the process alive while the agent is driving other apps. It disappears when the task ends.

---

## Build it yourself

```bash
git clone https://github.com/<you>/Use-this.git
cd Use-this
```

You need JDK 17 and the Android SDK with platform 35 and build-tools 35.0.0. Then either open it in Android Studio or:

```bash
./gradlew :app:assembleRelease
```

For a signed release, create `keystore.properties` in the project root (it is gitignored):

```properties
storeFile=/absolute/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file the release build is unsigned and the signing config is skipped.

### Layout

```
app/src/main/java/dev/rawdev/gaffer/
  MainActivity.kt              single activity, Compose host
  agent/
    AgentEngine.kt             the loop, approval gate, conversation state
    LlmClient.kt               OpenAI-compatible client (OkHttp)
    ToolCatalog.kt             tool JSON schemas handed to the model
    ToolRunner.kt              executes tools, blocks money apps
    Models.kt                  UI and state models
  data/
    Prefs.kt                   API key, endpoint, behaviour settings
    MemoryStore.kt             persistent notes
  service/
    ScreenAgentService.kt      accessibility: read tree, tap, swipe, type
    NotificationRelayService.kt  optional notification reader
    AgentKeepAliveService.kt   foreground service, keeps the process alive
  ui/
    ChatScreen.kt              chat, approvals, permission banner
    SettingsScreen.kt          endpoint, model, key, toggles, memory
    Theme.kt                   amber-on-ink theme
  util/
    Actions.kt                 intents: mail, WhatsApp, share, calendar
    Html.kt                    HTML to text for web_fetch
```

---

## Honest limitations

- **This is sideload-only.** Google Play policy forbids using the accessibility API for automation that is not accessibility. Do not try to publish it there.
- **Screen automation is brittle.** Apps change their layouts, and a tap that worked yesterday can miss today. Gaffer re-reads the screen after every action and retries differently, but expect the occasional dead end. Keep it to two or three attempts and then take over.
- **Consumer app automation is a terms-of-service risk.** Driving Instagram or WhatsApp through the UI can get accounts restricted. For client messaging at any volume, use Meta's official Business APIs instead. Gaffer's `send_whatsapp` uses the official `wa.me` deep link, which is the safe route.
- **OTP and biometric screens are walls.** Design around them, not through them.
- **The model can be wrong.** It will sometimes tap the wrong thing or misread a screen. That is exactly why every outbound action asks for your approval.
- **The API key sits in app-private storage.** That is fine against other apps, but it is not hardware-backed encryption. Use a key you can revoke, and set a spend limit with your provider.
- **No streaming.** Responses arrive whole, which makes tool-calling cleaner at the cost of feeling slower.

---

## Licence

MIT. Do what you like with it.
