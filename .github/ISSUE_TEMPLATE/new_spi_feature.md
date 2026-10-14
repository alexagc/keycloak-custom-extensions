---
name: New SPI Extension Proposal
about: Propose and specify a new Keycloak SPI extension for this repository
title: "[SPI Proposal]: <Extension Name>"
labels: ["enhancement", "spi-design"]
assignees: ''
---

### 1. SPI Category & Interface
* **Category:** (e.g., `event-listeners`, `protocol-mappers`, `authenticators`, `required-actions`)
* **Keycloak SPI Interface:** (e.g., `org.keycloak.events.EventListenerProvider`)

### 2. Business & Architecture Use Case
Why is this extension needed? What enterprise problem does it solve?

### 3. Proposed Configuration
What configuration properties will be required (e.g. via realm settings, SPI config, or environment variables)?

### 4. Failure Mode & Resilience
How should this SPI behave if an external service or downstream API fails?
