# Keycloak Providers

Place the Phase Two magic-link provider JAR here before starting local Keycloak.

Production currently runs Keycloak 25.0.5, so use:

`io.phasetwo.keycloak:keycloak-magic-link:0.29`

The newer `0.73` provider is built for Keycloak 26.x and fails on Keycloak
25.0.5 with `NoClassDefFoundError` for Keycloak admin permission classes.

The directory is mounted into `/opt/keycloak/providers` by `keycloak/docker-compose.yml`.
