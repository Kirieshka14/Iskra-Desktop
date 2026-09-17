const { app, BrowserWindow, session, Menu } = require("electron");
const path = require("node:path");
const fs = require("node:fs");

let mainWindow = null;

// Read optional mod configuration
let clientConfig = {
  apiUrl: "https://bacand-iskra.onrender.com",
  webUrl: "https://iskraai.run.place",
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

function createMacMenu() {
  if (process.platform !== "darwin") return;

  const template = [
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
        { role: "reload", label: "Перезагрузить" },
        { role: "forceReload", label: "Принудительно перезагрузить" },
        { role: "toggleDevTools", label: "Инструменты разработчика" },
        { type: "separator" },
        { role: "resetZoom", label: "Масштаб по умолчанию" },
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

  // Load production web frontend or local build
  const targetUrl = clientConfig.webUrl || "https://iskraai.run.place";
  mainWindow.loadURL(targetUrl);

  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

app.whenReady().then(() => {
  createMacMenu();
  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
