# Informe técnico de prueba de carga

## Objetivo

Evaluar el comportamiento de la API de Planta Libre con 50 usuarios virtuales (VUs) concurrentes durante cinco minutos, midiendo la latencia y los errores de las operaciones de consulta y creación de reservas.

## Entorno y preparación

La prueba se ejecuta en un entorno Docker aislado, con un proyecto Compose y volúmenes propios. La API queda accesible para k6 dentro de la red de ese proyecto mediante `http://back:8080`; no se debe dirigir la carga a producción ni a una base de datos compartida.

La preparación requiere Docker con Compose v2.24.4 o posterior y Python 3. El override de Compose deshabilita los datos demo, el chequeo externo de contraseñas filtradas y SMTP, y habilita el registro local de enlaces de confirmación. Así, el script puede confirmar las cuentas de prueba sin enviar correos reales. El k6 oficial se ejecuta en su contenedor, por lo que no hace falta instalar k6 en el host.

Desde la raíz del backend, preparar el entorno y los datos:

```bash
python3 scripts/preparar_k6.py
```

En Windows, si `python` apunta a Python 3:

```powershell
python scripts/preparar_k6.py
```

En este entorno de Windows también se utilizó:

```bash
/c/Users/Equipo/AppData/Local/Programs/Python/Python311/python.exe scripts/preparar_k6.py
```

El preparador selecciona un nombre de proyecto Docker nuevo y un puerto local libre, inicia la API y sus dependencias, registra y confirma 50 usuarios sintéticos `@loadtest.invalid`, y asigna a cada uno un token y un espacio distinto que aparece como `LIBRE` para una fecha y horario válidos. Guarda la asignación y los tokens en `k6/data/usuarios.json`; los datos de ejecución se guardan en `k6/data/ejecucion.json`. `k6/data/` está ignorado por Git y contiene credenciales temporales: no se debe publicar ni compartir.

La preparación no elimina servicios ni volúmenes si falla. No cambies `BASE_URL`, no reutilices un proyecto Compose existente y no ejecutes la carga contra otro entorno.

## Escenario y criterios de aceptación

El escenario usa el ejecutor `constant-vus` con 50 VUs durante cinco minutos. Cada VU consulta repetidamente:

| Operación | Ruta y método |
|---|---|
| Consultar el plano | `GET /api/pisos/{id}/plano` |
| Consultar Mis reservas | `GET /api/reservas/mias` |
| Crear reserva | `POST /api/reservas` |

Cada usuario intenta crear una reserva una sola vez, en el espacio y horario que recibió durante la preparación. Los intentos se distribuyen durante los cinco minutos, aproximadamente a intervalos de seis segundos; mientras espera su turno, cada VU sigue consultando el plano y Mis reservas. Cada VU realiza dos consultas por iteración y duerme un segundo, manteniéndose debajo del límite general de 200 solicitudes por minuto por usuario y del límite de 10 reservas por minuto por usuario.

Se aceptan p95 inferiores a 500 ms para el conjunto de solicitudes y para cada operación, una tasa de errores HTTP menor al 1 % y más del 99 % de checks exitosos.

## Ejecución

La preparación imprime el comando completo de k6 con el nombre del proyecto elegido y el archivo de entorno correspondiente. Ejecutá ese comando tal como aparece, sin agregar nuevamente el prefijo `planta-k6-` al nombre del proyecto.

Un estado HTTP no exitoso se contabiliza como error y cada operación tiene un check para su estado esperado. La reserva se intenta una sola vez por VU; el script no reintenta una reserva fallida.

## Resultados: segunda ejecución

La segunda ejecución mantuvo 50 VUs durante cinco minutos y produjo 26.774 checks, todos exitosos. Los resultados observados fueron:

| Indicador | Criterio | Resultado | Estado |
|---|---:|---:|---|
| Checks exitosos | > 99 % | 100 % (26.774 checks) | Cumple |
| Errores HTTP | < 1 % | 0 % | Cumple |
| p95 general | < 500 ms | 202,63 ms | Cumple |
| p95 `GET /api/pisos/{id}/plano` | < 500 ms | 245,58 ms | Cumple |
| p95 `GET /api/reservas/mias` | < 500 ms | 160,22 ms | Cumple |
| p95 `POST /api/reservas` | < 500 ms | 555,74 ms | No cumple |

## Análisis y conclusión

La ejecución cumplió el criterio de checks exitosos, mantuvo los errores HTTP por debajo del 1 % y alcanzó el p95 menor a 500 ms para el conjunto de solicitudes y para las dos consultas. El p95 de creación de reservas fue de 555,74 ms: supera el límite en 55,74 ms.

Por lo tanto, la prueba **no cumplió completamente** los criterios de aceptación. La latencia p95 de `POST /api/reservas` queda pendiente de mejora y de una nueva medición. Estos resultados describen únicamente la segunda ejecución; no se incluyen los de la primera.

## Detener y limpiar el entorno de prueba

Después de guardar los resultados, usar el nombre completo del proyecto indicado en `project` dentro de `k6/data/ejecucion.json`, sin anteponer otra vez `planta-k6-`. Usar también el archivo de entorno que imprimió el preparador:

```bash
docker compose -p <project-completo> --env-file <archivo-env-impreso> \
  -f docker-compose.yml -f docker-compose.k6.yml down -v
```

Este comando elimina los contenedores y volúmenes de ese proyecto, incluidos PostgreSQL y los secretos. No lo ejecutes con otro nombre de proyecto. La preparación no elimina el entorno automáticamente.
