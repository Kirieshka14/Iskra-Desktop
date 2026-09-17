const { app, BrowserWindow, session, Tray, Menu } = require("electron");
const path = require("node:path");
const fs = require("node:fs");

let mainWindow = null;
let tray = null;

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

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 880,
    minHeight: 600,
    backgroundColor: "#0d0f12",
    title: clientConfig.clientName || "Iskra Desktop",
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
  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
