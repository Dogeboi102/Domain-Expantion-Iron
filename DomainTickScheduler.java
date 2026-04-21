plugins {
    id 'java'
    id 'net.neoforged.gradle.userdev' version '7.0.145'
}

version = '1.0.0'
group = 'com.jjkdomains'

base {
    archivesName = 'jjk-domains'
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

minecraft {
    accessTransformers {
        file('src/main/resources/META-INF/accesstransformer.cfg')
    }
}

runs {
    configureEach {
        systemProperty 'forge.logging.markers', 'REGISTRIES'
        systemProperty 'forge.logging.console.level', 'debug'
        modSource project.sourceSets.main
    }

    client {
        systemProperty 'forge.enabledGameTestNamespaces', 'jjkdomains'
    }

    server {
        systemProperty 'forge.enabledGameTestNamespaces', 'jjkdomains'
        programArgument '--nogui'
    }
}

repositories {
    maven {
        name = "NeoForged"
        url = "https://maven.neoforged.net/releases"
    }
    maven {
        name = "CurseMaven"
        url = "https://www.cursemaven.com"
    }
    maven {
        name = "GeckoLib"
        url = "https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/"
    }
    maven {
        name = "BlameJared"
        url = "https://maven.blamejared.com"
    }
}

dependencies {
    implementation "net.neoforged:neoforge:21.1.86"

    // Irons Spells 'n Spellbooks — compileOnly so it's not bundled in our jar
    compileOnly "curse.maven:irons-spells-n-spellbooks-716927:5765374"

    // GeckoLib (required by Irons Spells)
    compileOnly "software.bernie.geckolib:geckolib-neoforge-1.21.1:4.5.9"
}

tasks.withType(ProcessResources).configureEach {
    var replaceProperties = [
            minecraft_version      : "1.21.1",
            minecraft_version_range: "[1.21.1,1.22)",
            neo_version            : "21.1.86",
            neo_version_range      : "[21.1,)",
            loader_version_range   : "[4,)",
            mod_id                 : "jjkdomains",
            mod_name               : "JJK Domain Expansions",
            mod_license            : "MIT",
            mod_version            : project.version,
            mod_authors            : "JJKDomains",
            mod_description        : "Adds JJK-inspired Domain Expansion spells to Irons Spells n Spellbooks"
    ]
    inputs.properties replaceProperties

    filesMatching(['META-INF/neoforge.mods.toml']) {
        expand replaceProperties
    }
}
