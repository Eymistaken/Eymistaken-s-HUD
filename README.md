# ⚔️ Eymistaken's HUD (SimpleCPS)

**A lightweight, fully customizable PvP HUD mod for Minecraft Fabric.** *Includes CPS, FPS, Ping, Keystrokes, and an advanced Combo Counter.*

![Version](https://img.shields.io/badge/Minecraft-1.21.11-green?style=flat&logo=minecraft)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue?style=flat&logo=fabric)
![License](https://img.shields.io/badge/License-MIT-yellow)

## ✨ Features

This mod is designed for both casual players and competitive PvP enthusiasts. It provides essential information without cluttering your screen, thanks to the **Smart Stacking** system.

### 🛡️ Combat Mode Switch (New in v1.0.5!)
Adapt the mod to your game version's mechanics.
* **Modern Mode (1.9+):** The Combo Counter respects attack cooldowns. Spam-clicking (weak hits) will **NOT** increase your combo count. The counter freezes until you land a fully charged hit. Perfect for tracking critical hits.
* **Classic Mode (1.8):** Every hit counts immediately, regardless of cooldown. Old-school spam clicking style.

### 🥊 Advanced Combo Counter
* **Player-Only Detection:** Ignores mobs, tracks hits on players only.
* **Target Switching:** Option to keep your combo streak alive when switching between targets.
* **Smart Reset:** Configurable timeout (0.5s - 10s) and damage reset logic (reset on any damage vs. target damage only).

### ⌨️ Keystrokes
* Visualize your movement keys (WASD + Space).
* **Modes:** Letters (WASD), Arrows (▲◀▼▶), or Custom Text.
* Full RGB/Rainbow support for both text and background.

### 📊 Essential HUD Elements
* **CPS Counter:** Displays Left and Right clicks per second.
* **FPS Display:** Monitor your frame rate with custom labels.
* **Ping Display:** See your latency to the server in real-time.

### 🎨 Customization & Smart Stacking
* **Smart Stacking:** Place multiple elements (e.g., CPS, FPS, Combo) in the same corner, and they will automatically stack vertically. No overlap!
* **Full Control:** Customize position, scale, opacity, text colors, background colors, and rainbow modes for *every* module individually.

---

## 📸 Screenshots

| Combo Counter | Keystrokes |
|:---:|:---:|
| *![Combo GIF Placeholder](https://via.placeholder.com/400x200?text=Place+Combo+GIF+Here)* | *![Keystrokes Placeholder](https://via.placeholder.com/400x200?text=Place+Keystrokes+IMG+Here)* |
| *Modern Mode Logic* | *RGB Mode Enabled* |

*(Screenshots coming soon)*

---

## ⚙️ Configuration

You can configure the mod in-game using **Mod Menu**.

1.  Press `ESC` and click on **Mods**.
2.  Search for **Eymistaken's HUD**.
3.  Click the **Config** icon.

**Dependencies:**
* [Fabric API](https://modrinth.com/mod/fabric-api)
* [Cloth Config API](https://modrinth.com/mod/cloth-config)
* [Mod Menu](https://modrinth.com/mod/modmenu) (Optional, for in-game settings)

---

## 📥 Installation

1.  Download and install **Fabric Loader**.
2.  Download **Eymistaken's HUD**, **Fabric API**, and **Cloth Config**.
3.  Place the `.jar` files into your `.minecraft/mods` folder.
4.  Launch the game!

---

## 🤝 Contributing

Suggestions and Pull Requests are welcome! If you find a bug, please report it in the [Issues](https://github.com/Eymistaken/SimpleCPS/issues) tab.

---

**Developed by Eymistaken** *Powered by Fabric & Cloth Config*
