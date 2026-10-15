---
name: keycloak-spi-scaffolder
description: Scaffolds new Keycloak 26.x Quarkus SPI extension submodules adhering to strict repository standards (Maven POM, Google AutoService, Java 21 native HttpClient, package hierarchies, Testcontainers E2E test, and standalone README.md).
---

# Keycloak SPI Scaffolder Skill

Use this skill whenever scaffolding a new SPI extension submodule in `extensions/<category>/<extension-name>/` for Keycloak 26.x.

---

## 1. Directory Structure Blueprint

Each extension submodule must strictly follow this filesystem layout:

```text
extensions/<category>/<extension-name>/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/io/github/alexc/keycloak/<category>/<shortname>/
    │   │   ├── <Name>ProviderFactory.java   (annotated with @AutoService)
    │   │   └── <Name>Provider.java          (session/request scoped)
    │   └── resources/                       (configuration, messages, static assets)
    └── test/
        ├── java/io/github/alexc/keycloak/<category>/<shortname>/
        │   ├── <Name>ProviderTest.java      (Unit tests with Mockito + JUnit 5)
        │   └── <Name>E2EIT.java             (Testcontainers Keycloak 26.x E2E integration test)
        └── resources/
```

---

## 2. Standard Submodule `pom.xml` Template

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.github.alexc</groupId>
    <artifactId>keycloak-custom-extensions-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../../../pom.xml</relativePath>
  </parent>

  <artifactId>keycloak-<category>-<extension-name></artifactId>
  <name>Keycloak Extension: <Human Readable Name></name>
  <description><Concise description of the SPI extension></description>

  <dependencies>
    <!-- Keycloak Provided APIs (Never embed in JAR) -->
    <dependency>
      <groupId>org.keycloak</groupId>
      <artifactId>keycloak-core</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.keycloak</groupId>
      <artifactId>keycloak-server-spi</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.keycloak</groupId>
      <artifactId>keycloak-server-spi-private</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.keycloak</groupId>
      <artifactId>keycloak-services</artifactId>
      <scope>provided</scope>
    </dependency>

    <!-- Compile-Time Service Registration -->
    <dependency>
      <groupId>com.google.auto.service</groupId>
      <artifactId>auto-service</artifactId>
      <scope>provided</scope>
      <optional>true</optional>
    </dependency>

    <!-- Logging -->
    <dependency>
      <groupId>org.jboss.logging</groupId>
      <artifactId>jboss-logging</artifactId>
      <scope>provided</scope>
    </dependency>

    <!-- Unit Testing -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- Integration Testing (Testcontainers Keycloak) -->
    <dependency>
      <groupId>com.github.dasniko</groupId>
      <artifactId>testcontainers-keycloak</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

---

## 3. Factory Blueprint (`@AutoService`)

```java
package io.github.alexc.keycloak.<category>.<shortname>;

import com.google.auto.service.AutoService;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderFactory;

/**
 * Singleton factory and lifecycle manager for {@link <Name>Provider}.
 */
@AutoService(<TargetSPI>Factory.class)
public class <Name>ProviderFactory implements <TargetSPI>Factory {

  private static final Logger LOG = Logger.getLogger(<Name>ProviderFactory.class);
  public static final String PROVIDER_ID = "<provider-id>";

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public <TargetSPI> create(KeycloakSession session) {
    return new <Name>Provider(session);
  }

  @Override
  public void init(Config.Scope config) {
    // Read global SPI configuration properties
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    // Post initialization if necessary
  }

  @Override
  public void close() {
    // Clean up singleton resources
  }
}
```

---

## 4. Testcontainers Keycloak E2E Integration Test Blueprint

```java
package io.github.alexc.keycloak.<category>.<shortname>;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class <Name>E2EIT {

  static boolean isDockerAvailable() {
    return DockerClientFactory.instance().isDockerAvailable();
  }

  @Container
  static final KeycloakContainer KEYCLOAK =
      new KeycloakContainer("quay.io/keycloak/keycloak:26.8.0")
          .withProviderClassesFrom("target/classes");

  @Test
  @EnabledIf("isDockerAvailable")
  void testExtensionDeployment() {
    assertTrue(KEYCLOAK.isRunning(), "Keycloak container must boot with custom provider mounted");
  }
}
```

---

## 5. Submodule Checklist

Whenever scaffolding a module, ensure:
1. Register module in root `pom.xml`: `<module>extensions/<category>/<extension-name></module>`.
2. Add standalone `extensions/<category>/<extension-name>/README.md`.
3. Add entry in `CHANGELOG.md` under `[Unreleased]`.
4. Update the catalog table in the root `README.md`.
