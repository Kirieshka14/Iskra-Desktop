const { app, BrowserWindow, session, Menu, Tray, shell, nativeImage } = require("electron");
const path = require("node:path");
const fs = require("node:fs");

let mainWindow = null;
let appTray = null;

const DEFAULT_WEB_URL = "https://iskraai.pages.dev";

// Read optional mod configuration
let clientConfig = {
  apiUrl: "https://bacand-iskra.onrender.com",
  webUrl: DEFAULT_WEB_URL,
  clientKey: "",
};

try {
  const cfgPath = path.join(__dirname, "iskra.config.json");
  if (fs.existsSync(cfgPath)) {
    clientConfig = { ...clientConfig, ...JSON.parse(fs.readFileSync(cfgPath, "utf8")) };
  }
} catch (err) {
  console.warn("Could not load iskra.config.json, using defaults", err);
}

// Ensure webUrl is valid or fallback to production
if (!clientConfig.webUrl || clientConfig.webUrl.includes("run.place")) {
  clientConfig.webUrl = DEFAULT_WEB_URL;
}

function buildErrorHtml(errDesc, targetUrl) {
  return `<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <title>Iskra Desktop — Ошибка подключения</title>
  <style>
    body {
      margin: 0;
      padding: 0;
      background: #0d0f12;
      color: #f4f4f5;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100vh;
      text-align: center;
      box-sizing: border-box;
    }
    .card {
      max-width: 480px;
      padding: 40px 32px;
      background: #14171d;
      border: 1px solid #27272a;
      border-radius: 20px;
      box-shadow: 0 20px 40px rgba(0,0,0,0.5);
    }
    .badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 8px;
      background: rgba(244, 63, 94, 0.15);
      color: #fb7185;
      border: 1px solid rgba(244, 63, 94, 0.3);
      font-size: 12px;
      font-weight: 600;
      margin-bottom: 16px;
    }
    h1 {
      font-size: 20px;
      margin: 0 0 10px 0;
      color: #fff;
    }
    p {
      font-size: 13px;
      color: #a1a1aa;
      line-height: 1.6;
      margin: 0 0 24px 0;
    }
    .url {
      display: block;
      font-family: monospace;
      font-size: 11px;
      color: #71717a;
      word-break: break-all;
      margin-top: 6px;
    }
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 10px 24px;
      border-radius: 12px;
      font-size: 13px;
      font-weight: 600;
      color: #fff;
      background: linear-gradient(135deg, #f43f5e, #f59e0b);
      border: none;
      cursor: pointer;
      box-shadow: 0 4px 14px rgba(244, 63, 94, 0.3);
      transition: all 0.2s ease;
      text-decoration: none;
    }
    .btn:hover {
      filter: brightness(1.1);
      transform: translateY(-1px);
    }
    .btn-secondary {
      background: #27272a;
      color: #d4d4d8;
      box-shadow: none;
      margin-left: 8px;
    }
    .btn-secondary:hover {
      background: #3f3f46;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge">Ошибка сети</div>
    <h1>Не удалось загрузить Iskra</h1>
    <p>
      Проверьте интернет-соединение или доступность сервера платформы.
      <span class="url">Адрес: ${targetUrl}<br>Код: ${errDesc || "UNKNOWN_ERROR"}</span>
    </p>
    <div>
      <button class="btn" onclick="location.href='${targetUrl}'">Повторить попытку</button>
      <button class="btn btn-secondary" onclick="location.href='${DEFAULT_WEB_URL}'">Основное зеркало</button>
    </div>
  </div>
</body>
</html>`;
}

