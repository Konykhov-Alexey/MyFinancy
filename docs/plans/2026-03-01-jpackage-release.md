# jpackage Release Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Исправить путь к БД на `~/.myfinancy/myfinancy` и настроить сборку релизного дистрибутива через maven-shade-plugin + release.sh.

**Architecture:** Путь к БД меняется в `hibernate.cfg.xml` с `./data/myfinancy` на `~/.myfinancy/myfinancy` — H2 умеет раскрывать `~`. Для сборки добавляется `maven-shade-plugin`, создающий fat JAR с точкой входа `Launcher`. Скрипт `release.sh` запускает `jpackage` поверх fat JAR и создаёт `.deb`-пакет.

**Tech Stack:** Java 25 (jpackage входит в JDK), Maven 4.0, maven-shade-plugin 3.6.0, H2 2.3.

---

### Task 1: Обновить версию в pom.xml

**Files:**
- Modify: `pom.xml:9`

**Step 1: Изменить версию**

В `pom.xml` строка 9:
```xml
<version>1.0.0</version>
```
(было `1.0-SNAPSHOT`)

**Step 2: Убедиться что компиляция проходит**

```bash
export JAVA_HOME=~/.jdks/corretto-25.0.2
mvn clean compile -q
```
Ожидание: `BUILD SUCCESS`, нет ошибок.

**Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: bump version to 1.0.0"
```

---

### Task 2: Исправить путь к БД

**Files:**
- Modify: `src/main/resources/hibernate.cfg.xml:10`

**Step 1: Поменять URL**

В `hibernate.cfg.xml` строка 10:
```xml
<property name="hibernate.connection.url">jdbc:h2:file:~/.myfinancy/myfinancy;AUTO_SERVER=TRUE</property>
```
(было `./data/myfinancy`)

`~` — стандартная нотация H2 для home-директории пользователя. H2 создаст `~/.myfinancy/` автоматически при первом запуске.

**Step 2: Убедиться что приложение стартует**

```bash
export JAVA_HOME=~/.jdks/corretto-25.0.2
mvn javafx:run
```
Ожидание: приложение открывается, в `~/.myfinancy/` появляются файлы `myfinancy.mv.db` и `myfinancy.trace.db`.

Проверить:
```bash
ls ~/.myfinancy/
```

**Step 3: Commit**

```bash
git add src/main/resources/hibernate.cfg.xml
git commit -m "fix: move H2 database to user home directory (~/.myfinancy/)"
```

---

### Task 3: Добавить maven-shade-plugin в pom.xml

**Files:**
- Modify: `pom.xml` — секция `<build><plugins>`

**Step 1: Добавить плагин**

После блока `maven-surefire-plugin` добавить в `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>org.example.Launcher</mainClass>
                    </transformer>
                </transformers>
                <filters>
                    <filter>
                        <artifact>*:*</artifact>
                        <excludes>
                            <exclude>META-INF/*.SF</exclude>
                            <exclude>META-INF/*.DSA</exclude>
                            <exclude>META-INF/*.RSA</exclude>
                        </excludes>
                    </filter>
                </filters>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Важно:** точка входа — `org.example.Launcher`, не `MainApp`. Это обязательно для fat JAR с JavaFX.

**Step 2: Собрать fat JAR**

```bash
export JAVA_HOME=~/.jdks/corretto-25.0.2
mvn clean package -DskipTests
```
Ожидание: `BUILD SUCCESS`. В `target/` появятся два JAR:
- `MyFinancy-1.0.0.jar` — fat JAR (нужный)
- `original-MyFinancy-1.0.0.jar` — оригинал без зависимостей

Проверить размер fat JAR (должен быть > 20 MB):
```bash
ls -lh target/MyFinancy-1.0.0.jar
```

**Step 3: Проверить что fat JAR запускается**

```bash
~/.jdks/corretto-25.0.2/bin/java -jar target/MyFinancy-1.0.0.jar
```
Ожидание: приложение открывается.

**Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: add maven-shade-plugin for fat JAR packaging"
```

---

### Task 4: Создать release.sh

**Files:**
- Create: `release.sh`

**Step 1: Создать скрипт**

Создать файл `release.sh` в корне проекта:

```bash
#!/usr/bin/env bash
set -e

JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/corretto-25.0.2}"
APP_VERSION="1.0.0"
JAR_NAME="MyFinancy-${APP_VERSION}.jar"

echo "==> Сборка fat JAR..."
export JAVA_HOME
mvn clean package -DskipTests -q

echo "==> Проверка наличия JAR..."
if [ ! -f "target/${JAR_NAME}" ]; then
  echo "Ошибка: target/${JAR_NAME} не найден"
  exit 1
fi

echo "==> Создание дистрибутива через jpackage..."
rm -rf release/
"${JAVA_HOME}/bin/jpackage" \
  --input target \
  --main-jar "${JAR_NAME}" \
  --main-class org.example.Launcher \
  --name "MyFinancy" \
  --app-version "${APP_VERSION}" \
  --description "Приложение для контроля семейного бюджета" \
  --vendor "MyFinancy" \
  --type deb \
  --dest release

echo "==> Готово! Дистрибутив:"
ls -lh release/
```

**Step 2: Сделать скрипт исполняемым**

```bash
chmod +x release.sh
```

**Step 3: Запустить скрипт и проверить результат**

```bash
./release.sh
```
Ожидание:
```
==> Готово! Дистрибутив:
-rw-r--r-- 1 ... ...M ... myfinancy_1.0.0_amd64.deb
```

Проверить что .deb создан:
```bash
ls release/*.deb
```

**Step 4: Добавить release/ в .gitignore**

Проверить `.gitignore`:
```bash
grep "release/" .gitignore || echo "release/" >> .gitignore
```

**Step 5: Commit**

```bash
git add release.sh .gitignore
git commit -m "build: add release.sh script for jpackage distribution"
```

---

### Task 5: Git-тэг релиза

**Step 1: Убедиться что все тесты проходят**

```bash
export JAVA_HOME=~/.jdks/corretto-25.0.2
mvn test
```
Ожидание: `BUILD SUCCESS`.

**Step 2: Поставить тэг**

```bash
git tag -a v1.0.0 -m "Release v1.0.0 — первый публичный релиз"
```

**Step 3: Проверить тэг**

```bash
git log --oneline -5
git tag -l
```
Ожидание: тэг `v1.0.0` в списке.
