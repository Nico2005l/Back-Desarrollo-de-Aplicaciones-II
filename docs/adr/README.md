# Registro de decisiones de arquitectura (ADR)

Cada archivo registra una decisión principal: el problema, las opciones evaluadas, la elegida y lo que implica. Una decisión aceptada no se edita. Si cambia, se escribe un ADR nuevo que la reemplaza, y la anterior pasa a estado *Reemplazada*.

| N.º | Decisión | Estado |
|---|---|---|
| [0001](0001-monolito-modular.md) | Monolito modular en capas | Aceptada |
| [0002](0002-sesion-jwt-corto-y-refresh-rotativo.md) | Sesión con JWT corto y refresh rotativo | Aceptada |
| [0003](0003-cobro-asincronico-por-cola.md) | Cobro asincrónico por cola punto a punto | Aceptada |
| [0004](0004-outbox-y-confirmaciones-del-publicador.md) | Outbox transaccional y confirmaciones del publicador | Aceptada |
| [0005](0005-idempotency-key-y-etag.md) | Idempotency-Key y ETag en la API | Aceptada |
| [0006](0006-errores-en-problem-details.md) | Errores en Problem Details | Aceptada |
| [0007](0007-limites-de-intentos-en-la-base.md) | Límites de intentos guardados en PostgreSQL | Aceptada |
