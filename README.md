# Neeraj_OEOF

> Android mobile application with an Express backend and Gemini AI integration.

![GitHub stars](https://img.shields.io/github/stars/Nee-sudo/Neeraj_OEOF?style=for-the-badge&logo=github) ![GitHub forks](https://img.shields.io/github/forks/Nee-sudo/Neeraj_OEOF?style=for-the-badge&logo=github) ![GitHub issues](https://img.shields.io/github/issues/Nee-sudo/Neeraj_OEOF?style=for-the-badge&logo=github) ![Last commit](https://img.shields.io/github/last-commit/Nee-sudo/Neeraj_OEOF?style=for-the-badge&logo=github)

![Express.js](https://img.shields.io/badge/Express.js-000000?style=for-the-badge&logo=express&logoColor=white) ![Java (Gradle)](https://img.shields.io/badge/Java%20(Gradle)-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) ![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white) ![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)

---

## 📑 Table of Contents

- [Description](#-description)
- [Key Features](#-key-features)
- [Use Cases](#-use-cases)
- [Tech Stack](#-tech-stack)
- [Quick Start](#-quick-start)
- [Environment Variables](#-environment-variables)
- [Key Dependencies](#-key-dependencies)
- [Available Scripts](#-available-scripts)
- [API Endpoints](#-api-endpoints)
- [Project Structure](#-project-structure)
- [Development Setup](#-development-setup)
- [Contributing](#-contributing)

---

## 📝 Description

Neeraj_OEOF is a full-stack, production-ready system consisting of a native Android mobile application and a TypeScript Express.js backend. Developed for the One Earth application, this project provides a unified architecture that bridges robust mobile interfaces with back-end database operations and real-time communication channels.

On the backend, Node.js and Express handle essential routing operations—including user authentication, posting systems, and messaging—while Socket.IO establishes low-latency, real-time connections. The native Android application interacts seamlessly with these API services and supports AI-driven workflows utilizing Google's Gemini API.

---

## ✨ Key Features

- **🤖 Gemini AI Studio Integration** — Utilizes the Gemini API key to run and test AI Studio application features inside the mobile client.
- **🔌 Real-Time Socket.IO Channels** — Implements Socket.IO on the Express server to enable instant messaging and real-time event updates.
- **🔐 Secure Token Authentication** — Protects API endpoints using JSON Web Tokens (JWT) with dedicated support for both access and refresh tokens.
- **📱 Native Android Client** — A Gradle-configured Android app setup designed for direct local execution and debugging in Android Studio.
- **🗄️ MongoDB and Seeding Support** — Integrates database connectivity with built-in scripts to automatically seed initial data on system startup.

---

## 🎯 Use Cases

- Developing and testing real-time mobile chat and posting applications backed by a TypeScript server.
- Demonstrating end-to-end integration between native Android clients, Express, and Google's Gemini API.

---

## 🛠️ Tech Stack

- **Android Client**: Native Java & Kotlin, Gradle (Kotlin DSL)
- **Backend**: Node.js, Express.js, TypeScript
- **Database & Real-time**: MongoDB, Socket.IO

### Key Ecosystem Tools
- Google Gemini AI SDK
- Firebase Admin SDK
- JSON Web Tokens (JWT)

---

## ⚡ Quick Start

Follow these steps to spin up the development environment quickly.

```bash
# 1. Clone the repository
git clone https://github.com/Nee-sudo/Neeraj_OEOF.git

# 2. Install dependencies
npm install

# 3. Configure environment
cp .env.example .env   # then fill in the values

# 4. Start the dev server
npm run start
```

---

## 🔑 Environment Variables

Create a `.env` file in the root directory and configure the following variables (refer to `.env.example`):

```bash
GEMINI_API_KEY=
MONGO_URI=
DATABASE_URL=
JWT_SECRET=
JWT_REFRESH_SECRET=
PORT=
```

---

## 📦 Key Dependencies

These are the core external packages driving the backend logic:

```text
cors: ^2.8.5
dotenv: ^16.3.1
express: ^4.18.2
jsonwebtoken: ^9.0.2
firebase-admin: ^12.1.0
mongodb: ^6.3.0
socket.io: ^4.7.2
```

---

## 🚀 Available Scripts

Run these scripts from the root directory to build, run, or seed the backend:

* **Build** — `npm run build`
* **Start** — `npm run start`
* **Dev** — `npm run dev`
* **Seed** — `npm run seed`

---

## 🌐 API Endpoints

### System Health
```http
GET /api/health
```

### Additional Endpoints
<!-- TODO: Document the following routes detected in the codebase structure -->
- **Auth Routes** (`/api/auth`) — Handles registration, login, and token refreshes.
- **Chat Routes** (`/api/chats`) — Manages real-time rooms and historical messages.
- **Post Routes** (`/api/posts`) — Coordinates social post publishing and retrieval.
- **User Routes** (`/api/users`) — User profile management and metadata.

---

## 📁 Project Structure

```text
.
├── .env.example
├── app
│   ├── app
│   │   ├── build.gradle.kts
│   │   └── src
│   │       ├── main
│   │       │   └── java
│   │       │       └── ...
│   │       └── test
│   │           └── java
│   │               └── ...
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src
│       ├── androidTest
│       │   └── java
│       │       └── com
│       │           └── ...
│       ├── main
│       │   ├── AndroidManifest.xml
│       │   ├── java
│       │   │   └── com
│       │       └── ...
│       │   └── res
│       │       ├── drawable
│       │       │   └── ...
│       │       ├── mipmap-anydpi-v26
│       │       │   └── ...
│       │       ├── mipmap-hdpi
│       │       │   └── ...
│       │       ├── mipmap-mdpi
│       │       │   └── ...
│       │       ├── mipmap-xhdpi
│       │       │   └── ...
│       │       ├── mipmap-xxhdpi
│       │       │   └── ...
│       │       ├── mipmap-xxxhdpi
│       │       │   └── ...
│       │       ├── values
│       │       │   └── ...
│       │       └── xml
│       │           └── ...
│       └── test
│           ├── java
│           │   └── com
│           │       └── ...
│           └── screenshots
│               └── greeting.png
├── backend
│   ├── backend
│   │   ├── config
│   │   │   └── database.ts
│   │   ├── controllers
│   │   │   ├── authController.ts
│   │   │   ├── chatController.ts
│   │   │   └── postController.ts
│   │   ├── models
│   │   │   ├── ChatMessage.ts
│   │   │   ├── ChatRoom.ts
│   │   │   ├── Comment.ts
│   │   │   ├── Counter.ts
│   │   │   ├── Post.ts
│   │   │   └── User.ts
│   │   ├── package.json
│   │   ├── routes
│   │   │   ├── auth.ts
│   │   │   ├── chats.ts
│   │   │   ├── posts.ts
│   │   │   └── users.ts
│   │   ├── seed.ts
│   │   ├── server.ts
│   │   ├── sockets
│   │   │   └── socketHandler.ts
│   │   └── tsconfig.json
│   ├── config
│   │   └── database.ts
│   ├── controllers
│   │   ├── authController.ts
│   │   ├── chatController.ts
│   │   └── postController.ts
│   ├── models
│   │   ├── ChatMessage.ts
│   │   ├── ChatRoom.ts
│   │   ├── Comment.ts
│   │   ├── Counter.ts
│   │   ├── Post.ts
│   │   └── User.ts
│   ├── package.json
│   ├── routes
│   │   ├── auth.ts
│   │   ├── chats.ts
│   │   ├── posts.ts
│   │   └── users.ts
│   ├── seed.ts
│   ├── server.ts
│   ├── sockets
│   │   └── socketHandler.ts
│   └── tsconfig.json
├── build.gradle.kts
├── gradle
│   └── libs.versions.toml
├── gradle.properties
├── metadata.json
└── settings.gradle.kts
```

---

## 🛠️ Development Setup

### Node.js / JavaScript Backend
1. Install Node.js (v18+ recommended)
2. Install dependencies: `npm install` (or `yarn` / `pnpm install` / `bun install`)
3. Start the dev server: see the **Quick Start** above

### Android Client (Gradle)
1. Open Android Studio (Ladybug or newer recommended).
2. Select **Open an Existing Project** and navigate to the `/app` directory inside this repository.
3. Wait for the Gradle sync to complete successfully.
<!-- TODO: Add any specific configuration steps for local run, e.g., local.properties configs or IP adjustment for localhost connections -->
4. Connect a physical Android device or start an emulator, then click **Run 'app'**.

---

## 👥 Contributing

Contributions are welcome! Here's the standard flow:

1. **Fork** the repository
2. **Clone** your fork: `git clone https://github.com/Nee-sudo/Neeraj_OEOF.git`
3. **Branch**: `git checkout -b feature/your-feature`
4. **Commit**: `git commit -m 'feat: add some feature'`
5. **Push**: `git push origin feature/your-feature`
6. **Open** a pull request

Please follow the existing code style and include tests for new behavior where applicable.

---
*This README was generated with ❤️ by [ReadmeBuddy](https://readmebuddy.com)*
