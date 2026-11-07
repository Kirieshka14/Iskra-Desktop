# Iskra-Desktop

Десктопный клиент IskraAi для Windows, Linux и macOS. Работает напрямую с сервером API без встроенного браузера.

## Установка готовой сборки

Скачайте подходящий файл из раздела **Releases**:

- **Windows**:
  - Скачайте `.exe` или `.msi` установщик.
  - Запустите его и следуйте инструкциям на экране. Ярлык появится в меню «Пуск» и на рабочем столе.
- **Linux**:
  - **AppImage (любой дистрибутив)**:
    ```bash
    chmod +x Iskra-Linux-x86_64.AppImage
    ./Iskra-Linux-x86_64.AppImage
    ```
  - **Debian / Ubuntu / Mint (.deb)**:
    ```bash
    sudo apt install ./iskra_1.8.2-1_amd64.deb
    ```
  - **Портативный архив (.tar.gz)**:
    ```bash
    tar -xzf Iskra-Linux-x86_64.tar.gz
    ./iskra/bin/iskra
    ```
- **macOS**:
  - Скачайте `.dmg` образ, откройте его и перетащите приложение `iskra` в папку `Applications`.

## Настройка (`iskra.config.json`)

При запуске клиент читает параметры подключения из файла `iskra.config.json` в рабочей директории (если файл отсутствует, используются значения по умолчанию):

```json
{
  "clientName": "Iskra Desktop Client",
  "clientVersion": "0.8.1.1",
  "targetPlatform": "desktop",
  "webUrl": "https://iskraai.pages.dev",
  "apiUrl": "https://bacand-iskra.onrender.com",
  "clientKey": ""
}
```

- `apiUrl` — адрес сервера API, к которому подключается клиент.
- `clientKey` — опциональный ключ доступа клиента (`X-Iskra-Client-Key`).
- `webUrl` — адрес веб-версии платформы.

Сессия авторизации сохраняется локально в домашней директории пользователя (`~/.iskra/session.json`).

## Сборка и запуск из исходников

### Требования
- Установленный **JDK 17** или выше.

### Запуск в режиме разработки
```bash
./gradlew run
```

### Сборка установщиков
Команды собирают установочный пакет под текущую операционную систему:

```bash
# Linux (.deb)
./gradlew packageDeb

# Windows (.msi и .exe)
./gradlew packageMsi
./gradlew packageExe

# macOS (.dmg)
./gradlew packageDmg
```

Готовые установочные файлы создаются в директории `build/compose/binaries/main/`.
