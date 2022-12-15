plugins {
    `java-library`
}

fun DependencyHandlerScope.externalLib(libraryName: String) {
    implementation(files("${rootProject.rootDir}/libs/$libraryName.jar"))
}

dependencies {
    implementation("commons-io:commons-io")

    implementation("org.apache.commons:commons-configuration2")
    implementation("commons-beanutils:commons-beanutils")

    api("org.ow2.asm:asm-tree")
    implementation("org.ow2.asm:asm")
    implementation("org.ow2.asm:asm-analysis")
    implementation("org.ow2.asm:asm-util")
    implementation("org.ow2.asm:asm-commons")
    implementation("com.github.Col-E:CAFED00D")

    implementation("com.github.leibnitz27:cfr") { isChanging = true }
    implementation("org.quiltmc:quiltflower")
    implementation("ch.qos.logback:logback-classic")

    implementation("net.fabricmc:mapping-io")

    //externalLib("fernflower-13-12-22")
}
