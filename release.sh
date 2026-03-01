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
