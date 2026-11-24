# Evidencia del broker — SEC-07

Prueba de que el broker de mensajería **solo acepta AMQP sobre TLS, con una cuenta propia de la
aplicación**, y de que rechaza las dos formas de conectarse que el criterio SEC-07 prohíbe:

1. la cuenta por defecto `guest/guest`;
2. AMQP sin TLS por el puerto 5672.

| Dato | Valor |
|---|---|
| Fecha de la evidencia | 2026-11-24 |
| Broker | RabbitMQ 3.13.7 (`rabbitmq:3.13-management`) |
| Docker Engine | 28.5.1 |
| Entorno | `docker compose up` del repositorio, red del proyecto |
| Cliente usado | `com.rabbitmq:amqp-client:5.38.0` (el mismo que usa la aplicación) |
| Criterio | SEC-07 — broker solo con TLS y cuenta propia |

---

## 1. Qué implementa el criterio

Tres piezas, ya en el repositorio, lo sostienen:

| Pieza | Archivo | Qué hace |
|---|---|---|
| Solo TLS | [`docker/rabbitmq/rabbitmq.conf`](../docker/rabbitmq/rabbitmq.conf) | `listeners.tcp = none` apaga el listener sin cifrado; `listeners.ssl.default = 5671` deja solo AMQPS con TLS 1.2/1.3 |
| Sin `guest` | [`docker-compose.yml`](../docker-compose.yml) + [`docker/rabbitmq/provisionar.sh`](../docker/rabbitmq/provisionar.sh) | El broker arranca con `RABBITMQ_DEFAULT_USER=planta-libre-admin`, así que `guest` nunca se crea; ante la duda, el aprovisionamiento hace `DELETE /api/users/guest` y `loopback_users.guest = true` lo limita al interior del contenedor |
| Cuenta propia y mínima | [`docker/rabbitmq/provisionar.sh`](../docker/rabbitmq/provisionar.sh) | Crea el vhost `planta-libre` y el usuario `planta-libre-api` con permisos solo sobre `^planta-libre\..*` |
| La aplicación no arranca si falta | [`MensajeriaConfig.java`](../src/main/java/com/daii/backdaii/mensajeria/MensajeriaConfig.java) | El bean `FirmaMensajes` falla al inicio si `RABBITMQ_URL` no empieza con `amqps://` o si usa `guest` o una conexión anónima |

```conf
# docker/rabbitmq/rabbitmq.conf
listeners.tcp = none
listeners.ssl.default = 5671
ssl_options.versions.1 = tlsv1.3
ssl_options.versions.2 = tlsv1.2
loopback_users.guest = true
```

---

## 2. Estado del broker al momento de la prueba

Los cuatro oyentes del broker. **No hay ninguno en 5672**, el puerto de AMQP sin cifrar:

```console
$ docker compose exec rabbitmq rabbitmq-diagnostics -q listeners
Interface: [::], port: 15672, protocol: http,            purpose: HTTP API
Interface: [::], port: 15692, protocol: http/prometheus,  purpose: Prometheus exporter API over HTTP
Interface: [::], port: 25672, protocol: clustering,       purpose: inter-node and CLI tool communication
Interface: [::], port: 5671,  protocol: amqp/ssl,         purpose: AMQP 0-9-1 and AMQP 1.0 over TLS
```

Las cuentas del broker. **`guest` no existe**:

```console
$ docker compose exec rabbitmq rabbitmqctl list_users
Listing users ...
user              tags
planta-libre-admin  [administrator]
planta-libre-api    []
```

El usuario de la aplicación no tiene etiquetas de administración: es una cuenta de trabajo, no de
gestión, y sus permisos están acotados al vhost `planta-libre` y a los objetos `planta-libre.*`.

---

## 3. La sonda

Un programa mínimo que intenta **una** conexión AMQP con el cliente real y reporta el resultado o
la excepción. Se ejecuta dentro de la red del compose, para que 5671 y 5672 sean alcanzables a nivel
de red y el rechazo no se confunda con un problema del host.