function createMacMenu() {
  const isMac = process.platform === "darwin";

  const template = [
    ...(isMac
      ? [
          {
            label: app.name,
            submenu: [
              { role: "about", label: "О программе Iskra" },
              { type: "separator" },
              { role: "services" },
              { type: "separator" },
              { role: "hide", label: "Скрыть Iskra" },
              { role: "hideOthers", label: "Скрыть остальные" },
              { role: "unhide", label: "Показать все" },
              { type: "separator" },
              { role: "quit", label: "Завершить Iskra" },
            ],
          },
        ]
      : [
          {
            label: "Файл",
            submenu: [{ role: "quit", label: "Выход" }],
          },
        ]),
    {
      label: "Правка",
      submenu: [
        { role: "undo", label: "Отменить" },
        { role: "redo", label: "Повторить" },
        { type: "separator" },
        { role: "cut", label: "Вырезать" },
        { role: "copy", label: "Скопировать" },
        { role: "paste", label: "Вставить" },
        { role: "selectAll", label: "Выбрать всё" },
      ],
    },
    {
      label: "Вид",
      submenu: [
        {
          label: "Перезагрузить",
          accelerator: "CmdOrCtrl+R",
          click: () => mainWindow && mainWindow.reload(),
        },
        {
          label: "Инструменты разработчика",
          accelerator: "F12",
          click: () => mainWindow && mainWindow.webContents.toggleDevTools(),
        },
        { type: "separator" },
        { role: "resetZoom", label: "Масштаб 100%" },
        { role: "zoomIn", label: "Увеличить" },
        { role: "zoomOut", label: "Уменьшить" },
        { type: "separator" },
        { role: "togglefullscreen", label: "Полноэкранный режим" },
      ],
    },
    {
      label: "Окно",
      submenu: [
        { role: "minimize", label: "Свернуть" },
        { role: "zoom", label: "Развернуть" },
        { type: "separator" },
        { role: "front", label: "Все окна на передний план" },
      ],
    },
  ];

  const menu = Menu.buildFromTemplate(template);
  Menu.setApplicationMenu(menu);
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1240,
    height: 840,
    minWidth: 900,
    minHeight: 640,
    backgroundColor: "#0d0f12",
    title: clientConfig.clientName || "Iskra Desktop",
    titleBarStyle: process.platform === "darwin" ? "hiddenInset" : "default",
    trafficLightPosition: { x: 16, y: 16 },
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
    },
  });

  // Inject Client Key into all outgoing network requests to Iskra backend
  if (clientConfig.clientKey) {
    session.defaultSession.webRequest.onBeforeSendHeaders((details, callback) => {
      details.requestHeaders["X-Iskra-Client-Key"] = clientConfig.clientKey;
      callback({ requestHeaders: details.requestHeaders });
    });
  }

  // Fallback and error handling
  let triedFallback = false;
  const targetUrl = clientConfig.webUrl || DEFAULT_WEB_URL;

  mainWindow.webContents.on("did-fail-load", (event, errorCode, errorDescription, validatedURL) => {
    console.error("Failed to load URL:", validatedURL, errorCode, errorDescription);
    if (!triedFallback && validatedURL !== DEFAULT_WEB_URL) {
      triedFallback = true;
      console.log("Retrying with fallback mirror:", DEFAULT_WEB_URL);
      mainWindow.loadURL(DEFAULT_WEB_URL);
    } else {
      mainWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(buildErrorHtml(errorDescription, targetUrl))}`);
    }
  });

  // Open external links in user's default system browser
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    const isInternal = url.startsWith(DEFAULT_WEB_URL) || (clientConfig.webUrl && url.startsWith(clientConfig.webUrl));
    if (!isInternal && (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("mailto:"))) {
      shell.openExternal(url);
      return { action: "deny" };
    }
    return { action: "allow" };
  });

  // Handle history keyboard shortcuts (Alt+Left, Alt+Right)
  mainWindow.webContents.on("before-input-event", (event, input) => {
    if (input.type === "keyDown") {
      if (input.alt && input.key === "ArrowLeft" && mainWindow.webContents.canGoBack()) {
        mainWindow.webContents.goBack();
        event.preventDefault();
      } else if (input.alt && input.key === "ArrowRight" && mainWindow.webContents.canGoForward()) {
        mainWindow.webContents.goForward();
        event.preventDefault();
      }
    }
  });

  // Mouse navigation buttons (back/forward)
  mainWindow.on("app-command", (e, cmd) => {
    if (cmd === "browser-backward" && mainWindow.webContents.canGoBack()) {
      mainWindow.webContents.goBack();
    } else if (cmd === "browser-forward" && mainWindow.webContents.canGoForward()) {
      mainWindow.webContents.goForward();
    }
  });

  mainWindow.loadURL(targetUrl);

  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

function createTray() {
  try {
    // Generate a simple 16x16 monochrome icon for system tray if icon file isn't present
    const iconPath = path.join(__dirname, "build", "icon.png");
    let icon;
    if (fs.existsSync(iconPath)) {
      icon = nativeImage.createFromPath(iconPath);
    } else {
      // 16x16 transparent image with dot
      icon = nativeImage.createEmpty();
    }

    appTray = new Tray(icon);
    const contextMenu = Menu.buildFromTemplate([
      {
        label: "Показать Iskra",
        click: () => {
          if (mainWindow) {
            if (mainWindow.isMinimized()) mainWindow.restore();
            mainWindow.show();
            mainWindow.focus();
          } else {
            createWindow();
          }
        },
      },
      {
        label: "Перезагрузить",
        click: () => mainWindow && mainWindow.reload(),
      },
      { type: "separator" },
      {
        label: "Выход",
        click: () => {
          app.isQuitting = true;
          app.quit();
        },
      },
    ]);

    appTray.setToolTip("Iskra Desktop");
    appTray.setContextMenu(contextMenu);
    appTray.on("click", () => {
      if (mainWindow) {
        if (mainWindow.isVisible()) {
          mainWindow.focus();
        } else {
          mainWindow.show();
        }
      }
    });
  } catch (err) {
    console.warn("Tray creation skipped:", err.message);
  }
}

app.whenReady().then(() => {
  createMacMenu();
  createWindow();
  createTray();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
