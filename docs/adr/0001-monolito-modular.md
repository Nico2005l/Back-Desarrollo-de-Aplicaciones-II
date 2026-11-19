# 0001. Monolito modular en capas

- **Estado:** Aceptada
- **Fecha:** 2026-08-28

## Contexto

El sistema gestiona espacios, reservas, cobros y usuarios. Lo desarrolla un equipo de tres personas y se despliega con Docker Compose. Se necesitan fronteras claras entre módulos para poder probarlos por separado. No hay ningún requisito de escalar o desplegar partes del sistema por separado.

## Opciones consideradas

1. **Monolito modular en capas** (controladores, servicios y repositorios), con interfaces entre módulos y puertos para los sistemas externos.
2. **Microservicios** (identidad, reservas, cobros), cada uno con su base de datos.
3. **Monolito sin capas**, con controladores que usan los repositorios directamente.

## Decisión

Opción 1. Las fronteras se expresan con paquetes e interfaces:

- los controladores dependen de interfaces de servicio;
- solo los servicios usan repositorios;
- las entidades no salen de la capa de datos.

Los servicios no conocen RabbitMQ: publican eventos de dominio, y el paquete `mensajeria` los traduce a mensajes.

## Consecuencias

- Hay un solo artefacto que construir, probar y desplegar. Las transacciones son locales: una reserva y su evento se guardan juntos (ver [0004](0004-outbox-y-confirmaciones-del-publicador.md)).
- Se descartaron los microservicios: con este tamaño no se justifica el costo de red, de consistencia distribuida y de operación.
- Se descartó el monolito sin capas: acopla el HTTP a la persistencia e impide probar las reglas sin levantar la base.
- Si más adelante un módulo necesita escalar por su cuenta, como los cobros, el corte ya está marcado por la interfaz y la cola.