```java
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;

/** Intenta una conexión AMQP e informa el resultado, o el tipo y el mensaje del fallo. */
public final class SondaSec07 {

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String user = args[2];
        String pass = args[3];
        String vhost = args[4];
        boolean tls = Boolean.parseBoolean(args[5]);

        ConnectionFactory f = new ConnectionFactory();
        f.setHost(host);
        f.setPort(port);
        f.setUsername(user);
        f.setPassword(pass);
        f.setVirtualHost(vhost);
        f.setConnectionTimeout(5000);
        f.setHandshakeTimeout(5000);
        if (tls) {
            f.useSslProtocol();
        }
        try (Connection c = f.newConnection()) {
            System.out.println("RESULTADO: CONECTADO  servidor=" + c.getServerProperties().get("version")
                    + "  usuario=" + user + "  puerto=" + port + "  tls=" + tls);
        } catch (Exception e) {
            System.out.println("RESULTADO: RECHAZADO");
            System.out.println("EXCEPCION: " + e.getClass().getName());
            System.out.println("MENSAJE: " + e.getMessage());
            Throwable causa = e.getCause();
            while (causa != null) {
                System.out.println("CAUSA: " + causa.getClass().getName() + ": " + causa.getMessage());
                causa = causa.getCause();
            }
        }
    }
}
```

---

## 4. Resultados

Cuatro conexiones con la misma sonda: un control positivo y las dos formas prohibidas (con una
variante de la segunda).

### 4.1 Control positivo — cuenta propia por TLS en 5671

```console
### 1) CONTROL POSITIVO: cuenta propia (planta-libre-api) por TLS en 5671
RESULTADO: CONECTADO  servidor=3.13.7  usuario=planta-libre-api  puerto=5671  tls=true
```

Conecta. Esto es lo que hace la aplicación en producción y en la suite de integración. Sirve para
descartar que los rechazos de abajo se deban a una sonda mal armada o a un certificado no confiado:
**la misma sonda, contra el mismo broker, con la misma CA, conecta cuando debe**.

### 4.2 ERROR 1 — `guest/guest` por TLS en 5671

```console
### 2) ERROR 1: cuenta guest/guest por TLS en 5671
RESULTADO: RECHAZADO
EXCEPCION: com.rabbitmq.client.AuthenticationFailureException
MENSAJE: ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN. For details see the broker logfile.
```

El lado del broker lo confirma, con el nombre de la cuenta:

```console
$ docker compose logs rabbitmq | grep guest
rabbitmq-1  | [error] <0.1522.0> PLAIN login refused: user 'guest' - invalid credentials
rabbitmq-1  | [info]  <0.1522.0> closing AMQP connection <0.1522.0> (... -> 172.21.0.2:5671)
```

**Qué prueba:** el broker no tiene la cuenta `guest`, así que la credencial por defecto no abre
nada. TLS no alcanza para entrar: hace falta una cuenta propia.

### 4.3 ERROR 2 — AMQP sin TLS por el puerto 5672

```console
### 3) ERROR 2: AMQP sin TLS por el puerto 5672
RESULTADO: RECHAZADO
EXCEPCION: java.net.ConnectException
MENSAJE: Connection refused (connect failed)
```

Y con `guest/guest`, para mostrar que el rechazo ocurre antes de evaluar credenciales:

```console
### 4) ERROR 2b: guest/guest por el puerto 5672 sin TLS
RESULTADO: RECHAZADO
EXCEPCION: java.net.ConnectException
MENSAJE: Connection refused (connect failed)
```

**Qué prueba:** no hay ningún proceso escuchando en 5672 (`listeners.tcp = none`), así que un
cliente que intente AMQP sin cifrar ni siquiera llega a negociar: la conexión TCP se rechaza. El
mensaje es idéntico con o sin credenciales válidas, que es justamente el punto: por ese puerto no
se entra de ninguna manera.

