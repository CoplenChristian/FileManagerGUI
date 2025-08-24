# FileManagerGUI  

A modern **Java Swing desktop utility** for exploring drives, calculating folder sizes, and safely managing files.  
Built as a Maven project with modular code, persistent user settings, and a clean UI.  

---

## ✨ Features  

- **Explorer Mode**
  - Browse folders and view their contents in a sortable table
  - Double-click or press `Enter` to navigate into subfolders
  - Quick-access buttons for common folders (Documents, Desktop, Downloads, AppData)

- **Size Analysis**
  - Calculate sizes of all subfolders (parallelized for speed)
  - Human-readable size formatting (B, KiB, MiB, GiB, TiB)
  - Color-coded size emphasis:
    - 🔴 **Red** = > 1 GiB
    - 🟠 **Orange** = > 256 MiB

- **Top-K Largest Folders**
  - Scan a drive and find the **Top 5 largest folders**
  - Ancestor/descendant filtering avoids duplicate nesting results

- **File Deletion**
  - Select multiple rows and delete safely
  - Options:
    - Use Recycle Bin / Trash (with fallback to permanent delete)
    - Always permanently delete
  - Confirmation prompt before permanent deletions (configurable)

- **Drive Information**
  - Drive selector combo box
  - Real-time free and total space display, updating after deletions

- **Settings (Persisted in JSON)**
  - Theme: Light / Dark (applied recursively across the UI)
  - Delete behavior: Recycle Bin first or permanent
  - Confirm before permanent delete
  - Settings stored in `settings.json` (excluded from Git)  
    → Template: `settings.default.json`

- **Performance**
  - Multi-threaded folder scanning
  - Caching layer with configurable TTL (time-to-live)
  - Maven-based project structure for easy builds & dependency management

---

## 📸 Screenshot  

![screenshot](docs/screenshot.png)  
*(Dark theme with Explorer view open)*  

---

## 🚀 Getting Started  

### Prerequisites  
- Java 17+ (tested on Java 17 & Java 23)  
- Maven 3.8+  

### Build  
```bash
mvn clean package
```

### Run  
```bash
java -jar target/FileManagerGUI-0.0.1-SNAPSHOT.jar
```

---

## ⚙️ Settings  

The app creates a local `settings.json` in the working directory.  
This file is **ignored by Git** (see `.gitignore`).  

A default template is provided as `settings.default.json`. Copy and rename it:  

```bash
cp settings.default.json settings.json
```

Edit to customize:  
```json
{
  "theme": "LIGHT",
  "alwaysPermanentDelete": false,
  "confirmPermanentDelete": true
}
```

---

## 🛠 Project Structure  

```
FileManagerGUI/
├── src/main/java/CoplenChristian/FileManagerGUI/
│   ├── ui/          # Swing UI classes
│   ├── scan/        # Folder scanning & Top-K logic
│   └── util/        # Helpers: cache, human-readable size, settings
├── src/test/java/   # JUnit 5 tests
├── target/          # Maven build output
├── settings.json    # Local settings (ignored by Git)
├── settings.default.json
├── pom.xml
└── README.md
```

---

## 🤝 Collaboration  

This project was developed by **Christian Coplen** with heavy assistance from **ChatGPT (OpenAI GPT-5)**,  
who provided architectural guidance, performance optimizations, and code generation for features like:  

- Multi-threaded folder scanning  
- Cache system with TTL  
- Dark/Light theming  
- JSON-based settings persistence  
- Drive info integration  

---

## 📜 License  

MIT License — feel free to fork, modify, and contribute.  

---

## 📌 Roadmap  

- [ ] Drag & drop support for folders  
- [ ] Search bar for files/folders  
- [ ] Export reports (CSV/Excel)  
- [ ] JavaFX UI (modern alternative to Swing)  
- [ ] Cross-platform trash/recycle bin handling  

---

## 🙌 Acknowledgements  

Special thanks to **ChatGPT (GPT-5)** for iterative design and development assistance throughout the project.  
