# FSRForgeOpti 1.0 (etapa 1: FSR 1.0)

## Compilar
1. Copie `gradlew`, `gradlew.bat` e `gradle/wrapper/gradle-wrapper.jar` do MDK do Forge 1.8.9
   (o `gradle-wrapper.properties` ja esta aqui, Gradle 2.14.1). Use JDK 8.
2. `./gradlew setupDecompWorkspace`   (uma vez)
3. `./gradlew build`                  -> build/libs/FSRForgeOpti-1.0.jar

Coloque o .jar e o OptiFine_1.8.9_HD_U_M5.jar em .minecraft/mods.

## Uso
RShift = menu | /fsr <on/off> [escala 40-100] [nitidez 0.0-1.0]