---

## 5. Resumen

| # | Intento | Resultado | Error exacto |
|---|---|---|---|
| 1 | `planta-libre-api` · 5671 · TLS | **Conecta** | — |
| 2 | `guest/guest` · 5671 · TLS | **Rechazado** | `AuthenticationFailureException: ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN` |
| 3 | `planta-libre-api` · 5672 · sin TLS | **Rechazado** | `java.net.ConnectException: Connection refused (connect failed)` |
| 4 | `guest/guest` · 5672 · sin TLS | **Rechazado** | `java.net.ConnectException: Connection refused (connect failed)` |

Los dos errores que pide SEC-07 quedan guardados en las secciones 4.2 y 4.3.

---

## 6. Cómo reproducirlo

```powershell
# 1. Levantar el broker con su configuración endurecida
docker compose up -d init rabbitmq rabbitmq-usuarios

# 2. Confirmar el estado: un solo oyente AMQP (5671, con TLS) y ninguna cuenta guest
docker compose exec -T rabbitmq rabbitmq-diagnostics -q listeners
docker compose exec -T rabbitmq rabbitmqctl list_users

# 3. Compilar la sonda (el cliente AMQP que ya usa la aplicación)
$m2    = Join-Path $env:USERPROFILE ".m2\repository"
$amqp  = Join-Path $m2 "com\rabbitmq\amqp-client\5.38.0\amqp-client-5.38.0.jar"
$slf4j = Join-Path $m2 "org\slf4j\slf4j-api\2.0.9\slf4j-api-2.0.9.jar"
javac --release 21 -cp "$amqp;$slf4j" -d target\sonda-sec07 target\sonda-sec07\SondaSec07.java

# 4. Confiar en la CA local del broker para que el rechazo de guest sea de autenticación, no de TLS
keytool -importcert -noprompt -alias ca-planta-libre -file docker\rabbitmq\certs\ca.pem `
        -keystore target\sonda-sec07\ca.p12 -storetype PKCS12 -storepass changeit

# 5. Ejecutar las cuatro conexiones dentro de la red del compose
docker run --rm --network back-desarrollo-de-aplicaciones-ii_default --entrypoint sh `
  -v "${PWD}\target\sonda-sec07:/sonda:ro" -v "${amqp}:/jars/amqp.jar:ro" -v "${slf4j}:/jars/slf4j.jar:ro" `
  -v back-desarrollo-de-aplicaciones-ii_secretos:/secretos:ro `
  eclipse-temurin:21-jre /sonda/probar.sh

# 6. Ver el rechazo desde el lado del broker
docker compose logs rabbitmq | Select-String "login refused"
```

La CA y el certificado del servidor los genera `docker/init/inicializar.sh` en
`docker/rabbitmq/certs/` (ignorado por git). La contraseña de `planta-libre-api` vive en el volumen
`secretos`, también ignorado, y la sonda la lee del archivo en vez de recibirla por parámetro.

---

## 7. Cobertura automatizada

| Qué | Dónde | Estado |
|---|---|---|
| La aplicación conecta por AMQPS con su cuenta y su vhost | `MensajeriaIntegracionTest` (toda la clase, 11 tests con RabbitMQ real y TLS) | Automatizado |
| La configuración del broker apaga 5672 y no crea `guest` | `docker/rabbitmq/rabbitmq.conf`, `docker-compose.yml`, `provisionar.sh` | Configuración versionada |
| El broker rechaza `guest/guest` y el 5672 sin TLS | Este documento, secciones 4.2 y 4.3 | Evidencia manual reproducible |

Los dos rechazos no forman parte de la suite automática porque requieren un broker con la
configuración endurecida del compose, no el que levantan los tests (que usa Testcontainers con su
propio certificado por corrida). La evidencia manual cubre ese hueco y queda registrada acá.
