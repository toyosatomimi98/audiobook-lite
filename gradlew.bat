@rem Gradle wrapper launcher for Windows.
@echo off
setlocal
set APP_HOME=%~dp0

if exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" goto execute

echo Gradle wrapper JAR is missing. Open the project in Android Studio once or run: gradle wrapper 1>&2
exit /b 1

:execute
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java
)
"%JAVA_EXE%" -Xmx64m -Xms64m "-Dorg.gradle.appname=gradlew" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
